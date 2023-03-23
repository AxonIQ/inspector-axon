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

package io.axoniq.inspector

import io.axoniq.inspector.client.RSocketHandlerRegistrar
import io.axoniq.inspector.client.RSocketInspectorClient
import io.axoniq.inspector.client.ServerProcessorReporter
import io.axoniq.inspector.client.SetupPayloadCreator
import io.axoniq.inspector.client.strategy.CborEncodingStrategy
import io.axoniq.inspector.client.strategy.RSocketPayloadEncodingStrategy
import io.axoniq.inspector.eventprocessor.*
import io.axoniq.inspector.eventprocessor.metrics.InspectorHandlerProcessorInterceptor
import io.axoniq.inspector.eventprocessor.metrics.ProcessorMetricsRegistry
import io.axoniq.inspector.messaging.*
import org.axonframework.common.ReflectionUtils
import org.axonframework.config.AggregateConfiguration
import org.axonframework.config.Configurer
import org.axonframework.config.ConfigurerModule
import org.axonframework.eventsourcing.EventSourcingRepository
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.tracing.SpanFactory
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class AxonInspectorConfigurerModule(
    private val properties: io.axoniq.inspector.AxonInspectorProperties
) : ConfigurerModule {

    override fun configureModule(configurer: Configurer) {
        val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(properties.threadPoolSize);
        configurer
            .registerComponent(ProcessorMetricsRegistry::class.java) {
                ProcessorMetricsRegistry()
            }
            .registerComponent(ProcessorReportCreator::class.java) {
                ProcessorReportCreator(
                    it.eventProcessingConfiguration(),
                    it.getComponent(ProcessorMetricsRegistry::class.java)
                )
            }
            .registerComponent(SetupPayloadCreator::class.java) {
                SetupPayloadCreator(it)
            }
            .registerComponent(EventProcessorManager::class.java) {
                EventProcessorManager(it.eventProcessingConfiguration())
            }
            .registerComponent(DeadLetterManager::class.java) {
                DeadLetterManager(it.eventProcessingConfiguration(), it.eventSerializer())
            }
            .registerComponent(RSocketPayloadEncodingStrategy::class.java) {
                CborEncodingStrategy()
            }
            .registerComponent(RSocketHandlerRegistrar::class.java) {
                RSocketHandlerRegistrar(
                    it.getComponent(RSocketPayloadEncodingStrategy::class.java)
                )
            }
            .registerComponent(RSocketProcessorResponder::class.java) {
                RSocketProcessorResponder(
                    it.getComponent(EventProcessorManager::class.java),
                    it.getComponent(ProcessorReportCreator::class.java),
                    it.getComponent(RSocketHandlerRegistrar::class.java),
                )
            }
            .registerComponent(RSocketDlqResponder::class.java) {
                RSocketDlqResponder(
                    it.getComponent(DeadLetterManager::class.java),
                    it.getComponent(RSocketHandlerRegistrar::class.java),
                )
            }
            .registerComponent(RSocketInspectorClient::class.java) {
                RSocketInspectorClient(
                    properties,
                    it.getComponent(SetupPayloadCreator::class.java),
                    it.getComponent(RSocketHandlerRegistrar::class.java),
                    it.getComponent(RSocketPayloadEncodingStrategy::class.java),
                    executor,
                )
            }
            .registerComponent(ServerProcessorReporter::class.java) {
                ServerProcessorReporter(
                    it.getComponent(RSocketInspectorClient::class.java),
                    it.getComponent(ProcessorReportCreator::class.java),
                    executor,
                )
            }
            .registerComponent(HandlerMetricsRegistry::class.java) {
                HandlerMetricsRegistry(
                    it.getComponent(RSocketInspectorClient::class.java),
                    executor,
                )
            }
            .registerComponent(SpanFactory::class.java) {
                InspectorSpanFactory(it.getComponent(HandlerMetricsRegistry::class.java))
            }
            .eventProcessing()
            .registerDefaultHandlerInterceptor { config, name ->
                InspectorHandlerProcessorInterceptor(
                    config.getComponent(ProcessorMetricsRegistry::class.java),
                    name,
                )
            }

        configurer.onInitialize {
            it.getComponent(ServerProcessorReporter::class.java)
            it.getComponent(RSocketProcessorResponder::class.java)
            it.getComponent(RSocketDlqResponder::class.java)

            it.onStart {
                it.findModules(AggregateConfiguration::class.java).forEach { ac ->
                    val repo = ac.repository()
                    if (repo is EventSourcingRepository) {
                        val field =
                            ReflectionUtils.fieldsOf(repo::class.java).firstOrNull { f -> f.name == "eventStore" }
                        if (field != null) {
                            val current = ReflectionUtils.getFieldValue<EventStore>(field, repo)
                            if (current !is InspectorWrappedEventStore) {
                                ReflectionUtils.setFieldValue(field, repo, InspectorWrappedEventStore(current))
                            }
                        }
                    }
                }
            }
        }

        configurer.onStart {
            val config = configurer.buildConfiguration()

            val interceptor = InspectorDispatchInterceptor(
                config.getComponent(HandlerMetricsRegistry::class.java),
            )
            config.eventBus().registerDispatchInterceptor(interceptor)
            config.commandBus().registerDispatchInterceptor(interceptor)
            config.queryBus().registerDispatchInterceptor(interceptor)
            config.deadlineManager()?.registerDispatchInterceptor(interceptor)
        }
    }
}
