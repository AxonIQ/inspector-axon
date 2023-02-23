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

package io.axoniq.inspector.starter

import io.axoniq.inspector.module.AxonInspectorConfigurerModule
import io.axoniq.inspector.module.AxonInspectorProperties
import io.axoniq.inspector.module.messaging.HandlerMetricsRegistry
import io.axoniq.inspector.module.messaging.InspectorSpanFactory
import org.axonframework.config.Configuration
import org.axonframework.tracing.MultiSpanFactory
import org.axonframework.tracing.NoOpSpanFactory
import org.axonframework.tracing.SpanFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(InspectorProperties::class)
class AxonInspectorAutoConfiguration {

    @Bean
    fun configurerModule(properties: InspectorProperties, applicationContext: ApplicationContext) =
        AxonInspectorConfigurerModule(
            AxonInspectorProperties(
                host = properties.host,
                port = properties.port,
                secure = properties.secure,
                workspaceId = properties.workspaceId,
                environmentId = properties.environmentId,
                accessToken = properties.accessToken,
                applicationName = applicationContext.id!!
            )
        )

    @Bean
    fun spanFactoryInspectorPostProcessor(configuration: Configuration) = object : BeanPostProcessor {
        override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
            if (bean !is SpanFactory || bean is InspectorSpanFactory) {
                return bean
            }
            val spanFactory = InspectorSpanFactory(configuration.getComponent(HandlerMetricsRegistry::class.java))
            if (bean is NoOpSpanFactory) {
                return spanFactory
            }
            return MultiSpanFactory(
                listOf(
                    spanFactory,
                    bean
                )
            )
        }
    }
}
