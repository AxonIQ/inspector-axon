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

package io.axoniq.inspector.module.client

import com.fasterxml.jackson.databind.ObjectMapper
import io.axoniq.inspector.api.InspectorClientAuthentication
import io.axoniq.inspector.api.InspectorClientIdentifier
import io.axoniq.inspector.module.AxonInspectorProperties
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.core.RSocketConnector
import io.rsocket.metadata.*
import io.rsocket.transport.netty.client.TcpClientTransport
import io.rsocket.util.DefaultPayload
import org.axonframework.lifecycle.Lifecycle
import org.axonframework.lifecycle.Phase
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.netty.tcp.TcpClient
import java.lang.management.ManagementFactory
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.log

class RSocketInspectorClient(
    private val properties: AxonInspectorProperties,
    private val setupPayloadCreator: SetupPayloadCreator,
    private val nodeName: String = ManagementFactory.getRuntimeMXBean().name,
) : Lifecycle {
    private val handlers: MutableList<RegisteredRsocketMessageHandler> = mutableListOf()

    fun registerHandlerWithoutPayload(route: String, handler: () -> Any) {
        handlers.add(PayloadlessRegisteredRsocketMessageHandler(route, handler))
    }

    fun <T> registerHandlerWithPayload(route: String, payloadType: Class<T>, handler: (T) -> Any) {
        handlers.add(PayloadRegisteredRsocketMessageHandler(route, payloadType, handler))
    }

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val mapper = ObjectMapper().findAndRegisterModules()
    private val executor = Executors.newScheduledThreadPool(1);

    private lateinit var rsocket: RSocket
    private var connected = false
    private var retries = 0

    override fun registerLifecycleHandlers(registry: Lifecycle.LifecycleRegistry) {
        registry.onStart(Phase.EXTERNAL_CONNECTIONS, this::start)
    }

    fun send(route: String, payload: Any): Mono<Void> {
        if (!connected) {
            return Mono.error { IllegalStateException("Connection to Inspector Axon is interrupted") }
        }
        return rsocket.requestResponse(DefaultPayload.create(encodePayload(payload), createRoutingMetadata(route)))
            .doOnError { error ->
                logger.warn("Was unable to send request to Inspector Axon. Route: {}", route, error)
            }.then()
    }

    private fun createRoutingMetadata(route: String): CompositeByteBuf {
        val metadata: CompositeByteBuf = ByteBufAllocator.DEFAULT.compositeBuffer()
        val routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT, listOf(route))
        CompositeMetadataCodec.encodeAndAddMetadata(
            metadata,
            ByteBufAllocator.DEFAULT,
            WellKnownMimeType.MESSAGE_RSOCKET_ROUTING,
            routingMetadata.content
        )
        return metadata
    }

    private fun encodePayload(payload: Any): CompositeByteBuf {
        val payloadBuffer: CompositeByteBuf = ByteBufAllocator.DEFAULT.compositeBuffer()
        payloadBuffer.writeBytes(mapper.writeValueAsBytes(payload))
        return payloadBuffer

    }

    private fun createAuthenticationMetadata(auth: InspectorClientAuthentication): CompositeByteBuf {
        val metadata: CompositeByteBuf = ByteBufAllocator.DEFAULT.compositeBuffer()
        val routingMetadata = TaggingMetadataCodec.createRoutingMetadata(ByteBufAllocator.DEFAULT, listOf("client"))
        val authMetadata = ByteBufAllocator.DEFAULT.compositeBuffer()
        authMetadata.writeBytes(auth.toBearerToken().toByteArray())
        CompositeMetadataCodec.encodeAndAddMetadata(
            metadata,
            ByteBufAllocator.DEFAULT,
            WellKnownMimeType.MESSAGE_RSOCKET_AUTHENTICATION,
            authMetadata
        )
        CompositeMetadataCodec.encodeAndAddMetadata(
            metadata,
            ByteBufAllocator.DEFAULT,
            WellKnownMimeType.MESSAGE_RSOCKET_ROUTING,
            routingMetadata.content
        )
        return metadata
    }

    fun start() {
        connect()
        executor.scheduleWithFixedDelay({
            if (!connected) {
                logger.info("Reconnecting Inspector Axon...")
                connect()
            }
        }, 5000, 10000, TimeUnit.MILLISECONDS)
    }

    fun connect() {
        try {
            rsocket = createRSocket()
        } catch (e: Exception) {
            retries++
            logger.info("Failed to connect to Inspector Axon", e)
        }
    }

    private fun createRSocket(): RSocket {
        val authentication = InspectorClientAuthentication(
            identification = InspectorClientIdentifier(
                workspaceId = properties.workspaceId,
                environmentId = properties.environmentId,
                applicationName = properties.applicationName,
                nodeName = nodeName
            ),
            accessToken = properties.accessToken
        )

        val rsocket = RSocketConnector.create()
            .metadataMimeType(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.string)
            .dataMimeType(WellKnownMimeType.APPLICATION_JSON.string)
            .setupPayload(
                DefaultPayload.create(
                    encodePayload(setupPayloadCreator.createReport()),
                    createAuthenticationMetadata(authentication)
                )
            )
            .acceptor { setup, sendingRSocket ->
                sendingRSocket.onClose()
                    .doOnError { logger.info("error", it) }
                    .subscribe()
                Mono.just(object : RSocket {
                    override fun requestResponse(payload: Payload): Mono<Payload> {
                        val route = routeFromPayload(payload)
                        val matchingHandler = handlers.firstOrNull { it.route == route }
                            ?: throw IllegalArgumentException("No handler registered for route $route")
                        val result = when (matchingHandler) {
                            is PayloadlessRegisteredRsocketMessageHandler ->
                                handleMessageWithoutPayload(matchingHandler, route)

                            is PayloadRegisteredRsocketMessageHandler<*> -> {
                                handleMessageWithPayload(matchingHandler, payload, route)
                            }

                            else -> throw IllegalArgumentException("Unknown handler type - should not happen!")
                        }
                        return Mono.just(result).map { DefaultPayload.create(encodePayload(it)) }
                    }
                })
            }
            .connect(tcpClientTransport())
            .block()!!
        return rsocket
    }

    private fun handleMessageWithoutPayload(
        matchingHandler: PayloadlessRegisteredRsocketMessageHandler,
        route: String,
    ): Any {
        logger.info("Received Inspector Axon message for route [$route] without payload")
        return matchingHandler.handler.invoke()
    }

    private fun <T> handleMessageWithPayload(
        matchingHandler: PayloadRegisteredRsocketMessageHandler<T>,
        payload: Payload,
        route: String,
    ): Any {
        val data = payload.dataUtf8
        if (matchingHandler.payloadType == String::class.java) {
            logger.info("Received Inspector Axon message for route [$route] with payload: [{}]", data)
            return matchingHandler.handler.invoke(data as T)
        }
        val payload = mapper.readValue(data, matchingHandler.payloadType)
        return matchingHandler.handler.invoke(payload)
    }

    private fun routeFromPayload(payload: Payload): String {
        val compositeMetadata = CompositeMetadata(payload.metadata(), false)
        val routeMetadata =
            compositeMetadata.firstOrNull { it.mimeType == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string }
                ?: throw IllegalArgumentException("Request contained no route metadata!")
        return RoutingMetadata(routeMetadata.content).iterator().next()
            ?: throw IllegalArgumentException("Request contained no route metadata!")
    }

    private fun tcpClientTransport() =
        TcpClientTransport.create(tcpClient())

    private fun tcpClient(): TcpClient {
        val client = TcpClient.create()
            .host(properties.host)
            .port(properties.port)
            .doOnConnected {
                logger.info("Inspector Axon connected")
                retries = 0
                connected = true
            }
            .doOnConnect {
                logger.info("Inspector Axon connecting")
            }
            .doOnDisconnected {
                logger.info("Inspector Axon disconnected")
                connected = false
            }
        return if (properties.secure) {
            return client.secure()
        } else client
    }

    fun isConnected() = connected

    fun dispose() {
        if (connected) {
            rsocket.dispose()
        }
    }

    private interface RegisteredRsocketMessageHandler {
        val route: String
    }

    private data class PayloadRegisteredRsocketMessageHandler<T>(
        override val route: String,
        val payloadType: Class<T>,
        val handler: (T) -> Any
    ) : RegisteredRsocketMessageHandler

    private data class PayloadlessRegisteredRsocketMessageHandler(
        override val route: String,
        val handler: () -> Any
    ) : RegisteredRsocketMessageHandler
}
