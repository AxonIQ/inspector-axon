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

import io.axoniq.inspector.api.metrics.DispatcherStatisticIdentifier
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
                        dispatcher = DispatcherStatisticIdentifier(handlerInformation, message.toInformation()),
                    )
                }
            }.orElseGet {
                registry.registerMessageDispatchedDuringHandling(
                    dispatcher = DispatcherStatisticIdentifier(null, message.toInformation()),
                )
            }

            message
        }
    }
}
