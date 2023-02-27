package io.axoniq.inspector.module.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.axoniq.inspector.api.InspectorClientAuthentication
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.rsocket.metadata.CompositeMetadataCodec
import io.rsocket.metadata.TaggingMetadataCodec
import io.rsocket.metadata.WellKnownMimeType
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService


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

val payloadMapper: ObjectMapper = ObjectMapper().findAndRegisterModules()
val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1);
