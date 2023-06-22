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

package io.axoniq.inspector.messaging

import io.axoniq.inspector.api.InspectorMessageOrigin
import io.axoniq.inspector.api.metrics.DispatcherStatisticIdentifier
import io.axoniq.inspector.api.metrics.HandlerStatisticsMetricIdentifier
import io.axoniq.inspector.api.metrics.HandlerType
import io.axoniq.inspector.api.metrics.MessageIdentifier
import org.axonframework.messaging.Message
import org.axonframework.messaging.MessageDispatchInterceptor
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork
import java.util.function.BiFunction

class InspectorDispatchInterceptor(
        private val registry: HandlerMetricsRegistry,
        private val componentName: String,
) : MessageDispatchInterceptor<Message<*>> {

    override fun handle(messages: MutableList<out Message<*>>): BiFunction<Int, Message<*>, Message<*>> {
        return BiFunction { _, message ->
            if (!CurrentUnitOfWork.isStarted()) {
                // Determine the origin of the handler
                if (message.payload.javaClass.isAnnotationPresent(InspectorMessageOrigin::class.java)) {
                    reportMessageDispatchedFromOrigin(message.payload.javaClass.getAnnotation(InspectorMessageOrigin::class.java).name, message)
                } else {
                    reportMessageDispatchedFromOrigin(componentName, message)
                }
            } else {
                InspectorSpanFactory.onTopLevelSpanIfActive {
                    it.registerMessageDispatched(message.toInformation())
                }
            }
            message
        }
    }

    private fun reportMessageDispatchedFromOrigin(originName: String, message: Message<*>) {
        registry.registerMessageDispatchedDuringHandling(
                DispatcherStatisticIdentifier(HandlerStatisticsMetricIdentifier(
                        type = HandlerType.Origin,
                        component = originName,
                        message = MessageIdentifier("Dispatcher", originName)), message.toInformation())
        )
    }
}
