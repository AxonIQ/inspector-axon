package io.axoniq.inspector.module.eventprocessor

import io.axoniq.inspector.api.*
import mu.KotlinLogging
import org.axonframework.common.ReflectionUtils
import org.axonframework.config.EventProcessingConfiguration
import org.axonframework.eventhandling.EventTrackerStatus
import org.axonframework.eventhandling.StreamingEventProcessor
import org.axonframework.eventhandling.TrackingEventProcessor
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor
import org.axonframework.messaging.StreamableMessageSource

class ProcessorReportCreator(
    private val processingConfig: EventProcessingConfiguration,
    private val metricsRegistry: ProcessorMetricsRegistry,
) {
    private val logger = KotlinLogging.logger { }

    fun createReport() = ProcessorStatusReport(
        processingConfig.eventProcessors()
            .filter { it.value is StreamingEventProcessor }
            .map { entry ->
                val sep = entry.value as StreamingEventProcessor
                ProcessorStatus(
                    entry.key,
                    listOf(ProcessingGroupStatus(
                        entry.key,
                        processingConfig.deadLetterQueue(entry.key).map { it.amountOfSequences() }.orElse(null)
                    )),
                    sep.tokenStoreIdentifier,
                    sep.toType(),
                    sep.isRunning,
                    sep.isError,
                    sep.maxCapacity(),
                    sep.processingStatus().filterValues { !it.isErrorState }.size,
                    processingConfig.tokenStore(entry.key).fetchSegments(entry.key).size, // TODO This will do a query to the TokenStore for every report! Think of optimizing this.
                    sep.processingStatus().map { (_, segment) -> segment.toStatus() },
                    sep.messageSource()?.createHeadToken()?.position()?.orElse(-1) ?: -1,
                    metricsRegistry.ingestLatencyForProcessor(entry.key).getValue(),
                    metricsRegistry.commitLatencyForProcessor(entry.key).getValue(),
                )
            }
    )

    private fun StreamingEventProcessor.messageSource(): StreamableMessageSource<*>? {
        if(this is TrackingEventProcessor) {
            return ReflectionUtils.getFieldValue(TrackingEventProcessor::class.java.getDeclaredField("messageSource"), this)
        }
        if(this is PooledStreamingEventProcessor) {
            return ReflectionUtils.getFieldValue(PooledStreamingEventProcessor::class.java.getDeclaredField("messageSource"), this)
        }

        return null
    }

    private fun StreamingEventProcessor.toType(): ProcessorMode {
        return when (this) {
            is TrackingEventProcessor -> ProcessorMode.TRACKING
            is PooledStreamingEventProcessor -> ProcessorMode.POOLED
            else -> ProcessorMode.UNKNOWN
        }
    }

    private fun EventTrackerStatus.toStatus() = SegmentStatus(
        segment = this.segment.segmentId,
        mergeableSegment = this.segment.mergeableSegmentId(),
        oneOf = this.segment.mask + 1,
        caughtUp = this.isCaughtUp,
        error = this.isErrorState,
        errorType = this.error?.javaClass?.typeName,
        errorMessage = this.error?.message,
        position = this.currentPosition.orElse(-1L),
    )
}

