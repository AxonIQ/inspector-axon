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

import io.axoniq.inspector.module.client.strategy.RSocketPayloadEncodingStrategy
import io.rsocket.Payload
import io.rsocket.RSocket
import io.rsocket.metadata.CompositeMetadata
import io.rsocket.metadata.RoutingMetadata
import io.rsocket.metadata.WellKnownMimeType
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class RSocketHandlerRegistrar(
    private val encodingStrategy: RSocketPayloadEncodingStrategy
) : RSocket {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val handlers: MutableList<RegisteredRsocketMessageHandler> = mutableListOf()

    fun registerHandlerWithoutPayload(route: String, handler: () -> Any) {
        logger.info("Registered Inspector handler for route {}", route)
        handlers.add(PayloadlessRegisteredRsocketMessageHandler(route, handler))
    }

    fun <T> registerHandlerWithPayload(route: String, payloadType: Class<T>, handler: (T) -> Any) {
        logger.info("Registered Inspector handler for route {}", route)
        handlers.add(PayloadRegisteredRsocketMessageHandler(route, payloadType, handler))
    }

    fun createRespondingRSocketFor(rSocket: RSocket) = object : RSocket {
        override fun requestResponse(payload: Payload): Mono<Payload> {
            val route = routeFromPayload(payload)
            if (route == "authentication_failed") {
                logger.warn("Authentication to Inspector Axon failed. Are your properties set correctly?")
                rSocket.dispose()
                return Mono.empty()
            }
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
            return Mono.just(result).map { encodingStrategy.encode(it) }
        }
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
        val decodedPayload = encodingStrategy.decode(payload, matchingHandler.payloadType)
        logger.info("Received Inspector Axon message for route [$route] with payload: [{}]", decodedPayload)
        return matchingHandler.handler.invoke(decodedPayload)
    }

    private fun routeFromPayload(payload: Payload): String {
        val compositeMetadata = CompositeMetadata(payload.metadata(), false)
        val routeMetadata =
            compositeMetadata.firstOrNull { it.mimeType == WellKnownMimeType.MESSAGE_RSOCKET_ROUTING.string }
                ?: throw IllegalArgumentException("Request contained no route metadata!")
        return RoutingMetadata(routeMetadata.content).iterator().next()
            ?: throw IllegalArgumentException("Request contained no route metadata!")
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
