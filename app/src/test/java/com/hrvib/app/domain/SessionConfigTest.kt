package com.hrvib.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SessionConfigTest {
    @Test
    fun breathsPerMinuteUsesBpmOverTotalBeatsWithOneDecimalTarget() {
        val config = SessionConfig(metronomeBpm = 55.0, inhaleBeats = 4, exhaleBeats = 6, durationMinutes = 5)
        assertThat(config.breathsPerMinute).isWithin(0.0001).of(5.5)
    }
}
