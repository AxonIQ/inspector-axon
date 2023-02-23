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
