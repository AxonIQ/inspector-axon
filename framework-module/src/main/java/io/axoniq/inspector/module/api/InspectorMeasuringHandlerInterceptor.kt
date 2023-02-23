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

package io.axoniq.inspector.module.api

import io.axoniq.inspector.module.messaging.InspectorSpanFactory
import org.axonframework.messaging.InterceptorChain
import org.axonframework.messaging.Message
import org.axonframework.messaging.MessageHandlerInterceptor
import org.axonframework.messaging.unitofwork.UnitOfWork

/**
 * Interceptor that wraps another interceptor and measure the time spent, reporting it to Inspector Axon.
 * Will show up in the Handler UI as metric option to show.
 */
class InspectorMeasuringHandlerInterceptor(
    val subject: MessageHandlerInterceptor<Message<*>>,
    private val name: String = subject::class.java.simpleName,
) : MessageHandlerInterceptor<Message<*>> {
    override fun handle(unitOfWork: UnitOfWork<out Message<*>>, interceptorChain: InterceptorChain): Any? {
        val start = System.nanoTime()
        var endBefore: Long? = null
        var startAfter: Long? = null
        val result = subject.handle(unitOfWork) {
            endBefore = System.nanoTime()
            val internalResult = interceptorChain.proceed()
            startAfter = System.nanoTime()
            internalResult
        }
        val end = System.nanoTime()

        if (endBefore == null || startAfter == null) {
            return result
        }

        val time = (endBefore!! - start) + (end - startAfter!!)
        InspectorSpanFactory.onTopLevelSpanIfActive(unitOfWork.message) {
            it.additionalMetrics["MHI_$name"] = time
        }
        return result
    }
}
