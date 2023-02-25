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

package io.axoniq.inspector.module.client

import io.axoniq.inspector.api.*
import io.axoniq.inspector.module.eventprocessor.DeadLetterManager
import io.axoniq.inspector.module.eventprocessor.EventProcessorManager
import io.axoniq.inspector.module.eventprocessor.ProcessorReportCreator
import org.axonframework.lifecycle.Lifecycle
import org.axonframework.lifecycle.Phase
import org.slf4j.LoggerFactory

open class RSocketMessageResponder(
    private val eventProcessorManager: EventProcessorManager,
    private val processorReportCreator: ProcessorReportCreator,
    private val deadLetterManager: DeadLetterManager,
    private val rsocketClient: RSocketInspectorClient
) : Lifecycle {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun registerLifecycleHandlers(registry: Lifecycle.LifecycleRegistry) {
        registry.onStart(Phase.EXTERNAL_CONNECTIONS, this::start)
    }

    fun start() {
        rsocketClient.registerHandlerWithPayload(Routes.EventProcessor.START, String::class.java, this::handleStart)
        rsocketClient.registerHandlerWithPayload(Routes.EventProcessor.STOP, String::class.java, this::handleStop)
        rsocketClient.registerHandlerWithoutPayload(Routes.EventProcessor.STATUS, this::handleStatusQuery)
        rsocketClient.registerHandlerWithPayload(
            Routes.EventProcessor.RELEASE,
            ProcessorSegmentId::class.java,
            this::handleRelease
        )
        rsocketClient.registerHandlerWithPayload(
            Routes.EventProcessor.SPLIT,
            ProcessorSegmentId::class.java,
            this::handleSplit
        )
        rsocketClient.registerHandlerWithPayload(
            Routes.EventProcessor.MERGE,
            ProcessorSegmentId::class.java,
            this::handleMerge
        )
        rsocketClient.registerHandlerWithPayload(
            Routes.EventProcessor.RESET,
            ResetDecision::class.java,
            this::handleReset
        )
        rsocketClient.registerHandlerWithPayload(
            Routes.ProcessingGroup.DeadLetter.LETTERS,
            DeadLetterRequest::class.java,
            this::handleDeadLetterQuery
        )
        rsocketClient.registerHandlerWithPayload(
            Routes.ProcessingGroup.DeadLetter.SEQUENCE_SIZE,
            DeadLetterSequenceSize::class.java,
            this::handleSequenceSizeQuery
        )
        rsocketClient.registerHandlerWithPayload(
            Routes.ProcessingGroup.DeadLetter.DELETE_SEQUENCE,
            DeadLetterSequenceDeleteRequest::class.java,
            this::handleDeleteSequenceCommand
        )
        rsocketClient.registerHandlerWithPayload(
            Routes.ProcessingGroup.DeadLetter.DELETE_LETTER,
            DeadLetterSingleDeleteRequest::class.java,
            this::handleDeleteLetterCommand
        )
        rsocketClient.registerHandlerWithPayload(
            Routes.ProcessingGroup.DeadLetter.PROCESS,
            DeadLetterProcessRequest::class.java,
            this::handleProcessCommand
        )
    }

    private fun handleStart(processorName: String) {
        logger.info("Handling Inspector Axon START command for processor [{}]", processorName)
        eventProcessorManager.start(processorName)
    }

    private fun handleStop(processorName: String) {
        logger.info("Handling Inspector Axon STOP command for processor [{}]", processorName)
        eventProcessorManager.stop(processorName)
    }

    private fun handleStatusQuery(): ProcessorStatusReport {
        logger.info("Handling Inspector Axon STATUS command")
        return processorReportCreator.createReport()
    }

    fun handleRelease(processorSegmentId: ProcessorSegmentId) {
        logger.info(
            "Handling Inspector Axon RELEASE command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        eventProcessorManager.releaseSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    private fun handleSplit(processorSegmentId: ProcessorSegmentId): Boolean {
        logger.info(
            "Handling Inspector Axon SPLIT command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        return eventProcessorManager
            .splitSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    private fun handleMerge(processorSegmentId: ProcessorSegmentId): Boolean {
        logger.info(
            "Handling Inspector Axon MERGE command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        return eventProcessorManager
            .mergeSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    private fun handleReset(resetDecision: ResetDecision) {
        logger.info("Handling Inspector Axon RESET command for processor [{}]", resetDecision.processorName)
        eventProcessorManager.resetTokens(resetDecision)
    }

    private fun handleDeadLetterQuery(request: DeadLetterRequest): DeadLetterResponse {
        logger.info("Handling Inspector Axon DEAD_LETTERS query for request [{}]", request)
        return DeadLetterResponse(deadLetterManager.deadLetters(request.processingGroup, request.offset, request.size))
    }

    private fun handleSequenceSizeQuery(request: DeadLetterSequenceSize): Long {
        logger.info(
            "Handling Inspector Axon DEAD_LETTER_SEQUENCE_SIZE query for processing group [{}]",
            request.processingGroup
        )
        return deadLetterManager.sequenceSize(request.processingGroup, request.sequenceIdentifier)
    }

    private fun handleDeleteSequenceCommand(request: DeadLetterSequenceDeleteRequest) {
        logger.info(
            "Handling Inspector Axon DELETE_FULL_DEAD_LETTER_SEQUENCE command for processing group [{}]",
            request.processingGroup
        )
        deadLetterManager.delete(request.processingGroup, request.sequenceIdentifier)
    }

    private fun handleDeleteLetterCommand(request: DeadLetterSingleDeleteRequest) {
        logger.info(
            "Handling Inspector Axon DELETE_DEAD_LETTER_IN_SEQUENCE command for processing group [{}]",
            request.processingGroup
        )
        deadLetterManager.delete(request.processingGroup, request.sequenceIdentifier, request.messageIdentifier)
    }

    private fun handleProcessCommand(request: DeadLetterProcessRequest): Boolean {
        logger.info("Handling Inspector Axon DEAD LETTERS query for processing group [{}]", request.processingGroup)
        return deadLetterManager.process(request.processingGroup, request.messageIdentifier)
    }
}
