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

package io.axoniq.inspector.client

import io.axoniq.inspector.api.InspectorClientAuthentication
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.rsocket.metadata.CompositeMetadataCodec
import io.rsocket.metadata.TaggingMetadataCodec
import io.rsocket.metadata.WellKnownMimeType


fun CompositeByteBuf.addRouteMetadata(route: String) {
    val routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT, listOf(route))
    CompositeMetadataCodec.encodeAndAddMetadata(
        this,
        ByteBufAllocator.DEFAULT,
        WellKnownMimeType.MESSAGE_RSOCKET_ROUTING,
        routingMetadata.content
    )
}

fun CompositeByteBuf.addAuthMetadata(auth: InspectorClientAuthentication) {
    val authMetadata = ByteBufAllocator.DEFAULT.compositeBuffer()
    authMetadata.writeBytes(auth.toBearerToken().toByteArray())
    CompositeMetadataCodec.encodeAndAddMetadata(
        this,
        ByteBufAllocator.DEFAULT,
        WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION,
        authMetadata
    )
}
