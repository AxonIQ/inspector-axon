package io.axoniq.inspector.module.eventprocessor

import org.axonframework.config.EventProcessingConfiguration
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.deadletter.DeadLetter
import org.axonframework.messaging.deadletter.SequencedDeadLetterQueue
import org.axonframework.serialization.Serializer
import io.axoniq.inspector.api.DeadLetter as ApiDeadLetter

private const val LETTER_PAYLOAD_SIZE_LIMIT = 1024

class DeadLetterManager(
    private val eventProcessingConfig: EventProcessingConfiguration,
    private val eventSerializer: Serializer
) {

    /**
     *
     */
    fun deadLetters(
        processingGroup: String,
        offset: Int = 0,
        size: Int = 25,
        maxSequenceLetters: Int = 10
    ): List<List<ApiDeadLetter>> = dlqFor(processingGroup)
        .deadLetters()
        .drop(offset)
        .take(size)
        .map { sequence ->
            sequence
                .asIterable()
                .take(maxSequenceLetters)
                .map { toDeadLetter(it, processingGroup) }
        }

    private fun toDeadLetter(letter: DeadLetter<out EventMessage<*>>, processingGroup: String) =
        letter.toApiLetter(sequenceIdentifierFor(processingGroup, letter))

    private fun DeadLetter<out EventMessage<*>>.toApiLetter(sequenceIdentifier: String) = ApiDeadLetter(
        this.message().identifier,
        serializePayload(),
        this.message().payloadType.simpleName,
        this.cause().map { it.type() }.orElse(null),
        this.cause().map { it.message() }.orElse(null),
        this.enqueuedAt(),
        this.lastTouched(),
        this.diagnostics(),
        sequenceIdentifier
    )

    private fun DeadLetter<out EventMessage<*>>.serializePayload() =
        try {
            eventSerializer
                .serialize(this.message().payload, String::class.java)
                .data
        } catch (_: Exception) {
            this.message().payload.toString()
        }.take(LETTER_PAYLOAD_SIZE_LIMIT)

    private fun sequenceIdentifierFor(
        processingGroup: String,
        letter: DeadLetter<out EventMessage<*>>
    ): String = eventProcessingConfig
        .sequencingPolicy(processingGroup)
        .getSequenceIdentifierFor(letter.message())
        ?.let {
            if (it is String) it else it.hashCode().toString()
        }
        ?: letter.message().identifier

    /**
     *
     */
    fun sequenceSize(
        processingGroup: String,
        sequenceIdentifier: String
    ) = dlqFor(processingGroup).sequenceSize(sequenceIdentifier)

    /**
     *
     */
    fun delete(
        processingGroup: String,
        sequenceIdentifier: String,
    ) {
        val dlq = dlqFor(processingGroup)
        dlq.deadLetterSequence(sequenceIdentifier)
            .forEach { dlq.evict(it) }
    }

    /**
     *
     */
    fun delete(
        processingGroup: String,
        sequenceIdentifier: String,
        messageIdentifier: String
    ) {
        val dlq = dlqFor(processingGroup)
        dlq.deadLetterSequence(sequenceIdentifier)
            .first { it.message().identifier == messageIdentifier }
            .let { dlq.evict(it) }
    }

    private fun dlqFor(processingGroup: String): SequencedDeadLetterQueue<EventMessage<*>> =
        eventProcessingConfig
            .deadLetterQueue(processingGroup)
            .orElseThrow {
                IllegalArgumentException(
                    "There's no dead-letter queue configured for Processing Group [$processingGroup]!"
                )
            }

    /**
     *
     */
    fun process(
        processingGroup: String,
        messageIdentifier: String
    ): Boolean {
        return letterProcessorFor(processingGroup).process { it.message().identifier == messageIdentifier }
    }

    private fun letterProcessorFor(processingGroup: String) =
        eventProcessingConfig
            .sequencedDeadLetterProcessor(processingGroup)
            .orElseThrow {
                IllegalArgumentException(
                    "There's no dead-letter queue configured for Processing Group [$processingGroup]!"
                )
            }

}
