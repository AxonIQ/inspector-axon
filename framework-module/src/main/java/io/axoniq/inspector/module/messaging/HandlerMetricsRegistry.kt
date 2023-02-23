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

import io.axoniq.inspector.api.*
import io.axoniq.inspector.module.client.RSocketInspectorClient
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.distribution.HistogramSnapshot
import io.micrometer.core.instrument.distribution.ValueAtPercentile
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HandlerMetricsRegistry(
    private val rSocketInspectorClient: RSocketInspectorClient,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val meterRegistry = SimpleMeterRegistry()

    private val handlerTotalCounterRegistry: MutableMap<HandlerInformation, RollingCountMeasure> = ConcurrentHashMap()
    private val handlerFailureCounterRegistry: MutableMap<HandlerInformation, RollingCountMeasure> = ConcurrentHashMap()
    private val dispatcherRegistry: MutableMap<DispatcherInformation, RollingCountMeasure> = ConcurrentHashMap()

    private val handlerTimerRegistry: MutableMap<HandlerInformation, HandlerTimers> = ConcurrentHashMap()

    init {
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(this::report, 10, 10, TimeUnit.SECONDS)
    }

    data class HandlerTimers(
        val totalTimer: Timer,
        val handleTimer: Timer,
        val additionalTimers: MutableMap<String, Timer> = ConcurrentHashMap()
    )

    private fun report() {
        try {
            val stats = getStats()
            rSocketInspectorClient
                .send(Routes.MessageFlow.STATS, stats)
                .block()

        } catch (e: Exception) {
            logger.warn("No metrics could be reported to Inspector Axon: {}", e.message)
        }
    }

    private fun HistogramSnapshot.toDistribution(): DistributionStatistics {
        val percentiles = percentileValues()
        return DistributionStatistics(
            min = percentiles.ofPercentile(0.01),
            percentile90 = percentiles.ofPercentile(0.90),
            percentile95 = percentiles.ofPercentile(0.95),
            median = percentiles.ofPercentile(0.50),
            mean = mean(TimeUnit.MILLISECONDS),
            max = percentiles.ofPercentile(1.00),
        )
    }

    private fun getStats(): ClientMessageStatisticsReport {
        val flow = ClientMessageStatisticsReport(
            handlers = handlerTimerRegistry.entries.map {
                HandlerStatistics(
                    it.key, MessageHandlerBucketStatistics(
                        handlerTotalCounterRegistry[it.key]?.value() ?: 0.0,
                        handlerFailureCounterRegistry[it.key]?.value() ?: 0.0,
                        it.value.totalTimer.takeSnapshot().toDistribution(),
                        it.value.handleTimer.takeSnapshot().toDistribution(),
                        it.value.additionalTimers.mapValues { (k, v) -> v.takeSnapshot().toDistribution() }
                    )
                )
            },
            dispatchers = dispatcherRegistry.entries.map { DispatcherStatistics(it.key, it.value.value()) })

        handlerTotalCounterRegistry.values.forEach { it.incrementWindow() }
        handlerFailureCounterRegistry.values.forEach { it.incrementWindow() }
        dispatcherRegistry.values.forEach { it.incrementWindow() }
        return flow
    }

    private fun Array<ValueAtPercentile>.ofPercentile(percentile: Double): Double {
        return this.firstOrNull { pc -> pc.percentile() == percentile }
            ?.value(TimeUnit.MILLISECONDS)!!
    }

    private fun createTimer(handler: HandlerInformation, name: String): Timer {
        return Timer
            .builder("${handler}_timer_$name")
            .publishPercentiles(1.00, 0.95, 0.90, 0.50, 0.01)
            .distributionStatisticExpiry(Duration.ofMinutes(1))
            .distributionStatisticBufferLength(1)
            .register(meterRegistry)
    }

    fun registerMessageHandled(
        handler: HandlerInformation,
        successful: Boolean,
        totalDuration: Long,
        handlerDuration: Long,
        additionalStats: Map<String, Long>
    ) {
        val timers = handlerTimerRegistry.computeIfAbsent(handler) { _ ->
            HandlerTimers(
                createTimer(handler, "total"),
                createTimer(handler, "handler"),
            )
        }
        timers.totalTimer.record(totalDuration, TimeUnit.NANOSECONDS)
        timers.handleTimer.record(handlerDuration, TimeUnit.NANOSECONDS)
        additionalStats.forEach { (name, value) ->
            timers.additionalTimers.computeIfAbsent(name) { createTimer(handler, name) }
                .record(value, TimeUnit.NANOSECONDS)
        }

        handlerTotalCounterRegistry.computeIfAbsent(handler) { _ ->
            RollingCountMeasure()
        }.increment()
        if (!successful) {
            handlerFailureCounterRegistry.computeIfAbsent(handler) { _ ->
                RollingCountMeasure()
            }.increment()
        }
    }

    fun registerMessageDispatchedDuringHandling(
        dispatcher: DispatcherInformation,
    ) {
        dispatcherRegistry.computeIfAbsent(dispatcher) { _ ->
            RollingCountMeasure()
        }.increment()
    }
}

