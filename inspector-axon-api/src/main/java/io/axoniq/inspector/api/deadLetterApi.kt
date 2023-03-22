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

data class DeadLetter(
    val messageIdentifier: String,
    val message: String,
    val messageType: String,
    val causeType: String?,
    val causeMessage: String?,
    val enqueuedAt: Instant,
    val lastTouched: Instant,
    val diagnostics: Map<String, *>,
    val sequenceIdentifier: String
)

data class DeadLetterResponse(
    val sequences: List<List<DeadLetter>>
)

data class DeadLetterRequest(
    val processingGroup: String,
    val offset: Int,
    val size: Int,
    val maxSequenceLetters: Int,
)

data class DeadLetterSequenceSize(
    val processingGroup: String,
    val sequenceIdentifier: String
)

data class DeadLetterSequenceDeleteRequest(
    val processingGroup: String,
    val sequenceIdentifier: String,
)

data class DeadLetterSingleDeleteRequest(
    val processingGroup: String,
    val sequenceIdentifier: String,
    val messageIdentifier: String,
)

data class DeadLetterProcessRequest(
    val processingGroup: String,
    val messageIdentifier: String
)
