package com.hrvib.app.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

object HrvMath {
    data class TimedRr(val timestampMs: Long, val rrMs: Double)

    fun rmssd(rr: List<Double>): Double? {
        if (rr.size < 3) return null
        val diffs = rr.zipWithNext { a, b -> b - a }
        val meanSq = diffs.map { it.pow(2.0) }.average()
        return sqrt(meanSq)
    }

    fun rolling3SecRmssd(
        samples: List<TimedRr>,
        nowMs: Long,
        windowMs: Long = 3000L
    ): Double? {
        val window = samples.filter { it.timestampMs in (nowMs - windowMs)..nowMs }.map { it.rrMs }
        return rmssd(window)
    }

    fun ema(previous: Double?, current: Double, alpha: Double = 0.2): Double {
        val prev = previous ?: current
        return (alpha * current) + ((1 - alpha) * prev)
    }

    /**
     * Rule:
     * 1) Keep physiological RR bounds [300, 2000] ms.
     * 2) Remove >20% sudden changes unless both neighbors are stable around current point.
     */
    fun rejectArtifacts(rr: List<TimedRr>): List<TimedRr> {
        if (rr.isEmpty()) return emptyList()
        val bounded = rr.filter { it.rrMs in 300.0..2000.0 }
        if (bounded.size < 3) return bounded
        val kept = mutableListOf<TimedRr>()
        bounded.forEachIndexed { idx, cur ->
            if (idx == 0 || idx == bounded.lastIndex) {
                kept += cur
            } else {
                val prev = bounded[idx - 1].rrMs
                val next = bounded[idx + 1].rrMs
                val change = abs(cur.rrMs - prev) / max(prev, 1.0)
                val stableNeighborhood = abs(prev - next) / max(prev, 1.0) <= 0.1
                if (change <= 0.2 || stableNeighborhood) kept += cur
            }
        }
        return kept
    }

    fun madFilter(values: List<Double>): List<Double> {
        if (values.size < 4) return values
        val median = values.sorted().let { s ->
            if (s.size % 2 == 0) (s[s.size / 2 - 1] + s[s.size / 2]) / 2 else s[s.size / 2]
        }
        val absDev = values.map { abs(it - median) }
        val mad = absDev.sorted().let { s ->
            if (s.size % 2 == 0) (s[s.size / 2 - 1] + s[s.size / 2]) / 2 else s[s.size / 2]
        }
        if (mad == 0.0) return values
        val scaledMad = mad * 1.4826
        return values.filter { abs(it - median) <= 3.5 * scaledMad }
    }

    fun averageHrFromRr(rr: List<TimedRr>): Double? {
        if (rr.isEmpty()) return null
        return rr.map { 60000.0 / it.rrMs }.average()
    }
}
