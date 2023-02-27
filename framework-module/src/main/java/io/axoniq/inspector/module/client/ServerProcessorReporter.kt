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

import io.axoniq.inspector.api.Routes
import io.axoniq.inspector.module.eventprocessor.ProcessorReportCreator
import mu.KotlinLogging
import org.axonframework.lifecycle.Lifecycle
import org.axonframework.lifecycle.Phase
import java.util.concurrent.TimeUnit

class ServerProcessorReporter(
    private val client: RSocketInspectorClient,
    private val processorReportCreator: ProcessorReportCreator
) : Lifecycle {

    private val logger = KotlinLogging.logger { }

    override fun registerLifecycleHandlers(lifecycleRegistry: Lifecycle.LifecycleRegistry) {
        lifecycleRegistry.onStart(Phase.INSTRUCTION_COMPONENTS, this::scheduleSafeNextReport)
        lifecycleRegistry.onShutdown(Phase.EXTERNAL_CONNECTIONS, this::setShutDownFlag)
    }

    fun start() {
        scheduleSafeNextReport()
    }

    fun setShutDownFlag() {
        client.dispose()
    }

    private fun scheduleSafeNextReport() {
        executor.schedule({
            try {
                this.report()
            } catch (e: Exception) {
                logger.error("Was unable to report processor metrics: {}", e.message)
            }
        }, 1000, TimeUnit.MILLISECONDS)
    }

    private fun report() {
        client.send(Routes.EventProcessor.REPORT, processorReportCreator.createReport()).block()
    }
}

