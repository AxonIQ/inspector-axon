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

package io.axoniq.inspector.module.client.strategy

import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.rsocket.Payload
import io.rsocket.metadata.WellKnownMimeType
import io.rsocket.util.DefaultPayload

class CborEncodingStrategy : RSocketPayloadEncodingStrategy {
    private val mapper = CBORMapper.builder().build().findAndRegisterModules()

    override fun getMimeType(): WellKnownMimeType {
        return WellKnownMimeType.APPLICATION_CBOR
    }

    override fun encode(payload: Any, metadata: ByteBuf?): Payload {
        val payloadBuffer: CompositeByteBuf = ByteBufAllocator.DEFAULT.compositeBuffer()
        payloadBuffer.writeBytes(mapper.writeValueAsBytes(payload))
        return DefaultPayload.create(payloadBuffer, metadata)
    }

    override fun <T> decode(payload: Payload, expectedType: Class<T>): T {
        if (expectedType == String::class.java) {
            return payload.dataUtf8 as T
        }

        return mapper.readValue(payload.data.array(), expectedType)
    }
}
