package io.axoniq.inspector.module.messaging

import io.axoniq.inspector.module.eventprocessor.ProcessorMetricsRegistry
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.Message
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.serialization.UnknownSerializedType
import java.time.Instant
import java.time.temporal.ChronoUnit


class InspectorHandlerProcessorInterceptor(
    private val processorMetricsRegistry: ProcessorMetricsRegistry,
    private val processorName: String,
) : MessageHandlerInterceptor<Message<*>> {

    override fun handle(unitOfWork: UnitOfWork<out Message<*>>, interceptorChain: InterceptorChain): Any? {
        val uow = CurrentUnitOfWork.map { it }.orElse(null)
        if (uow == null || unitOfWork.message.payload is UnknownSerializedType) {
            return interceptorChain.proceed()
        }
        unitOfWork.resources()[INSPECTOR_PROCESSING_GROUP] = processorName
        val message = unitOfWork.message
        if (message is EventMessage) {
            processorMetricsRegistry.registerIngested(
                processorName,
                ChronoUnit.NANOS.between(message.timestamp, Instant.now())
            )
            unitOfWork.afterCommit {
                processorMetricsRegistry.registerCommitted(
                    processorName,
                    ChronoUnit.NANOS.between(message.timestamp, Instant.now())
                )
            }
        }
        return interceptorChain.proceed()
    }
}
