package io.axoniq.inspector.module.client

import io.axoniq.inspector.api.InspectorClientAuthentication
import io.axoniq.inspector.api.InspectorClientAuthentication.Companion.AUTHENTICATION_MIME_TYPE
import io.axoniq.inspector.api.InspectorClientIdentifier
import io.axoniq.inspector.api.RSocketConfiguration
import io.axoniq.inspector.module.AxonInspectorProperties
import io.rsocket.SocketAcceptor
import io.rsocket.transport.netty.client.TcpClientTransport
import org.axonframework.lifecycle.Lifecycle
import org.axonframework.lifecycle.Phase
import org.slf4j.LoggerFactory
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler
import reactor.core.publisher.Mono
import reactor.netty.tcp.TcpClient
import reactor.util.retry.Retry
import java.lang.management.ManagementFactory
import java.time.Duration

class RSocketInspectorClient(
    private val properties: AxonInspectorProperties,
    private val setupPayloadCreator: SetupPayloadCreator,
    private val messageResponder: RSocketMessageResponder,
    private val nodeName: String = ManagementFactory.getRuntimeMXBean().name,
) : Lifecycle {
    private lateinit var requester: RSocketRequester
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var connected = false

    override fun registerLifecycleHandlers(registry: Lifecycle.LifecycleRegistry) {
        registry.onStart(Phase.EXTERNAL_CONNECTIONS, this::start)
    }

    fun send(route: String, payload: Any): Mono<Void> {
        return requester.route(route).data(payload)
            .retrieveMono(Void::class.java)
            .doOnError { error ->
                logger.warn("Was unable to send request to Inspector Axon. Route: {}", route, error)
            }
    }

    fun start() {
        val strategies = RSocketConfiguration.createRSocketStrategies()
        val responder: SocketAcceptor = RSocketMessageHandler.responder(strategies, messageResponder)

        val authentication = InspectorClientAuthentication(
            identification = InspectorClientIdentifier(
                workspaceId = properties.workspaceId,
                environmentId = properties.environmentId,
                applicationName = properties.applicationName,
                nodeName = nodeName
            ),
            accessToken = properties.accessToken
        )

        requester = RSocketRequester.builder()
            .setupMetadata(authentication.toBearerToken(), AUTHENTICATION_MIME_TYPE)
            .setupRoute("client")
            .setupData(setupPayloadCreator.createReport())
            .rsocketStrategies(strategies)
            .rsocketConnector { rSocketConnector ->
                rSocketConnector.acceptor(responder)
                    .reconnect(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2)))
            }
            .transport(tcpClientTransport())
    }

    private fun tcpClientTransport() =
        TcpClientTransport.create(tcpClient())

    private fun tcpClient(): TcpClient {
        val client = TcpClient.create()
            .host(properties.host)
            .port(properties.port)
            .doOnConnected {
                logger.info("Inspector Axon connected")
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
        requester.dispose()
    }
}
