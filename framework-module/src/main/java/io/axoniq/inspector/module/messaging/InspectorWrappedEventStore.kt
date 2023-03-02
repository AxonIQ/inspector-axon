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

import io.axoniq.inspector.api.metrics.PreconfiguredMetric
import io.axoniq.inspector.module.messaging.InspectorSpanFactory.Companion.onTopLevelSpanIfActive
import org.axonframework.common.Registration
import org.axonframework.common.stream.BlockingStream
import org.axonframework.eventhandling.DomainEventMessage
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.TrackedEventMessage
import org.axonframework.eventhandling.TrackingToken
import org.axonframework.eventsourcing.eventstore.DomainEventStream
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.messaging.MessageDispatchInterceptor
import java.util.function.Consumer
import java.util.stream.Collectors

class InspectorWrappedEventStore(
    private val delegate: EventStore
) : EventStore {

    override fun storeSnapshot(p0: DomainEventMessage<*>) {
        delegate.storeSnapshot(p0)
    }

    override fun subscribe(messageProcessor: Consumer<MutableList<out EventMessage<*>>>): Registration {
        return delegate.subscribe(messageProcessor)
    }

    override fun registerDispatchInterceptor(dispatchInterceptor: MessageDispatchInterceptor<in EventMessage<*>>): Registration {
        return delegate.registerDispatchInterceptor(dispatchInterceptor)
    }

    override fun publish(events: MutableList<out EventMessage<*>>) {
        return delegate.publish(events)
    }

    override fun openStream(trackingToken: TrackingToken?): BlockingStream<TrackedEventMessage<*>> {
        return delegate.openStream(trackingToken)
    }

    override fun readEvents(p0: String): DomainEventStream {
        val result = delegate.readEvents(p0)
        val events = result.asStream().map { it }.collect(Collectors.toList())
        onTopLevelSpanIfActive {
            it.registerMetricValue(PreconfiguredMetric.AGGREGATE_EVENTS_SIZE, events.size.toLong())
        }
        return DomainEventStream.of(events)
    }
}
