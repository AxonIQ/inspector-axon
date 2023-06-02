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

package io.axoniq.inspector.messaging

import io.axoniq.inspector.api.metrics.HandlerStatisticsMetricIdentifier
import io.axoniq.inspector.api.metrics.HandlerType
import io.axoniq.inspector.api.metrics.MessageIdentifier
import io.axoniq.inspector.api.metrics.StatisticDistribution
import io.micrometer.core.instrument.distribution.HistogramSnapshot
import io.micrometer.core.instrument.distribution.ValueAtPercentile
import org.axonframework.commandhandling.CommandMessage
import org.axonframework.deadline.DeadlineMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.messaging.Message
import org.axonframework.messaging.unitofwork.UnitOfWork
import org.axonframework.modelling.command.AggregateLifecycle
import org.axonframework.modelling.command.AggregateScopeDescriptor
import org.axonframework.queryhandling.QueryMessage
import org.axonframework.queryhandling.SubscriptionQueryUpdateMessage
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger("inspector-extensions")

fun Message<*>.toInformation() = MessageIdentifier(
    when (this) {
        is DeadlineMessage<*> -> DeadlineMessage::class.java.simpleName
        is CommandMessage -> CommandMessage::class.java.simpleName
        is EventMessage -> EventMessage::class.java.simpleName
        is QueryMessage<*, *> -> QueryMessage::class.java.simpleName
        is SubscriptionQueryUpdateMessage<*> -> SubscriptionQueryUpdateMessage::class.java.simpleName
        else -> this::class.java.simpleName
    },
    when (this) {
        is CommandMessage -> this.commandName.toSimpleName()
        is QueryMessage<*, *> -> this.queryName.toSimpleName()
        is DeadlineMessage<*> -> this.deadlineName.toSimpleName()
        else -> this.payloadType.name.toSimpleName()
    }
)

fun String.toSimpleName() = split(".").last()

fun UnitOfWork<*>.extractHandler(): HandlerStatisticsMetricIdentifier? = try {
    val processingGroup = resources()[INSPECTOR_PROCESSING_GROUP] as? String?
    val isAggregate = message is CommandMessage<*> && isAggregateLifecycleActive()
    val isProcessor = processingGroup != null

    val component = when {
        isAggregate -> (AggregateLifecycle.describeCurrentScope() as AggregateScopeDescriptor).type
        isProcessor -> processingGroup
        else -> resources()[INSPECTOR_DECLARING_CLASS] as String?
    }
    val type = when {
        isAggregate -> HandlerType.Aggregate
        isProcessor -> HandlerType.EventProcessor
        else -> HandlerType.Message
    }
    HandlerStatisticsMetricIdentifier(
        type = type,
        component = component,
        message = message.toInformation(),
    )
} catch (e: Exception) {
    logger.warn("Could not extract handler from Inspector invocation. Skipping registration of message.", e)
    null
}


fun isAggregateLifecycleActive(): Boolean {
    return try {
        val scope = AggregateLifecycle.describeCurrentScope()
        scope is AggregateScopeDescriptor
    } catch (e: Exception) {
        false
    }
}


fun HistogramSnapshot.toDistribution(): StatisticDistribution {
    val percentiles = percentileValues()
    return StatisticDistribution(
        min = percentiles.ofPercentile(0.01),
        percentile90 = percentiles.ofPercentile(0.90),
        percentile95 = percentiles.ofPercentile(0.95),
        median = percentiles.ofPercentile(0.50),
        mean = mean(TimeUnit.MILLISECONDS),
        max = percentiles.ofPercentile(1.00),
    )
}

fun Array<ValueAtPercentile>.ofPercentile(percentile: Double): Double {
    return this.firstOrNull { pc -> pc.percentile() == percentile }
        ?.value(TimeUnit.MILLISECONDS)!!
}
