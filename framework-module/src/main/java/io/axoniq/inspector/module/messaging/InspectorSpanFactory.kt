package io.axoniq.inspector.module.messaging

import io.axoniq.inspector.api.HandlerInformation
import org.axonframework.messaging.Message
import org.axonframework.tracing.Span
import org.axonframework.tracing.SpanAttributesProvider
import org.axonframework.tracing.SpanFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

class InspectorSpanFactory(
    private val registry: HandlerMetricsRegistry
) : SpanFactory {
    companion object {
        private val NOOP_SPAN = NoopSpan()
        private val ACTIVE_ROOT_SPANS = ConcurrentHashMap<String, RootProcessingSpan>()
        private val CURRENT_MESSAGE_ID = ThreadLocal<String>()

        fun onTopLevelSpanIfActive(message: Message<*>, block: (RootProcessingSpan) -> Unit) {
            onTopLevelSpanIfActive(message.identifier, block)
        }

        fun onTopLevelSpanIfActive(messageId: String, block: (RootProcessingSpan) -> Unit) {
            ACTIVE_ROOT_SPANS[messageId]?.let(block)
        }
    }

    inner class RootProcessingSpan(private val message: Message<*>) : Span {
        private var timeStarted: Long? = null
        private var spanSuccessful = true

        // Fields that should be set by the handler enhancer
        var timeHandlerStarted: Long? = null
        var timeHandlerEnded: Long? = null
        var handlerInformation: HandlerInformation? = null
        var handlerSuccessful = true

        // Addtional metrics that can be registered by other spans
        val additionalMetrics: MutableMap<String, Long> = mutableMapOf()

        override fun start(): Span {
            timeStarted = System.nanoTime()
            ACTIVE_ROOT_SPANS[message.identifier] = this
            CURRENT_MESSAGE_ID.set(message.identifier)
            return this
        }

        override fun end() {
            val end = System.nanoTime()
            if (handlerInformation != null && timeStarted != null && timeHandlerStarted != null && timeHandlerEnded != null) {
                registry.registerMessageHandled(
                    handler = handlerInformation!!,
                    successful = handlerSuccessful,
                    totalDuration = end - timeStarted!!,
                    handlerDuration = timeHandlerEnded!! - timeHandlerStarted!!,
                    additionalStats = additionalMetrics

                )
            }
            ACTIVE_ROOT_SPANS.remove(message.identifier)
            CURRENT_MESSAGE_ID.remove()
        }

        override fun recordException(t: Throwable): Span {
            spanSuccessful = false
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
        if (name == "QueryProcessingTask" || name == "AxonServerCommandBus.handle") {
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
        if(name == "LockingRepository.obtainLock") {
            return TimeRecordingSpan("aggregate_lock")
        }
        if(name.contains(".load ")) {
            return TimeRecordingSpan("aggregate_load")
        }
        if(name.endsWith(".commit")) {
            return TimeRecordingSpan("events_commit")
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
            RootProcessingSpan(message)
        }
    }

    class TimeRecordingSpan(private val metricName: String) : Span {
        private var started: Long? = null
        override fun start(): Span {
            started = System.nanoTime()
            return this
        }

        override fun end() {
            if (started == null || CURRENT_MESSAGE_ID.get() == null) {
                return
            }
            val ended = System.nanoTime()
            onTopLevelSpanIfActive(CURRENT_MESSAGE_ID.get()) {
                it.additionalMetrics[metricName] = ended - started!!
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
