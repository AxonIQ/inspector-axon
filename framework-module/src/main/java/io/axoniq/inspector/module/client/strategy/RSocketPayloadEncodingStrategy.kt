package io.axoniq.inspector.module.client.strategy

import io.netty.buffer.ByteBuf
import io.rsocket.Payload
import io.rsocket.metadata.WellKnownMimeType

interface RSocketPayloadEncodingStrategy {
    fun getMimeType(): WellKnownMimeType
    fun encode(payload: Any, metadata: ByteBuf? = null): Payload
    fun <T> decode(payload: Payload, expectedType: Class<T>): T
}
