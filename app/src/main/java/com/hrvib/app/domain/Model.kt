package com.hrvib.app.domain

data class BleDevice(
    val id: String,
    val name: String,
    val rssi: Int
)

enum class ConnectionState {
    Disconnected,
    Scanning,
    Connecting,
    Connected
}

data class HrRrSample(
    val timestampMs: Long,
    val hrBpm: Int?,
    val rrIntervalsMs: List<Double>
)

data class SessionConfig(
    val metronomeBpm: Double = 55.0,
    val inhaleBeats: Int = 4,
    val exhaleBeats: Int = 6,
    val durationMinutes: Int = 5
) {
    val breathsPerMinute: Double
        get() = (metronomeBpm / (inhaleBeats + exhaleBeats)).coerceAtLeast(0.1)
}

enum class RangeFilter(val days: Int) {
    Day(1), Week(7), Month(30), Year(365)
}
