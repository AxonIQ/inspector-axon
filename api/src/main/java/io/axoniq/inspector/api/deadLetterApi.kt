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
