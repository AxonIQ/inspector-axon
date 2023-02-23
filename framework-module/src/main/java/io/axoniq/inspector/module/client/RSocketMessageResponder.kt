package io.axoniq.inspector.module.client

import io.axoniq.inspector.api.*
import io.axoniq.inspector.module.eventprocessor.DeadLetterManager
import io.axoniq.inspector.module.eventprocessor.EventProcessorManager
import io.axoniq.inspector.module.eventprocessor.ProcessorReportCreator
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload

open class RSocketMessageResponder(
    private val eventProcessorManager: EventProcessorManager,
    private val processorReportCreator: ProcessorReportCreator,
    private val deadLetterManager: DeadLetterManager,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @MessageMapping(Routes.EventProcessor.START)
    fun handleStart(@Payload processorName: String) {
        logger.info("Handling Inspector Axon START command for processor [{}]", processorName)
        eventProcessorManager.start(processorName)
    }

    @MessageMapping(Routes.EventProcessor.STOP)
    fun handleStop(@Payload processorName: String) {
        logger.info("Handling Inspector Axon STOP command for processor [{}]", processorName)
        eventProcessorManager.stop(processorName)
    }

    @MessageMapping(Routes.EventProcessor.STATUS)
    fun handleStatusQuery(): ProcessorStatusReport {
        logger.info("Handling Inspector Axon STATUS command")
        return processorReportCreator.createReport()
    }

    @MessageMapping(Routes.EventProcessor.RELEASE)
    fun handleRelease(
        @Payload processorSegmentId: ProcessorSegmentId,
    ) {
        logger.info(
            "Handling Inspector Axon RELEASE command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        eventProcessorManager.releaseSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    @MessageMapping(Routes.EventProcessor.SPLIT)
    fun handleSplit(
        @Payload processorSegmentId: ProcessorSegmentId,
    ): Boolean {
        logger.info(
            "Handling Inspector Axon SPLIT command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        return eventProcessorManager
            .splitSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    @MessageMapping(Routes.EventProcessor.MERGE)
    fun handleMerge(
        @Payload processorSegmentId: ProcessorSegmentId,
    ): Boolean {
        logger.info(
            "Handling Inspector Axon MERGE command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        return eventProcessorManager
            .mergeSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    @MessageMapping(Routes.EventProcessor.RESET)
    fun handleReset(@Payload resetDecision: ResetDecision) {
        logger.info("Handling Inspector Axon RESET command for processor [{}]", resetDecision.processorName)
        eventProcessorManager.resetTokens(resetDecision)
    }

    @MessageMapping(Routes.ProcessingGroup.DeadLetter.LETTERS)
    fun handleDeadLetterQuery(request: DeadLetterRequest): DeadLetterResponse {
        logger.info("Handling Inspector Axon DEAD_LETTERS query for request [{}]", request)
        return DeadLetterResponse(deadLetterManager.deadLetters(request.processingGroup, request.offset, request.size))
    }

    @MessageMapping(Routes.ProcessingGroup.DeadLetter.SEQUENCE_SIZE)
    fun handleSequenceSizeQuery(request: DeadLetterSequenceSize): Long {
        logger.info(
            "Handling Inspector Axon DEAD_LETTER_SEQUENCE_SIZE query for processing group [{}]",
            request.processingGroup
        )
        return deadLetterManager.sequenceSize(request.processingGroup, request.sequenceIdentifier)
    }

    @MessageMapping(Routes.ProcessingGroup.DeadLetter.DELETE_SEQUENCE)
    fun handleDeleteSequenceCommand(request: DeadLetterSequenceDeleteRequest) {
        logger.info(
            "Handling Inspector Axon DELETE_FULL_DEAD_LETTER_SEQUENCE command for processing group [{}]",
            request.processingGroup
        )
        deadLetterManager.delete(request.processingGroup, request.sequenceIdentifier)
    }

    @MessageMapping(Routes.ProcessingGroup.DeadLetter.DELETE_LETTER)
    fun handleDeleteLetterCommand(request: DeadLetterSingleDeleteRequest) {
        logger.info(
            "Handling Inspector Axon DELETE_DEAD_LETTER_IN_SEQUENCE command for processing group [{}]",
            request.processingGroup
        )
        deadLetterManager.delete(request.processingGroup, request.sequenceIdentifier, request.messageIdentifier)
    }

    @MessageMapping(Routes.ProcessingGroup.DeadLetter.PROCESS)
    fun handleProcessCommand(request: DeadLetterProcessRequest): Boolean {
        logger.info("Handling Inspector Axon DEAD LETTERS query for processing group [{}]", request.processingGroup)
        return deadLetterManager.process(request.processingGroup, request.messageIdentifier)
    }
}
