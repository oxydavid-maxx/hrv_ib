package com.hrvib.app.domain

import com.google.common.truth.Truth.assertThat
import kotlin.math.sqrt
import org.junit.Test

class HrvMathTest {
    @Test
    fun rmssdMatchesManualComputation() {
        val rr = listOf(1000.0, 1100.0, 1000.0, 900.0)
        val expected = sqrt(((100.0 * 100.0) + (100.0 * 100.0) + (100.0 * 100.0)) / 3.0)
        assertThat(HrvMath.rmssd(rr)).isWithin(1e-6).of(expected)
    }

    @Test
    fun rollingWindowUsesLast3Seconds() {
        val rr = listOf(
            HrvMath.TimedRr(1000, 900.0),
            HrvMath.TimedRr(2000, 910.0),
            HrvMath.TimedRr(3500, 920.0),
            HrvMath.TimedRr(4100, 930.0)
        )
        val value = HrvMath.rolling3SecRmssd(rr, 4200, 3000)
        assertThat(value).isNotNull()
    }

    @Test
    fun artifactRejectionDropsOutOfRangeValues() {
        val rr = listOf(
            HrvMath.TimedRr(1, 800.0),
            HrvMath.TimedRr(2, 250.0),
            HrvMath.TimedRr(3, 1200.0),
            HrvMath.TimedRr(4, 810.0),
            HrvMath.TimedRr(5, 2200.0)
        )
        val cleaned = HrvMath.rejectArtifacts(rr)
        assertThat(cleaned.map { it.rrMs }).containsNoneOf(250.0, 2200.0)
    }

    @Test
    fun artifactRejectionDropsSuddenJumpWithoutStableNeighbors() {
        val rr = listOf(
            HrvMath.TimedRr(1, 800.0),
            HrvMath.TimedRr(2, 1100.0),
            HrvMath.TimedRr(3, 900.0),
            HrvMath.TimedRr(4, 760.0)
        )
        val cleaned = HrvMath.rejectArtifacts(rr)
        assertThat(cleaned.map { it.rrMs }).doesNotContain(1100.0)
    }

    @Test
    fun madFilterRemovesExtremeOutliers() {
        val values = listOf(20.0, 22.0, 21.0, 19.0, 23.0, 200.0)
        val cleaned = HrvMath.madFilter(values)
        assertThat(cleaned).doesNotContain(200.0)
    }

    @Test
    fun emaKeepsRawAccessible() {
        val first = HrvMath.ema(null, 40.0, 0.2)
        val second = HrvMath.ema(first, 60.0, 0.2)
        assertThat(first).isEqualTo(40.0)
        assertThat(second).isWithin(0.001).of(44.0)
    }
}
