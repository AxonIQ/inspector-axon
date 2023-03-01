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

import io.axoniq.inspector.api.*
import io.axoniq.inspector.module.api.InspectorMeasuringHandlerInterceptor
import org.axonframework.common.ReflectionUtils
import org.axonframework.config.Configuration
import org.axonframework.config.EventProcessingModule
import org.axonframework.eventhandling.MultiStreamableMessageSource
import org.axonframework.eventhandling.StreamingEventProcessor
import org.axonframework.messaging.StreamableMessageSource
import org.axonframework.serialization.upcasting.Upcaster
import org.axonframework.util.MavenArtifactVersionResolver
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAmount

class SetupPayloadCreator(
    private val configuration: Configuration,
    private val eventProcessingConfiguration: EventProcessingModule = configuration.eventProcessingConfiguration() as EventProcessingModule,
) {

    fun createReport(): SetupPayload {
        val processors = eventProcessingConfiguration.eventProcessors()
            .filter { it.value is StreamingEventProcessor }
            .map { entry ->
                entry.key
            }
        return SetupPayload(
            commandBus = commandBusInformation(),
            queryBus = queryBusInformation(),
            eventStore = eventBusInformation(),
            processors = processors.map {
                val processor =
                    eventProcessingConfiguration.eventProcessor(it, StreamingEventProcessor::class.java).get()
                ProcessorInformation(
                    name = it,
                    supportsReset = processor.supportsReset(),
                    batchSize = processor.getBatchSize(),
                    messageSourceType = processor.getMessageSource(),
                    tokenClaimInterval = processor.getTokenClaimInterval(),
                    tokenStoreClaimTimeout = processor.getStoreTokenClaimTimeout(),
                    errorHandler = eventProcessingConfiguration.errorHandler(it)::class.java.name,
                    invocationErrorHandler = eventProcessingConfiguration.listenerInvocationErrorHandler(it)::class.java.name,
                    interceptors = processor.getInterceptors("interceptors"),
                    tokenStoreType = processor.getStoreTokenStoreType(),
                    contexts = processor.contexts()
                )
            },
            versions = versionInformation(),
            upcasters = upcasters()
        )
    }

    private fun upcasters(): List<String> {
        val upcasters =
            configuration.upcasterChain().getPropertyValue<List<out Upcaster<*>>>("upcasters") ?: emptyList()
        return upcasters.map { it::class.java.name }
    }

    private val dependenciesToCheck = listOf(
        "org.axonframework:axon-messaging",
        "org.axonframework:axon-configuration",
        "org.axonframework:axon-disruptor",
        "org.axonframework:axon-eventsourcing",
        "org.axonframework:axon-legacy",
        "org.axonframework:axon-metrics",
        "org.axonframework:axon-micrometer",
        "org.axonframework:axon-modelling",
        "org.axonframework:axon-server-connector",
        "org.axonframework:axon-spring",
        "org.axonframework:axon-spring-boot-autoconfigure",
        "org.axonframework:axon-spring-boot-starter",
        "org.axonframework:axon-tracing-opentelemetry",
        "org.axonframework:axon-tracing-amqp",
        "org.axonframework.extensions.amqp:axon-amqp",
        "org.axonframework.extensions.jgroups:axon-jgroups",
        "org.axonframework.extensions.kafka:axon-kafka",
        "org.axonframework.extensions.mongo:axon-mongo",
        "org.axonframework.extensions.reactor:axon-reactor",
        "org.axonframework.extensions.springcloud:axon-springcloud",
        "org.axonframework.extensions.tracing:axon-tracing",
        "io.axoniq:axonserver-connector-java",
        "io.axoniq.inspector.connectors:inspector-framework-module",
    )

    private fun versionInformation(): Versions {
        return Versions(
            frameworkVersion = resolveVersion("org.axonframework:axon-messaging")!!,
            moduleVersions = dependenciesToCheck.map { ModuleVersion(it, resolveVersion(it)) }
        )
    }

    private fun resolveVersion(dep: String): String? {
        val (groupId, artifactId) = dep.split(":")
        return MavenArtifactVersionResolver(
            groupId,
            artifactId,
            this::class.java.classLoader
        ).get()
    }

    private fun queryBusInformation(): QueryBusInformation {
        val bus = configuration.queryBus()
        val axonServer = bus::class.java.name == "org.axonframework.axonserver.connector.query.AxonServerQueryBus"
        val localSegmentType = if (axonServer) bus.getPropertyType("localSegment") else null
        val context = if (axonServer) bus.getPropertyValue<String>("context") else null
        val handlerInterceptors = if (axonServer) {
            bus.getPropertyValue<Any>("localSegment")?.getInterceptors("handlerInterceptors") ?: emptyList()
        } else {
            bus.getInterceptors("handlerInterceptors")
        }
        val dispatchInterceptors = bus.getInterceptors("dispatchInterceptors")
        val messageSerializer = if (axonServer) {
            bus.getPropertyValue<Any>("serializer")?.getSerializerType("messageSerializer")
        } else null
        val serializer = if (axonServer) {
            bus.getPropertyValue<Any>("serializer")?.getSerializerType("serializer")
        } else null
        return QueryBusInformation(
            type = bus::class.java.name,
            axonServer = axonServer,
            localSegmentType = localSegmentType,
            context = context,
            handlerInterceptors = handlerInterceptors,
            dispatchInterceptors = dispatchInterceptors,
            messageSerializer = messageSerializer,
            serializer = serializer,
        )
    }

    private fun eventBusInformation(): EventStoreInformation {
        val bus = configuration.eventBus()
        val axonServer =
            bus::class.java.name == "org.axonframework.axonserver.connector.event.axon.AxonServerEventStore"
        val context = if (axonServer) {
            bus.getPropertyValue<Any>("storageEngine")?.getPropertyValue<String>("context")
        } else null
        val dispatchInterceptors = bus.getInterceptors("dispatchInterceptors")
        return EventStoreInformation(
            type = bus::class.java.name,
            axonServer = axonServer,
            context = context,
            dispatchInterceptors = dispatchInterceptors,
            eventSerializer = bus.getPropertyValue<Any>("storageEngine")?.getSerializerType("eventSerializer"),
            snapshotSerializer = bus.getPropertyValue<Any>("storageEngine")?.getSerializerType("snapshotSerializer"),
        )
    }

    private fun commandBusInformation(): CommandBusInformation {
        val bus = configuration.commandBus()
        val axonServer = bus::class.java.name == "org.axonframework.axonserver.connector.command.AxonServerCommandBus"
        val localSegmentType = if (axonServer) bus.getPropertyType("localSegment") else null
        val context = if (axonServer) bus.getPropertyValue<String>("context") else null
        val handlerInterceptors = if (axonServer) {
            bus.getPropertyValue<Any>("localSegment")?.getInterceptors("handlerInterceptors", "invokerInterceptors")
                ?: emptyList()
        } else {
            bus.getInterceptors("handlerInterceptors", "invokerInterceptors")
        }
        val dispatchInterceptors = bus.getInterceptors("dispatchInterceptors")
        val serializer = if (axonServer) {
            bus.getPropertyValue<Any>("serializer")?.getSerializerType("messageSerializer")
        } else null
        return CommandBusInformation(
            type = bus::class.java.name,
            axonServer = axonServer,
            localSegmentType = localSegmentType,
            context = context,
            handlerInterceptors = handlerInterceptors,
            dispatchInterceptors = dispatchInterceptors,
            messageSerializer = serializer
        )
    }

    private fun <T> Any.getPropertyValue(fieldName: String): T? {
        val field = ReflectionUtils.fieldsOf(this::class.java).firstOrNull { it.name == fieldName } ?: return null
        return ReflectionUtils.getMemberValue(
            field,
            this
        )
    }

    private fun Any.getPropertyType(fieldName: String): String {
        return ReflectionUtils.getMemberValue<Any>(
            ReflectionUtils.fieldsOf(this::class.java).first { it.name == fieldName },
            this
        ).let { it::class.java.name }
    }

    private fun StreamingEventProcessor.getBatchSize(): Int = getPropertyValue("batchSize") ?: -1
    private fun StreamingEventProcessor.getMessageSource(): String = getPropertyType("messageSource")
    private fun StreamingEventProcessor.getTokenClaimInterval(): Long = getPropertyValue("tokenClaimInterval") ?: -1
    private fun StreamingEventProcessor.getStoreTokenStoreType(): String = getPropertyType("tokenStore")
    private fun StreamingEventProcessor.getStoreTokenClaimTimeout(): Long = getPropertyValue<Any>("tokenStore")
        ?.getPropertyValue<TemporalAmount>("claimTimeout")?.let { it.get(ChronoUnit.SECONDS) * 1000 } ?: -1


    private fun Any.getInterceptors(vararg fieldNames: String): List<InterceptorInformation> {

        val interceptors = fieldNames.firstNotNullOfOrNull { this.getPropertyValue<Any>(it) } ?: return emptyList()
        if (interceptors::class.java.name == "org.axonframework.axonserver.connector.DispatchInterceptors") {
            return interceptors.getInterceptors("dispatchInterceptors")
        }
        if (interceptors !is List<*>) {
            return emptyList()
        }
        return interceptors
            .filterNotNull()
            .map {
                if (it is InspectorMeasuringHandlerInterceptor) {
                    InterceptorInformation(it.subject::class.java.name, true)
                } else InterceptorInformation(it::class.java.name, false)
            }
            .filter { !it.type.startsWith("org.axonframework.eventhandling") }
    }

    private fun Any.getSerializerType(fieldName: String): SerializerInformation? {
        val serializer = getPropertyValue<Any>(fieldName) ?: return null
        if (serializer::class.java.name == "org.axonframework.axonserver.connector.event.axon.GrpcMetaDataAwareSerializer") {
            return SerializerInformation(serializer.getPropertyType("delegate"), true)
        }
        return SerializerInformation(serializer::class.java.name, false)
    }

    private fun StreamingEventProcessor.contexts(): List<String> {
        val messageSource = getPropertyValue<StreamableMessageSource<*>>("messageSource") ?: return emptyList()
        val sources = if (messageSource is MultiStreamableMessageSource) {
            messageSource.getPropertyValue<List<StreamableMessageSource<*>>>("eventStreams") ?: emptyList()
        } else {
            listOf(messageSource)
        }
        return sources.mapNotNull { toContext(it) }.distinct()
    }

    private fun toContext(it: StreamableMessageSource<*>): String? {
        if (it::class.java.simpleName == "AxonServerEventStore") {
            return it.getPropertyValue<Any>("storageEngine")?.getPropertyValue("context")
        }
        if (it::class.java.simpleName == "AxonIQEventStorageEngine") {
            return it.getPropertyValue("context")
        }
        // Fallback
        return it.getPropertyValue("context")
    }

}


