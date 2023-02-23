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

package io.axoniq.inspector.module.messaging

import java.util.concurrent.atomic.AtomicLong

/**
 * Rolls up for every report, which is sent every ten seconds.
 * Since it keeps 6 values, we get the amount of the last minute, updating every 10 seconds
 */
class RollingCountMeasure {
    private var count1 = AtomicLong(0)
    private var count2 = AtomicLong(0)
    private var count3 = AtomicLong(0)
    private var count4 = AtomicLong(0)
    private var count5 = AtomicLong(0)
    private var count6 = AtomicLong(0)

    fun increment() {
        count1.incrementAndGet()
    }

    fun incrementWindow() {
        count6 = count5
        count5 = count4
        count4 = count3
        count3 = count2
        count2 = count1
        count1.set(0)
    }

    fun value(): Double {
        val total = count1.get() + count2.get() + count3.get() + count4.get() + count5.get() + count6.get()
        return total.toDouble()
    }
}
