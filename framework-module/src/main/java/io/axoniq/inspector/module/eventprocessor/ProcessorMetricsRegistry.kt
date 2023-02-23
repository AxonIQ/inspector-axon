package io.axoniq.inspector.module.eventprocessor

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ProcessorMetricsRegistry(
) {
    private val ingestLatencyRegistry: MutableMap<String, ExpiringLatencyValue> = ConcurrentHashMap()
    private val commitLatencyRegistry: MutableMap<String, ExpiringLatencyValue> = ConcurrentHashMap()


    fun registerIngested(processor: String, latencyInNanos: Long) {
        ingestLatencyForProcessor(processor).setValue(latencyInNanos.toDouble() / 1000000)
    }

    fun registerCommitted(processor: String, latencyInNanos: Long) {
        commitLatencyForProcessor(processor).setValue(latencyInNanos.toDouble() / 1000000)
    }

    fun ingestLatencyForProcessor(processor: String) =
        ingestLatencyRegistry.computeIfAbsent(processor) { ExpiringLatencyValue() }

    fun commitLatencyForProcessor(processor: String) =
        commitLatencyRegistry.computeIfAbsent(processor) { ExpiringLatencyValue() }

    class ExpiringLatencyValue(
        private val expiryTime: Long = 30000 // Default to 30 seconds
    ) {
        private val clock = Clock.systemUTC()
        private val value: AtomicReference<Double> = AtomicReference(-1.0)
        private val timeSet: AtomicLong = AtomicLong(-1)

        fun setValue(newValue: Double) {
            value.set(newValue)
            timeSet.set(clock.millis())
        }

        fun getValue(): Double {
            if (value.get() != null && clock.millis() - timeSet.get() < expiryTime) {
                return value.get()
            }
            return -1.0
        }

    }
}
