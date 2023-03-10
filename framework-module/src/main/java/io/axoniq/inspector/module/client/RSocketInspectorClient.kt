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

import io.axoniq.inspector.api.InspectorClientAuthentication
import io.axoniq.inspector.api.InspectorClientIdentifier
import io.axoniq.inspector.module.AxonInspectorProperties
import io.axoniq.inspector.module.client.strategy.RSocketPayloadEncodingStrategy
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.CompositeByteBuf
import io.rsocket.RSocket
import io.rsocket.core.RSocketConnector
import io.rsocket.metadata.*
import io.rsocket.transport.netty.client.TcpClientTransport
import org.axonframework.lifecycle.Lifecycle
import org.axonframework.lifecycle.Phase
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.netty.tcp.TcpClient
import java.lang.management.ManagementFactory
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Suppress("MemberVisibilityCanBePrivate")
class RSocketInspectorClient(
    private val properties: AxonInspectorProperties,
    private val setupPayloadCreator: SetupPayloadCreator,
    private val registrar: RSocketHandlerRegistrar,
    private val encodingStrategy: RSocketPayloadEncodingStrategy,
    private val executor: ScheduledExecutorService,
    private val nodeName: String = ManagementFactory.getRuntimeMXBean().name,
) : Lifecycle {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private lateinit var rsocket: RSocket
    private var connected = false

    override fun registerLifecycleHandlers(registry: Lifecycle.LifecycleRegistry) {
        registry.onStart(Phase.EXTERNAL_CONNECTIONS, this::start)
    }

    fun send(route: String, payload: Any): Mono<Void> {
        if (!connected) {
            return Mono.empty()
        }
        return rsocket
            .requestResponse(encodingStrategy.encode(payload, createRoutingMetadata(route)))
            .doOnError {
                if (it.message!!.contains("Access Denied")) {
                    logger.info("Was unable to send call to Inspector Axon since authentication was incorrect!")
                }
            }
            .then()
    }

    fun start() {
        executor.scheduleWithFixedDelay({
            if (!connected) {
                logger.info("Reconnecting Inspector Axon...")
                connect()
            }
        }, 2500, 10000, TimeUnit.MILLISECONDS)
    }

    fun connect() {
        try {
            rsocket = createRSocket()
            connected = true
        } catch (e: Exception) {
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

        val setupPayload =
            encodingStrategy.encode(setupPayloadCreator.createReport(), createSetupMetadata(authentication))
        val rsocket = RSocketConnector.create()
            .metadataMimeType(WellKnownMimeType.MESSAGE_RSOCKET_COMPOSITE_METADATA.string)
            .dataMimeType(encodingStrategy.getMimeType().string)
            .setupPayload(setupPayload)
            .acceptor { _, rsocket ->
                Mono.just(registrar.createRespondingRSocketFor(rsocket))
            }
            .connect(tcpClientTransport())
            .block()!!
        return rsocket
    }

    private fun createRoutingMetadata(route: String): CompositeByteBuf {
        val metadata: CompositeByteBuf = ByteBufAllocator.DEFAULT.compositeBuffer()
        metadata.addRouteMetadata(route)
        return metadata
    }

    private fun createSetupMetadata(auth: InspectorClientAuthentication): CompositeByteBuf {
        val metadata: CompositeByteBuf = ByteBufAllocator.DEFAULT.compositeBuffer()
        metadata.addRouteMetadata("client")
        metadata.addAuthMetadata(auth)
        return metadata
    }

    private fun tcpClientTransport() =
        TcpClientTransport.create(tcpClient())

    private fun tcpClient(): TcpClient {
        val client = TcpClient.create()
            .host(properties.host)
            .port(properties.port)
            .doOnConnected {
                logger.info("Inspector Axon connected")
            }
            .doOnConnect {
                logger.info("Inspector Axon connecting...")
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
}
