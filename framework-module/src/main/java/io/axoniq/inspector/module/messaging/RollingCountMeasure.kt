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
