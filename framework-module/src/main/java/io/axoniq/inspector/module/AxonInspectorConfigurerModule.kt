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

package io.axoniq.inspector.module

import io.axoniq.inspector.module.client.RSocketInspectorClient
import io.axoniq.inspector.module.client.RSocketMessageResponder
import io.axoniq.inspector.module.client.ServerProcessorReporter
import io.axoniq.inspector.module.client.SetupPayloadCreator
import io.axoniq.inspector.module.eventprocessor.DeadLetterManager
import io.axoniq.inspector.module.eventprocessor.EventProcessorManager
import io.axoniq.inspector.module.eventprocessor.ProcessorMetricsRegistry
import io.axoniq.inspector.module.eventprocessor.ProcessorReportCreator
import io.axoniq.inspector.module.messaging.HandlerMetricsRegistry
import io.axoniq.inspector.module.messaging.InspectorDispatchInterceptor
import io.axoniq.inspector.module.messaging.InspectorHandlerProcessorInterceptor
import io.axoniq.inspector.module.messaging.InspectorSpanFactory
import org.axonframework.config.Configurer
import org.axonframework.config.ConfigurerModule
import org.axonframework.tracing.SpanFactory

class AxonInspectorConfigurerModule(
    private val properties: AxonInspectorProperties
) : ConfigurerModule {

    override fun configureModule(configurer: Configurer) {
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
            .registerComponent(RSocketMessageResponder::class.java) {
                RSocketMessageResponder(
                    it.getComponent(EventProcessorManager::class.java),
                    it.getComponent(ProcessorReportCreator::class.java),
                    it.getComponent(DeadLetterManager::class.java),
                )
            }
            .registerComponent(RSocketInspectorClient::class.java) {
                RSocketInspectorClient(
                    properties,
                    it.getComponent(SetupPayloadCreator::class.java),
                    it.getComponent(RSocketMessageResponder::class.java),
                )
            }
            .registerComponent(ServerProcessorReporter::class.java) {
                ServerProcessorReporter(
                    it.getComponent(RSocketInspectorClient::class.java),
                    it.getComponent(ProcessorReportCreator::class.java)
                )
            }
            .registerComponent(HandlerMetricsRegistry::class.java) {
                HandlerMetricsRegistry(it.getComponent(RSocketInspectorClient::class.java))
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
