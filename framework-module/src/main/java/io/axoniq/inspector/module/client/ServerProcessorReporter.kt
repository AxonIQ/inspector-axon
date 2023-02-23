package io.axoniq.inspector.module.client

import io.axoniq.inspector.api.Routes
import io.axoniq.inspector.module.eventprocessor.ProcessorReportCreator
import mu.KotlinLogging
import org.axonframework.lifecycle.Lifecycle
import org.axonframework.lifecycle.Phase
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ServerProcessorReporter(
    private val client: RSocketInspectorClient,
    private val processorReportCreator: ProcessorReportCreator
) : Lifecycle {
    private val logger = KotlinLogging.logger { }

    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val shutdown = AtomicBoolean(false)

    override fun registerLifecycleHandlers(lifecycleRegistry: Lifecycle.LifecycleRegistry) {
        lifecycleRegistry.onStart(Phase.INSTRUCTION_COMPONENTS, this::scheduleSafeNextReport)
        lifecycleRegistry.onShutdown(Phase.EXTERNAL_CONNECTIONS, this::setShutDownFlag)
    }

    fun start() {
        scheduleSafeNextReport()
    }

    fun setShutDownFlag() {
        this.shutdown.set(true)
        client.dispose()
    }

    private fun scheduleSafeNextReport() {
        executor.schedule({
            try {
                this.report()
            } catch (e: Exception) {
                logger.error("Was unable to report processor metrics: {}", e.message)
            }
            if(!this.shutdown.get()) {
                this.scheduleSafeNextReport()
            }
        }, 1000, TimeUnit.MILLISECONDS)
    }

    private fun report() {
        if(this.shutdown.get()) {
            return
        }
        client.send(Routes.EventProcessor.REPORT, processorReportCreator.createReport()).block()
    }
}

