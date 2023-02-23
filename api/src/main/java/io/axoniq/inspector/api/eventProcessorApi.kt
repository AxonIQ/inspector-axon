package io.axoniq.inspector.api

import java.time.Instant

data class ProcessorStatusReport(
    val processors: List<ProcessorStatus>
)

enum class ProcessorMode {
    POOLED,
    TRACKING,
    UNKNOWN,
}

data class ProcessorStatus(
    val name: String,
    val processingGroups: List<ProcessingGroupStatus>,
    val tokenStoreIdentifier: String,
    val mode: ProcessorMode,
    val started: Boolean,
    val error: Boolean,
    val segmentCapacity: Int,
    val activeSegments: Int,
    val segmentCount: Int,
    val segments: List<SegmentStatus>,
    val headPosition: Long,
    val ingestLatency: Double,
    val commitLatency: Double,
)

data class ProcessingGroupStatus(
    val name: String,
    val dlqSize: Long?,
)

data class SegmentStatus(
    val segment: Int,
    val mergeableSegment: Int,
    val oneOf: Int,
    val caughtUp: Boolean,
    val error: Boolean,
    val errorType: String?,
    val errorMessage: String?,
    val position: Long,
)

data class ProcessorSegmentId(
    val processorName: String,
    val segmentId: Int
)

enum class ResetDecisions {
    HEAD, TAIL, FROM
}

data class ResetDecision(
    val processorName: String,
    val decision: ResetDecisions,
    val from: Instant? = null
)
