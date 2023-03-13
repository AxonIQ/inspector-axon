/*
 * Copyright (c) 2022-2023. Inspector Axon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.axoniq.inspector.module.messaging

import io.axoniq.inspector.api.Routes
import io.axoniq.inspector.api.metrics.*
import io.axoniq.inspector.module.client.RSocketInspectorClient
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.axonframework.lifecycle.Lifecycle
import org.axonframework.lifecycle.Phase
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class HandlerMetricsRegistry(
    private val rSocketInspectorClient: RSocketInspectorClient,
    private val executor: ScheduledExecutorService,
) : Lifecycle {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val meterRegistry = SimpleMeterRegistry()

    private val dispatches: MutableMap<DispatcherStatisticIdentifier, RollingCountMeasure> = ConcurrentHashMap()
    private val handlers: MutableMap<HandlerStatisticsMetricIdentifier, HandlerRegistryStatistics> = ConcurrentHashMap()
    private val aggregates: MutableMap<AggregateStatisticIdentifier, AggregateRegistryStatistics> = ConcurrentHashMap()

    override fun registerLifecycleHandlers(lifecycle: Lifecycle.LifecycleRegistry) {
        lifecycle.onStart(Phase.INSTRUCTION_COMPONENTS, this::start)
    }

    fun start() {
        executor.scheduleAtFixedRate({
            try {
                rSocketInspectorClient.send(Routes.MessageFlow.STATS, getStats()).block()
            } catch (e: Exception) {
                logger.warn("No metrics could be reported to Inspector Axon: {}", e.message)
            }
        }, 10, 10, TimeUnit.SECONDS)
    }

    private fun getStats(): StatisticReport {
        val flow = StatisticReport(
            handlers = handlers.entries
                .map {
                    HandlerStatisticsWithIdentifier(
                        it.key, HandlerStatistics(
                            it.value.totalCount.value(),
                            it.value.failureCount.value(),
                            it.value.totalTimer.takeSnapshot().toDistribution(),
                            it.value.metrics.map { (k, v) -> k.fullIdentifier to v.takeSnapshot().toDistribution() }
                                .toMap()
                        )
                    )
                },
            dispatchers = dispatches.entries
                .map {
                    DispatcherStatisticsWithIdentifier(
                        it.key,
                        DispatcherStatistics(it.value.value())
                    )
                },
            aggregates = aggregates.entries
                .map {
                    AggregateStatisticsWithIdentifier(
                        it.key, AggregateStatistics(
                            it.value.totalCount.value(),
                            it.value.failureCount.value(),
                            it.value.totalTimer.takeSnapshot().toDistribution(),
                            it.value.metrics.map { (k, v) -> k.fullIdentifier to v.takeSnapshot().toDistribution() }
                                .toMap()
                        )
                    )
                })

        handlers.values.forEach {
            it.totalCount.incrementWindow()
            it.failureCount.incrementWindow()
        }
        dispatches.values.forEach { it.incrementWindow() }
        aggregates.values.forEach {
            it.totalCount.incrementWindow()
            it.failureCount.incrementWindow()
        }
        return flow
    }

    private fun createTimer(handler: Any, name: String): Timer {
        return Timer
            .builder("${handler}_timer_$name")
            .publishPercentiles(1.00, 0.95, 0.90, 0.50, 0.01)
            .distributionStatisticExpiry(Duration.ofMinutes(1))
            .distributionStatisticBufferLength(1)
            .register(meterRegistry)
    }

    fun registerMessageHandled(
        handler: HandlerStatisticsMetricIdentifier,
        success: Boolean,
        duration: Long,
        metrics: Map<Metric, Long>
    ) {
        val handlerStats = handlers.computeIfAbsent(handler) { _ ->
            HandlerRegistryStatistics(createTimer(handler, "total"))
        }
        handlerStats.totalTimer.record(duration, TimeUnit.NANOSECONDS)
        metrics.filter { it.key.targetTypes.contains(MetricTargetType.HANDLER) }
            .forEach { (metric, value) ->
                handlerStats.metrics
                    .computeIfAbsent(metric) { createTimer(handler, metric.fullIdentifier) }
                    .record(value, metric.type.distributionUnit)
            }

        handlerStats.totalCount.increment()
        if (!success) {
            handlerStats.failureCount.increment()
        }

        if (handler.type == HandlerType.Aggregate) {
            val id = AggregateStatisticIdentifier(handler.component!!)
            val aggStats = aggregates.computeIfAbsent(id) { _ ->
                AggregateRegistryStatistics(createTimer(id, "total"))
            }

            metrics.filter { it.key.targetTypes.contains(MetricTargetType.AGGREGATE) }.forEach { (metric, value) ->
                aggStats.metrics
                    .computeIfAbsent(metric) { createTimer(id, metric.fullIdentifier) }
                    .record(value, metric.type.distributionUnit)
            }
            aggStats.totalTimer.record(duration, TimeUnit.NANOSECONDS)
            aggStats.totalCount.increment()
            if (!success) {
                aggStats.failureCount.increment()
            }

        }
    }

    fun registerMessageDispatchedDuringHandling(
        dispatcher: DispatcherStatisticIdentifier,
    ) {
        dispatches.computeIfAbsent(dispatcher) { _ ->
            RollingCountMeasure()
        }.increment()
    }

    /**
     * Holder object of a handler and all its related stats.
     * Includes total time, and the broken down metrics
     */
    private data class HandlerRegistryStatistics(
        val totalTimer: Timer,
        val totalCount: RollingCountMeasure = RollingCountMeasure(),
        val failureCount: RollingCountMeasure = RollingCountMeasure(),
        val metrics: MutableMap<Metric, Timer> = ConcurrentHashMap()
    )

    private data class AggregateRegistryStatistics(
        val totalTimer: Timer,
        val totalCount: RollingCountMeasure = RollingCountMeasure(),
        val failureCount: RollingCountMeasure = RollingCountMeasure(),
        val metrics: MutableMap<Metric, Timer> = ConcurrentHashMap()
    )
}

