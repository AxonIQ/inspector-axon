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

import io.axoniq.inspector.api.ResetDecision
import io.axoniq.inspector.api.ResetDecisions
import org.axonframework.common.ReflectionUtils
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.EventProcessingConfiguration
import org.axonframework.eventhandling.StreamingEventProcessor
import org.axonframework.eventhandling.TrackingEventProcessor
import org.axonframework.eventhandling.pooled.PooledStreamingEventProcessor
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class EventProcessorManager(private val eventProcessingConfig: EventProcessingConfiguration,
    private val transactionManager: TransactionManager) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun start(processorName: String) {
        eventProcessor(processorName).start()
    }

    fun stop(processorName: String) {
        eventProcessor(processorName).shutDown()
    }

    fun releaseSegment(processorName: String, segmentId: Int) {
        eventProcessor(processorName).releaseSegment(segmentId)
    }

    fun claimSegment(processorName: String, segmentId: Int): Boolean {
        val processor = eventProcessor(processorName)
        if (processor is TrackingEventProcessor) {
            logger.info("You are load-balancing TrackingEventProcessor. This is very ineffective. Consider using PooledStreamingEventProcessor instead.")
        } else if (processor is PooledStreamingEventProcessor) {
            try {
                val coordinatorField = processor::class.java.declaredFields.firstOrNull { it.name == "coordinator" }
                    ?: throw IllegalStateException("Could not find Coordinator field!")
                val coordinator = ReflectionUtils.getFieldValue<Any>(coordinatorField, processor)
                val coordinationTaskField =
                    coordinator::class.java.declaredFields.firstOrNull { it.name == "coordinationTask" }
                        ?: throw IllegalStateException("Could not find CoordinationTask field!")
                val coordinationTaskAtomicReference = ReflectionUtils
                    .getFieldValue<AtomicReference<*>>(
                        coordinationTaskField,
                        coordinator
                    )
                val coordinationTask = coordinationTaskAtomicReference.get()
                val unclaimedSegmentValidationThresholdField =
                    coordinationTask::class.java.declaredFields.firstOrNull { it.name == "unclaimedSegmentValidationThreshold" }
                        ?: throw IllegalStateException("Could not find unclaimedSegmentValidationThreshold field!")
                ReflectionUtils.setFieldValue(unclaimedSegmentValidationThresholdField, coordinationTask, 0L)

                val taskMethod = coordinationTask::class.java.declaredMethods.firstOrNull { it.name == "scheduleImmediateCoordinationTask" }
                    ?: throw IllegalStateException("Could not find scheduleImmediateCoordinationTask method!")
                ReflectionUtils.ensureAccessible(taskMethod)
                taskMethod.invoke(coordinationTask)

            } catch (e: Exception) {
                logger.warn("Was unable to wait for segment CLAIM command due to internal error", e)
                return false
            }
        }

        // Wait until claimed
        var loop = 0
        while (loop < 300) {
            Thread.sleep(100)
            if (processor.processingStatus().containsKey(segmentId)) {
                logger.info("Processor [$processorName] successfully claimed segment [$segmentId] in approx. [${loop * 100}ms].")
                return true
            }
            loop++
        }

        logger.info("Processor [$processorName] failed to claim [$segmentId] in approx. [${loop * 100}ms].")


        return false
    }

    fun splitSegment(processorName: String, segmentId: Int) =
        eventProcessor(processorName)
            .splitSegment(segmentId)
            .get(5, TimeUnit.SECONDS)

    fun mergeSegment(processorName: String, segmentId: Int) =
        eventProcessor(processorName)
            .mergeSegment(segmentId)
            .get(5, TimeUnit.SECONDS)

    fun resetTokens(resetDecision: ResetDecision) =
        eventProcessor(resetDecision.processorName)
            .resetTokens { messageSource ->
                when (resetDecision.decision) {
                    ResetDecisions.HEAD -> messageSource.createHeadToken()
                    ResetDecisions.TAIL -> messageSource.createTailToken()
                    ResetDecisions.FROM -> messageSource.createTokenAt(resetDecision.from!!)
                }
            }


    private fun eventProcessor(processorName: String): StreamingEventProcessor =
        eventProcessingConfig.eventProcessor(processorName, StreamingEventProcessor::class.java)
            .orElseThrow { IllegalArgumentException("Event Processor [$processorName] not found!") }
}
