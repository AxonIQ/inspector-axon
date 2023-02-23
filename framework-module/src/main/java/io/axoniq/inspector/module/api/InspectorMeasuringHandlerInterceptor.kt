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
