package com.gangchat.client.app

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class NetworkLatencyQuality { unavailable, poor, fair, good }

val lowNetworkLatencyThreshold: Duration = 150.milliseconds
val fairNetworkLatencyThreshold: Duration = 350.milliseconds

fun networkLatencyQuality(latency: Duration?): NetworkLatencyQuality {
    if (latency == null) return NetworkLatencyQuality.unavailable
    if (latency <= lowNetworkLatencyThreshold) return NetworkLatencyQuality.good
    if (latency <= fairNetworkLatencyThreshold) return NetworkLatencyQuality.fair
    return NetworkLatencyQuality.poor
}

fun networkLatencySignalBars(latency: Duration?): Int {
    return when (networkLatencyQuality(latency)) {
        NetworkLatencyQuality.good -> 3
        NetworkLatencyQuality.fair -> 2
        NetworkLatencyQuality.poor -> 1
        NetworkLatencyQuality.unavailable -> 0
    }
}

fun networkLatencyTooltip(latency: Duration?): String {
    if (latency == null) return "延迟检测中"
    return "${latency.inWholeMilliseconds} ms"
}
