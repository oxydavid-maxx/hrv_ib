package com.hrvib.app.data

import com.hrvib.app.data.db.EpochDao
import com.hrvib.app.data.db.EpochStatEntity
import com.hrvib.app.data.db.RrDao
import com.hrvib.app.data.db.RrSampleEntity
import com.hrvib.app.data.db.SessionDao
import com.hrvib.app.data.db.SessionEntity
import com.hrvib.app.data.db.TimeSeriesPoint
import com.hrvib.app.domain.HrvMath
import com.hrvib.app.domain.RangeFilter
import com.hrvib.app.domain.SessionConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.round

class SessionRepository(
    private val sessionDao: SessionDao,
    private val rrDao: RrDao,
    private val epochDao: EpochDao
) {
    suspend fun createSession(config: SessionConfig, startTime: Long): Long {
        return sessionDao.insert(
            SessionEntity(
                startTime = startTime,
                durationMin = config.durationMinutes,
                metronomeBpm = config.metronomeBpm,
                inhaleBeats = config.inhaleBeats,
                exhaleBeats = config.exhaleBeats,
                breathsPerMin = round(config.breathsPerMinute * 10.0) / 10.0,
                avgHRV = null,
                peakHRV = null,
                avgHR = null
            )
        )
    }

    suspend fun appendRr(sessionId: Long, timestampMs: Long, rrMs: Double, hrBpm: Int?) {
        rrDao.insertAll(listOf(RrSampleEntity(sessionId = sessionId, timestampMs = timestampMs, rrMs = rrMs, hrBpm = hrBpm)))
    }

    suspend fun appendEpoch(sessionId: Long, timestampMs: Long, rollingHrv: Double?, hrBpm: Int?) {
        epochDao.insertAll(listOf(EpochStatEntity(sessionId = sessionId, timestampMs = timestampMs, rollingHrvRmssd = rollingHrv, hrBpm = hrBpm)))
    }

    suspend fun completeSession(sessionId: Long, endMs: Long) {
        val session = sessionDao.getById(sessionId) ?: return
        val rr = rrDao.bySession(sessionId).map { HrvMath.TimedRr(it.timestampMs, it.rrMs) }
        val cleanedStart = session.startTime + 60_000L
        val rrAfterWarmup = rr.filter { it.timestampMs >= cleanedStart }
        val artifactCleaned = HrvMath.rejectArtifacts(rrAfterWarmup)

        val epochPoints = buildEpochRmssdSeries(artifactCleaned, cleanedStart, endMs)
        val noOutliers = HrvMath.madFilter(epochPoints)
        val avgHrv = noOutliers.takeIf { it.isNotEmpty() }?.average()
        val peakHrv = noOutliers.maxOrNull()
        val avgHr = HrvMath.averageHrFromRr(artifactCleaned)

        sessionDao.update(
            session.copy(
                avgHRV = avgHrv,
                peakHRV = peakHrv,
                avgHR = avgHr
            )
        )
    }

    private fun buildEpochRmssdSeries(
        rr: List<HrvMath.TimedRr>,
        startMs: Long,
        endMs: Long
    ): List<Double> {
        if (rr.size < 3) return emptyList()
        val output = mutableListOf<Double>()
        var t = startMs
        while (t <= endMs) {
            HrvMath.rolling3SecRmssd(rr, t)?.let { output += it }
            t += 1000L
        }
        return output
    }

    fun observeSessions(rangeFilter: RangeFilter, showExcluded: Boolean): Flow<List<SessionEntity>> {
        val from = System.currentTimeMillis() - rangeFilter.days * 24L * 60L * 60L * 1000L
        return sessionDao.observeSessions(from, showExcluded)
    }

    fun observeScatter(rangeFilter: RangeFilter, showExcluded: Boolean): Flow<List<Pair<Long, Pair<Double, Double>>>> {
        return observeSessions(rangeFilter, showExcluded).map { sessions ->
            sessions.mapNotNull { s ->
                val y = s.avgHRV ?: return@mapNotNull null
                s.id to (s.breathsPerMin to y)
            }
        }
    }

    fun observeTimeSeries(rangeFilter: RangeFilter, showExcluded: Boolean): Flow<List<TimeSeriesPoint>> {
        return observeSessions(rangeFilter, showExcluded).map { sessions ->
            val bucketMs = when (rangeFilter) {
                RangeFilter.Day -> 60L * 60L * 1000L
                RangeFilter.Week -> 24L * 60L * 60L * 1000L
                RangeFilter.Month -> 24L * 60L * 60L * 1000L
                RangeFilter.Year -> 7L * 24L * 60L * 60L * 1000L
            }
            sessions.groupBy { it.startTime / bucketMs }.mapNotNull { (key, values) ->
                val avg = values.mapNotNull { it.avgHRV }
                val peak = values.mapNotNull { it.peakHRV }
                if (avg.isEmpty() || peak.isEmpty()) null
                else TimeSeriesPoint(key * bucketMs, avg.average(), peak.average())
            }.sortedBy { it.bucket }
        }
    }

    suspend fun setDeleted(sessionId: Long, deleted: Boolean) {
        sessionDao.setDeleted(sessionId, deleted)
    }

    suspend fun getSession(sessionId: Long): SessionEntity? = sessionDao.getById(sessionId)
    suspend fun getRrBySession(sessionId: Long) = rrDao.bySession(sessionId)

    suspend fun seedDemoHistoryIfEmpty(nowMs: Long = System.currentTimeMillis()) {
        val from = nowMs - 365L * 24L * 60L * 60L * 1000L
        val existing = sessionDao.observeSessions(from, true).first()
        if (existing.isNotEmpty()) return
        val daysAgo = listOf(0, 1, 3, 6, 12, 24, 45, 90, 180, 320)
        daysAgo.forEachIndexed { idx, day ->
            sessionDao.insert(
                SessionEntity(
                    startTime = nowMs - day * 24L * 60L * 60L * 1000L,
                    durationMin = 5,
                    metronomeBpm = 55.0,
                    inhaleBeats = 4,
                    exhaleBeats = 6,
                    breathsPerMin = 5.4 + (idx % 4) * 0.2,
                    avgHRV = 24.0 + idx * 2.2,
                    peakHRV = 32.0 + idx * 2.8,
                    avgHR = 70.0 - idx * 0.5
                )
            )
        }
    }
}
