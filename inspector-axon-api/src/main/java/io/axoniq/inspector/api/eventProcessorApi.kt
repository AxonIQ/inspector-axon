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
    val segments: List<SegmentStatus>,
)

data class ProcessingGroupStatus(
    val name: String,
    val dlqSize: Long?,
)

data class SegmentStatus(
    val segment: Int,
    val mergeableSegment: Int,
    val mask: Int = -1,
    val oneOf: Int,
    val caughtUp: Boolean,
    val error: Boolean,
    val errorType: String?,
    val errorMessage: String?,
    val ingestLatency: Double?,
    val commitLatency: Double?,
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

data class SegmentOverview(
    val segments: List<SegmentDetails>
)

data class SegmentDetails(

    val segment: Int,
    val mergeableSegment: Int,
    val mask: Int,
)