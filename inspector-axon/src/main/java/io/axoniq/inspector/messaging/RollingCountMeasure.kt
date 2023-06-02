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

const val BUCKET_SIZE = 2500
const val BUCKET_COUNT = 60000 / BUCKET_SIZE

/**
 * Keeps a count values, returning the times the counter was incremented in the last minute.
 * This is done by keeping an integer in buckets of 2.5 seconds. When the value is queried,
 * all buckets of the last minute + 2.5 seconds are queried, excluding the last one to exclude incomplete buckets.
 *
 * Data is not cleaned automatically for performance reasons. It is only cleaned when the value is retrieved.
 */
class RollingCountMeasure {
    private val countMap = ConcurrentHashMap<Long, Int>()

    fun increment() {
        countMap.compute(currentBucket()) { _, v -> (v ?: 0) + 1 }
    }

    fun count(): Double {
        val bucket = currentBucket()
        val minimumBucket = (bucket - BUCKET_COUNT)
        val toRemove = countMap.keys.filter { it < minimumBucket }
        toRemove.forEach { countMap.remove(it) }

        return countMap.filter { it.key in minimumBucket until bucket }.values.sum().toDouble()
    }

    private fun currentBucket(): Long {
        return System.currentTimeMillis() / BUCKET_SIZE
    }
}
