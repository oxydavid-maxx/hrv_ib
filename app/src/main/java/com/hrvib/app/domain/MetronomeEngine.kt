package com.hrvib.app.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class BreathPhase { Inhale, Exhale }

data class MetronomeTick(
    val phase: BreathPhase,
    val beatInPhase: Int,
    val timestampMs: Long
)

class MetronomeEngine {
    fun ticks(config: SessionConfig): Flow<MetronomeTick> = flow {
        val intervalMs = (60_000.0 / config.metronomeBpm).toLong().coerceAtLeast(100L)
        var phase = BreathPhase.Inhale
        var beat = 0
        while (true) {
            val maxBeat = if (phase == BreathPhase.Inhale) config.inhaleBeats else config.exhaleBeats
            emit(MetronomeTick(phase, beat, System.currentTimeMillis()))
            delay(intervalMs)
            beat++
            if (beat >= maxBeat) {
                phase = if (phase == BreathPhase.Inhale) BreathPhase.Exhale else BreathPhase.Inhale
                beat = 0
            }
        }
    }
}
