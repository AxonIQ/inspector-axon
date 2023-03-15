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

import org.axonframework.common.Priority
import org.axonframework.config.ProcessingGroup
import org.axonframework.messaging.Message
import org.axonframework.messaging.annotation.HandlerEnhancerDefinition
import org.axonframework.messaging.annotation.MessageHandlingMember
import org.axonframework.messaging.annotation.WrappedMessageHandlingMember
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork

internal const val INSPECTOR_DECLARING_CLASS = "___inspectorDeclaringClass"
internal const val INSPECTOR_PROCESSING_GROUP = "___inspectorProcessor"

@Priority((Int.MIN_VALUE * 0.95).toInt())
class InspectorHandlerEnhancerDefinition : HandlerEnhancerDefinition {

    override fun <T : Any?> wrapHandler(original: MessageHandlingMember<T>): MessageHandlingMember<T> {
        if (original.attribute<Any>("EventSourcingHandler.payloadType").isPresent) {
            // Skip event sourcing handlers
            return original;
        }

        val declaringClassName = original.declaringClass().simpleName
        val processingGroup = original.declaringClass().getDeclaredAnnotation(ProcessingGroup::class.java)?.value
        return object : WrappedMessageHandlingMember<T>(original) {
            override fun handle(message: Message<*>, target: T?): Any? {
                if (!CurrentUnitOfWork.isStarted()) {
                    return super.handle(message, target)
                }
                val uow = CurrentUnitOfWork.get()
                uow.resources()[INSPECTOR_DECLARING_CLASS] = declaringClassName
                uow.resources().computeIfAbsent(INSPECTOR_PROCESSING_GROUP) { processingGroup }

                val start = System.nanoTime()
                try {
                    val result = super.handle(message, target)
                    InspectorSpanFactory.onTopLevelSpanIfActive {
                        it.registerHandler(uow.extractHandler(), System.nanoTime() - start)
                    }
                    return result
                } catch (e: Exception) {
                    InspectorSpanFactory.onTopLevelSpanIfActive {
                        it.recordException(e)
                        it.registerHandler(uow.extractHandler(), System.nanoTime() - start)
                    }
                    throw e
                }
            }
        }
    }
}
