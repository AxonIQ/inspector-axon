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

import java.util.concurrent.TimeUnit

/**
 * Due to limitations of micrometer, we cannot make a distribution of a simple counter/gauge. We need a timer for
 * that. Since we want to measure stuff like p90 interval of events read for an aggregate, we may need to convert this.
 */
enum class MetricType(val metricPrefix: String, val distributionUnit: TimeUnit) {
    TIMER("tmr", TimeUnit.NANOSECONDS),
    COUNTER("cntr", TimeUnit.MILLISECONDS),
}
