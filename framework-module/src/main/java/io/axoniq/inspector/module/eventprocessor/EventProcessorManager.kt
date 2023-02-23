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
