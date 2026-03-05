package com.hrvib.app.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MetronomeEngineTest {
    @Test
    fun ticksSwitchesPhaseBasedOnInhaleExhaleBeats() = runTest {
        val engine = MetronomeEngine()
        val config = SessionConfig(metronomeBpm = 600.0, inhaleBeats = 1, exhaleBeats = 1, durationMinutes = 5)

        val ticks = engine.ticks(config).take(4).toList()
        val phases = ticks.map { it.phase }

        assertThat(phases).containsExactly(
            BreathPhase.Inhale,
            BreathPhase.Exhale,
            BreathPhase.Inhale,
            BreathPhase.Exhale
        ).inOrder()
    }
}
