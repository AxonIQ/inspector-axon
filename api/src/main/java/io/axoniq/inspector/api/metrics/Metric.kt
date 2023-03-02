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

package io.axoniq.inspector.api.metrics

import io.axoniq.inspector.api.metrics.MetricTargetType.*


interface Metric {
    val type: MetricType
    val identifier: String
    val description: String
    val breakDownMetrics: List<Metric>
    val targetTypes: List<MetricTargetType>


    val fullIdentifier: String
        get() = "${type.metricPrefix}_${identifier}"
}

data class UserHandlerInterceptorMetric(
    override val type: MetricType = MetricType.TIMER,
    override val identifier: String,
    override val description: String = "User defined metric",
    override val breakDownMetrics: List<Metric> = emptyList(),
    override val targetTypes: List<MetricTargetType> = listOf(HANDLER, AGGREGATE),
) : Metric

enum class PreconfiguredMetric(
    override val type: MetricType,
    override val identifier: String,
    override val description: String,
    override val breakDownMetrics: List<Metric> = listOf(),
    override val targetTypes: List<MetricTargetType>,
) : Metric {
    MESSAGE_HANDLER_TIME(
        type = MetricType.TIMER,
        identifier = "handler",
        description = "Time it took for the actual user-made handler function to be invoked",
        targetTypes = listOf(AGGREGATE, HANDLER),
    ),
    AGGREGATE_LOCK_TIME(
        type = MetricType.TIMER,
        identifier = "aggregate_lock",
        description = "Time it took for a command to acquire a lock for the aggregate identifier",
        targetTypes = listOf(AGGREGATE, HANDLER),
    ),
    AGGREGATE_LOAD_TIME(
        type = MetricType.TIMER,
        identifier = "aggregate_load",
        description = "Time it took for a command to load the target aggregate",
        targetTypes = listOf(AGGREGATE, HANDLER),
        breakDownMetrics = listOf(AGGREGATE_LOCK_TIME)
    ),
    EVENT_COMMIT_TIME(
        type = MetricType.TIMER,
        identifier = "event_commit",
        description = "Time it took to commit events to the event store",
        targetTypes = listOf(AGGREGATE, HANDLER)
    ),
    AGGREGATE_EVENTS_SIZE(
        type = MetricType.COUNTER,
        identifier = "agg_events_size",
        description = "Amount of events loaded during aggregate initialization",
        targetTypes = listOf(AGGREGATE)
    )

    ;
}
