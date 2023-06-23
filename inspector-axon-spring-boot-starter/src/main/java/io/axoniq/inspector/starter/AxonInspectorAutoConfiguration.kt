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

import io.axoniq.inspector.AxonInspectorConfigurerModule
import io.axoniq.inspector.AxonInspectorProperties
import io.axoniq.inspector.messaging.InspectorSpanFactory
import org.axonframework.config.ConfigurerModule
import org.axonframework.tracing.MultiSpanFactory
import org.axonframework.tracing.NoOpSpanFactory
import org.axonframework.tracing.SpanFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@AutoConfiguration
@EnableConfigurationProperties(InspectorProperties::class)
@ConditionalOnProperty(name = ["axon.inspector.enabled"], matchIfMissing = true)
class AxonInspectorAutoConfiguration {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Bean
    @ConditionalOnProperty("axon.inspector.credentials", matchIfMissing = false)
    fun inspectorAxonConfigurerModule(
            properties: InspectorProperties,
            applicationContext: ApplicationContext
    ): AxonInspectorConfigurerModule {
        val credentials =
                properties.credentials ?: throw IllegalStateException("No known credentials for Inspector Axon!")
        val applicationName = properties.applicationName ?: applicationContext.id!!
        val (workspaceId, environmentId, accessToken) = credentials.split(":")
        logger.info(
                "Setting up Inspector Axon work Workspace {} and Environment {}. This application will be registered as {}",
                workspaceId,
                environmentId,
                applicationName
        )
        return AxonInspectorConfigurerModule(
                properties = AxonInspectorProperties(
                        host = properties.host,
                        port = properties.port,
                        initialDelay = properties.initialDelay,
                        secure = properties.secure,
                        workspaceId = workspaceId,
                        environmentId = environmentId,
                        accessToken = accessToken,
                        applicationName = applicationName
                ),
                configureSpanFactory = false
        )
    }

    @Bean
    @ConditionalOnProperty("axon.inspector.credentials", havingValue = "", matchIfMissing = true)
    fun missingInspectorAxonCredentialsConfigurerModule() = ConfigurerModule {
        logger.warn("No credentials were provided for the connection to Inspector Axon. Please provide them as instructed through the 'axon.inspector.credentials' property.")
    }

    @Bean
    @ConditionalOnProperty("axon.inspector.credentials", matchIfMissing = false)
    fun spanFactoryInspectorPostProcessor(): BeanPostProcessor = object : BeanPostProcessor {
        override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
            if (bean !is SpanFactory || bean is InspectorSpanFactory) {
                return bean
            }
            val spanFactory = InspectorSpanFactory()
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
