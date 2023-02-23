package io.axoniq.inspector.module.eventprocessor

import io.axoniq.inspector.api.ResetDecision
import io.axoniq.inspector.api.ResetDecisions
import org.axonframework.config.EventProcessingConfiguration
import org.axonframework.eventhandling.EventProcessor
import org.axonframework.eventhandling.StreamingEventProcessor
import java.util.concurrent.TimeUnit

class EventProcessorManager(private val eventProcessingConfig: EventProcessingConfiguration) {

    fun start(processorName: String) {
        eventProcessingConfig.eventProcessor(processorName, EventProcessor::class.java)
            .orElseThrow { IllegalArgumentException("Event Processor [$processorName] not found!") }
            .start()
    }

    fun stop(processorName: String) {
        eventProcessingConfig
            .eventProcessor(processorName, EventProcessor::class.java)
            .orElseThrow { IllegalArgumentException("Event Processor [$processorName] not found!") }
            .shutDown()
    }

    fun releaseSegment(processorName: String, segmentId: Int) {
        eventProcessingConfig
            .eventProcessor(processorName, StreamingEventProcessor::class.java)
            .orElseThrow { IllegalArgumentException("Streaming Event Processor [$processorName] not found.") }
            .releaseSegment(segmentId)
    }

    fun splitSegment(processorName: String, segmentId: Int) =
        eventProcessingConfig
            .eventProcessor(processorName, StreamingEventProcessor::class.java)
            .orElseThrow { IllegalArgumentException("Streaming Event Processor [$processorName] not found.") }
            .splitSegment(segmentId)
            .get(5, TimeUnit.SECONDS)

    fun mergeSegment(processorName: String, segmentId: Int) =
        eventProcessingConfig
            .eventProcessor(processorName, StreamingEventProcessor::class.java)
            .orElseThrow { IllegalArgumentException("Streaming Event Processor [$processorName] not found.") }
            .mergeSegment(segmentId)
            .get(5, TimeUnit.SECONDS)

    fun resetTokens(resetDecision: ResetDecision) =
        eventProcessingConfig
            .eventProcessor(resetDecision.processorName, StreamingEventProcessor::class.java)
            .orElseThrow { IllegalArgumentException("Streaming Event Processor [${resetDecision.processorName}] not found.") }
            .resetTokens { messageSource ->
                when (resetDecision.decision) {
                    ResetDecisions.HEAD -> messageSource.createHeadToken()
                    ResetDecisions.TAIL -> messageSource.createTailToken()
                    ResetDecisions.FROM -> messageSource.createTokenAt(resetDecision.from!!)
                }
            }

}
