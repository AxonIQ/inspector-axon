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

import io.axoniq.inspector.api.metrics.HandlerStatisticsMetricIdentifier
import io.axoniq.inspector.api.metrics.Metric
import io.axoniq.inspector.api.metrics.MetricType
import io.axoniq.inspector.api.metrics.PreconfiguredMetric
import org.axonframework.messaging.Message
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.tracing.Span
import org.axonframework.tracing.SpanAttributesProvider
import org.axonframework.tracing.SpanFactory
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier


class InspectorSpanFactory(
    private val registry: HandlerMetricsRegistry
) : SpanFactory {
    private val logger = LoggerFactory.getLogger(this::class.java)
    companion object {
        private val NOOP_SPAN = NoopSpan()
        private val ACTIVE_ROOT_SPANS = ConcurrentHashMap<String, MeasuringInspectorSpan>()
        private val CURRENT_MESSAGE_ID = ThreadLocal<String>()

        fun onTopLevelSpanIfActive(message: Message<*>, block: (MeasuringInspectorSpan) -> Unit) {
            onTopLevelSpanIfActive(message.identifier, block)
        }

        fun onTopLevelSpanIfActive(messageId: String, block: (MeasuringInspectorSpan) -> Unit) {
            ACTIVE_ROOT_SPANS[messageId]?.let(block)
        }

        fun onTopLevelSpanIfActive(block: (MeasuringInspectorSpan) -> Unit) {
            if (CURRENT_MESSAGE_ID.get() == null) {
                return
            }
            ACTIVE_ROOT_SPANS[CURRENT_MESSAGE_ID.get()]?.let(block)
        }
    }

    inner class MeasuringInspectorSpan(private val message: Message<*>) : Span {
        private var timeStarted: Long? = null
        private var transactionSuccessful = true

        // Fields that should be set by the handler enhancer
        private var handlerMetricIdentifier: HandlerStatisticsMetricIdentifier? = null
        private var handlerSuccessful = true

        // Additional metrics that can be registered by other spans for processors
        private val metrics: MutableMap<Metric, Long> = mutableMapOf()

        fun registerHandler(handlerMetricIdentifier: HandlerStatisticsMetricIdentifier, time: Long) {
            this.handlerMetricIdentifier = handlerMetricIdentifier
            this.registerMetricValue(PreconfiguredMetric.MESSAGE_HANDLER_TIME, time)
        }

        fun registerMetricValue(metric: Metric, value: Long) {
            val actualValue = value - metric.breakDownMetrics.sumOf { metrics[it] ?: 0 }
            metrics[metric] = actualValue
        }

        override fun start(): Span {
            timeStarted = System.nanoTime()
            ACTIVE_ROOT_SPANS[message.identifier] = this
            CURRENT_MESSAGE_ID.set(message.identifier)
            CurrentUnitOfWork.map {
                it.onRollback { transactionSuccessful = false }
            }
            return this
        }

        override fun end() {
            val end = System.nanoTime()
            ACTIVE_ROOT_SPANS.remove(message.identifier)
            CURRENT_MESSAGE_ID.remove()

            if (handlerMetricIdentifier == null || timeStarted == null) return
            if (!CurrentUnitOfWork.isStarted()) {
                registry.registerMessageHandled(
                    handler = handlerMetricIdentifier!!,
                    success = handlerSuccessful && transactionSuccessful,
                    duration = end - timeStarted!!,
                    metrics = metrics
                )
                return
            }

            CurrentUnitOfWork.get().onCleanup {
                registry.registerMessageHandled(
                    handler = handlerMetricIdentifier!!,
                    success = handlerSuccessful && transactionSuccessful,
                    duration = end - timeStarted!!,
                    metrics = metrics
                )
            }
        }

        override fun recordException(t: Throwable): Span {
            transactionSuccessful = false
            return this
        }
    }

    override fun createRootTrace(operationNameSupplier: Supplier<String>): Span {
        return NOOP_SPAN
    }

    override fun createHandlerSpan(
        operationNameSupplier: Supplier<String>,
        parentMessage: Message<*>,
        isChildTrace: Boolean,
        vararg linkedParents: Message<*>?
    ): Span {
        val name = operationNameSupplier.get()
        if (name == "QueryProcessingTask" || name == "AxonServerCommandBus.handle" || name == "DeadlineJob.execute") {
            return startIfNotActive(parentMessage)
        }
        return NOOP_SPAN
    }

    override fun createDispatchSpan(
        operationNameSupplier: Supplier<String>,
        parentMessage: Message<*>?,
        vararg linkedSiblings: Message<*>?
    ): Span {
        return NOOP_SPAN
    }

    override fun createInternalSpan(operationNameSupplier: Supplier<String>): Span {
        val name = operationNameSupplier.get()
        if (name == "LockingRepository.obtainLock") {
            return TimeRecordingSpan(PreconfiguredMetric.AGGREGATE_LOCK_TIME)
        }
        if (name.contains(".load ")) {
            return TimeRecordingSpan(PreconfiguredMetric.AGGREGATE_LOAD_TIME)
        }
        if (name.endsWith(".commit")) {
            return TimeRecordingSpan(PreconfiguredMetric.EVENT_COMMIT_TIME)
        }

        return NOOP_SPAN
    }

    override fun createInternalSpan(operationNameSupplier: Supplier<String>, message: Message<*>): Span {
        val name = operationNameSupplier.get()
        if (name.endsWith("Bus.handle")
            || name == "SimpleQueryBus.query"
            || name.startsWith("SimpleQueryBus.scatterGather")
            || name.startsWith("PooledStreamingEventProcessor")
            || name.startsWith("TrackingEventProcessor")
        ) {
            return startIfNotActive(message)
        }
        return NOOP_SPAN
    }

    override fun registerSpanAttributeProvider(provider: SpanAttributesProvider?) {
        // Not necessary
    }

    override fun <M : Message<*>?> propagateContext(message: M): M {
        return message
    }

    private fun startIfNotActive(message: Message<*>): Span {
        if (ACTIVE_ROOT_SPANS.containsKey(message.identifier)) {
            return NOOP_SPAN
        }
        return ACTIVE_ROOT_SPANS.computeIfAbsent(message.identifier) {
            MeasuringInspectorSpan(message)
        }
    }

    class TimeRecordingSpan(private val metric: Metric) : Span {
        init {
            assert(metric.type == MetricType.TIMER)
        }

        private var started: Long? = null
        override fun start(): Span {
            started = System.nanoTime()
            return this
        }

        override fun end() {
            if (started == null) {
                return
            }
            val ended = System.nanoTime()
            onTopLevelSpanIfActive {
                it.registerMetricValue(metric, ended - started!!)
            }

        }

        override fun recordException(t: Throwable?): Span = this
    }

    class NoopSpan : Span {
        override fun start(): Span = this

        override fun end() {
            // Not implemented
        }

        override fun recordException(t: Throwable?): Span = this
    }
}
