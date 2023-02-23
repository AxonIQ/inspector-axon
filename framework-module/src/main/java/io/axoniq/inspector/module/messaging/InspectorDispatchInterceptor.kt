package io.axoniq.inspector.module.messaging

import io.axoniq.inspector.api.DispatcherInformation
import org.axonframework.messaging.Message
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import java.util.function.BiFunction


class InspectorDispatchInterceptor(
    private val registry: HandlerMetricsRegistry,
) : MessageDispatchInterceptor<Message<*>> {

    override fun handle(messages: MutableList<out Message<*>>): BiFunction<Int, Message<*>, Message<*>> {
        return BiFunction { _, message ->
            CurrentUnitOfWork.map {
                it.afterCommit { uow ->
                    val handlerInformation = uow.extractHandler()
                    registry.registerMessageDispatchedDuringHandling(
                        dispatcher = DispatcherInformation(handlerInformation, message.toInformation()),
                    )
                }
            }.orElseGet {
                registry.registerMessageDispatchedDuringHandling(
                    dispatcher = DispatcherInformation(null, message.toInformation()),
                )
            }

            message
        }
    }
}
