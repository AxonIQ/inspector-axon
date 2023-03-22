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

package io.axoniq.inspector.eventprocessor

import io.axoniq.inspector.api.ProcessorSegmentId
import io.axoniq.inspector.api.ProcessorStatusReport
import io.axoniq.inspector.api.ResetDecision
import io.axoniq.inspector.api.Routes
import io.axoniq.inspector.client.RSocketHandlerRegistrar
import org.axonframework.lifecycle.Lifecycle
import org.axonframework.lifecycle.Phase
import org.slf4j.LoggerFactory

open class RSocketProcessorResponder(
    private val eventProcessorManager: EventProcessorManager,
    private val processorReportCreator: ProcessorReportCreator,
    private val registrar: RSocketHandlerRegistrar
) : Lifecycle {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun registerLifecycleHandlers(registry: Lifecycle.LifecycleRegistry) {
        registry.onStart(Phase.EXTERNAL_CONNECTIONS, this::start)
    }

    fun start() {
        registrar.registerHandlerWithPayload(Routes.EventProcessor.START, String::class.java, this::handleStart)
        registrar.registerHandlerWithPayload(Routes.EventProcessor.STOP, String::class.java, this::handleStop)
        registrar.registerHandlerWithoutPayload(Routes.EventProcessor.STATUS, this::handleStatusQuery)
        registrar.registerHandlerWithPayload(
            Routes.EventProcessor.RELEASE,
            ProcessorSegmentId::class.java,
            this::handleRelease
        )
        registrar.registerHandlerWithPayload(
            Routes.EventProcessor.SPLIT,
            ProcessorSegmentId::class.java,
            this::handleSplit
        )
        registrar.registerHandlerWithPayload(
            Routes.EventProcessor.MERGE,
            ProcessorSegmentId::class.java,
            this::handleMerge
        )
        registrar.registerHandlerWithPayload(
            Routes.EventProcessor.RESET,
            ResetDecision::class.java,
            this::handleReset
        )
    }

    private fun handleStart(processorName: String) {
        logger.info("Handling Inspector Axon START command for processor [{}]", processorName)
        eventProcessorManager.start(processorName)
    }

    private fun handleStop(processorName: String) {
        logger.info("Handling Inspector Axon STOP command for processor [{}]", processorName)
        eventProcessorManager.stop(processorName)
    }

    private fun handleStatusQuery(): ProcessorStatusReport {
        logger.info("Handling Inspector Axon STATUS command")
        return processorReportCreator.createReport()
    }

    fun handleRelease(processorSegmentId: ProcessorSegmentId) {
        logger.info(
            "Handling Inspector Axon RELEASE command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        eventProcessorManager.releaseSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    private fun handleSplit(processorSegmentId: ProcessorSegmentId): Boolean {
        logger.info(
            "Handling Inspector Axon SPLIT command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        return eventProcessorManager
            .splitSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    private fun handleMerge(processorSegmentId: ProcessorSegmentId): Boolean {
        logger.info(
            "Handling Inspector Axon MERGE command for processor [{}] and segment [{}]",
            processorSegmentId.processorName,
            processorSegmentId.segmentId
        )
        return eventProcessorManager
            .mergeSegment(processorSegmentId.processorName, processorSegmentId.segmentId)
    }

    private fun handleReset(resetDecision: ResetDecision) {
        logger.info("Handling Inspector Axon RESET command for processor [{}]", resetDecision.processorName)
        eventProcessorManager.resetTokens(resetDecision)
    }
}
