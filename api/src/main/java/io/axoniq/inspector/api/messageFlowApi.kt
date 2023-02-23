package io.axoniq.inspector.api

enum class HandlerType {
    EventProcessor,
    Aggregate,
    Message,
}

data class HandlerInformation(
    val type: HandlerType,
    val component: String?,
    val message: MessageInformation
)

data class DispatcherInformation(
    val handlerInformation: HandlerInformation?,
    val message: MessageInformation,
)

data class MessageInformation(
    val type: String,
    val name: String,
)

data class ClientMessageStatisticsReport(
    val handlers: List<HandlerStatistics>,
    val dispatchers: List<DispatcherStatistics>,
)

data class HandlerStatistics(
    val handler: HandlerInformation,
    val statistics: MessageHandlerBucketStatistics,
)

data class MessageHandlerBucketStatistics(
    val countPerMinute: Double,
    val failedCountPerMinute: Double,
    val totalDistribution: DistributionStatistics?,
    val handlerDistribution: DistributionStatistics?,
    val additionalDistributions: Map<String, DistributionStatistics>,
)

data class DistributionStatistics(
    val min: Double,
    val median: Double,
    val mean: Double,
    val percentile95: Double,
    val percentile90: Double,
    val max: Double,
)

data class DispatcherStatistics(
    val handler: DispatcherInformation,
    val countPerMinute: Double,
)
