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

import java.util.concurrent.ConcurrentHashMap

/**
 * Rolls up for every report, which is sent every ten seconds.
 * Since it keeps 6 values, we get the amount of the last minute, updating every 10 seconds
 */
class RollingCountMeasure {
    private val countMap = ConcurrentHashMap<Long, Int>()

    fun increment() {
        countMap.compute(currentBucket()) { _, v -> (v ?: 0) + 1 }
    }

    fun pruneData() {
        val bucket = currentBucket()
        val toRemove = countMap.keys.filter { it < (bucket - 60000) }
        toRemove.forEach { countMap.remove(it) }
    }

    fun count(): Double {
        val bucket = currentBucket()
        val minimumBucket = bucket - 60000
        return countMap.filter { it.key in minimumBucket until bucket }.values.sum().toDouble()
    }

    private fun currentBucket(): Long {
        val time = System.currentTimeMillis()
        return time - time.mod(2500)
    }
}
