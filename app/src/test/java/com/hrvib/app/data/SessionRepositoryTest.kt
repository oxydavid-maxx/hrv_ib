package com.hrvib.app.data

import com.google.common.truth.Truth.assertThat
import com.hrvib.app.data.db.EpochDao
import com.hrvib.app.data.db.EpochStatEntity
import com.hrvib.app.data.db.RrDao
import com.hrvib.app.data.db.RrSampleEntity
import com.hrvib.app.data.db.SessionDao
import com.hrvib.app.data.db.SessionEntity
import com.hrvib.app.domain.HrvMath
import com.hrvib.app.domain.RangeFilter
import com.hrvib.app.domain.SessionConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepositoryTest {
    private lateinit var sessionDao: FakeSessionDao
    private lateinit var rrDao: FakeRrDao
    private lateinit var epochDao: FakeEpochDao
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        sessionDao = FakeSessionDao()
        rrDao = FakeRrDao()
        epochDao = FakeEpochDao()
        repository = SessionRepository(sessionDao, rrDao, epochDao)
    }

    @Test
    fun createAppendAndReadBackPersistsSessionRrAndEpoch() = runTest {
        val start = 1_000L
        val id = repository.createSession(
            SessionConfig(metronomeBpm = 55.0, inhaleBeats = 4, exhaleBeats = 6, durationMinutes = 7),
            start
        )
        repository.appendRr(id, start + 1000, 850.0, 72)
        repository.appendEpoch(id, start + 2000, 34.2, 72)

        val session = repository.getSession(id)
        val rr = repository.getRrBySession(id)
        val epoch = epochDao.bySession(id)

        assertThat(session).isNotNull()
        assertThat(session!!.durationMin).isEqualTo(7)
        assertThat(session.breathsPerMin).isWithin(0.001).of(5.5)
        assertThat(rr).hasSize(1)
        assertThat(rr.first().rrMs).isEqualTo(850.0)
        assertThat(epoch).hasSize(1)
        assertThat(epoch.first().rollingHrvRmssd).isEqualTo(34.2)
    }

    @Test
    fun completeSessionExcludesFirstMinuteAndAppliesArtifactAndMadRules() = runTest {
        val start = 10_000L
        val sessionId = repository.createSession(SessionConfig(durationMinutes = 3), start)

        // Warmup data that should be excluded from summary.
        for (i in 0 until 10) {
            repository.appendRr(sessionId, start + i * 1000L, 1200.0, 50)
        }

        val postWarmup = listOf(800.0, 810.0, 790.0, 805.0, 120.0, 810.0, 800.0, 790.0, 780.0, 2005.0, 770.0)
        postWarmup.forEachIndexed { idx, rr ->
            repository.appendRr(sessionId, start + 60_000L + idx * 1000L, rr, 70)
        }
        val end = start + 90_000L
        repository.completeSession(sessionId, end)

        val session = repository.getSession(sessionId)!!
        val all = repository.getRrBySession(sessionId).map { HrvMath.TimedRr(it.timestampMs, it.rrMs) }
        val rrAfterWarmup = all.filter { it.timestampMs >= start + 60_000L }
        val artifactCleaned = HrvMath.rejectArtifacts(rrAfterWarmup)
        val epochSeries = buildList {
            var t = start + 60_000L
            while (t <= end) {
                HrvMath.rolling3SecRmssd(artifactCleaned, t)?.let { add(it) }
                t += 1000L
            }
        }
        val cleanedEpoch = HrvMath.madFilter(epochSeries)
        val expectedAvg = cleanedEpoch.average()
        val expectedPeak = cleanedEpoch.maxOrNull()
        val expectedAvgHr = HrvMath.averageHrFromRr(artifactCleaned)

        assertThat(session.avgHRV).isWithin(0.001).of(expectedAvg)
        assertThat(session.peakHRV).isWithin(0.001).of(expectedPeak!!)
        assertThat(session.avgHR).isWithin(0.001).of(expectedAvgHr!!)
        assertThat(artifactCleaned.map { it.rrMs }).containsNoneOf(120.0, 2005.0)
        assertThat(session.avgHR).isGreaterThan(70.0) // warmup 1200ms@50 bpm excluded
    }

    @Test
    fun scatterAndRangeFiltersReturnExpectedCounts() = runTest {
        val now = System.currentTimeMillis()
        insertSession(now - 2 * 60 * 60 * 1000L, avg = 30.0, peak = 40.0, breaths = 5.5) // day
        insertSession(now - 3 * 24 * 60 * 60 * 1000L, avg = 31.0, peak = 41.0, breaths = 5.8) // week
        insertSession(now - 20 * 24 * 60 * 60 * 1000L, avg = 32.0, peak = 42.0, breaths = 6.0) // month
        insertSession(now - 200 * 24 * 60 * 60 * 1000L, avg = 33.0, peak = 43.0, breaths = 6.2) // year

        assertThat(repository.observeScatter(RangeFilter.Day, false).first()).hasSize(1)
        assertThat(repository.observeScatter(RangeFilter.Week, false).first()).hasSize(2)
        assertThat(repository.observeScatter(RangeFilter.Month, false).first()).hasSize(3)
        assertThat(repository.observeScatter(RangeFilter.Year, false).first()).hasSize(4)

        val firstScatter = repository.observeScatter(RangeFilter.Day, false).first().first().second
        assertThat(firstScatter.first).isWithin(0.001).of(5.5)
        assertThat(firstScatter.second).isWithin(0.001).of(30.0)
    }

    @Test
    fun softDeleteExcludedByDefaultAndShownWhenRequested() = runTest {
        val now = System.currentTimeMillis()
        val keepId = insertSession(now - 1000L, avg = 25.0, peak = 35.0, breaths = 5.4)
        val deleteId = insertSession(now - 2000L, avg = 28.0, peak = 38.0, breaths = 5.6)

        repository.setDeleted(deleteId, true)

        val hidden = repository.observeSessions(RangeFilter.Year, false).first()
        val shown = repository.observeSessions(RangeFilter.Year, true).first()

        assertThat(hidden.map { it.id }).containsExactly(keepId)
        assertThat(shown.map { it.id }).containsExactly(deleteId, keepId)

        repository.setDeleted(deleteId, false)
        val restored = repository.observeSessions(RangeFilter.Year, false).first()
        assertThat(restored.map { it.id }).containsExactly(deleteId, keepId)
    }

    @Test
    fun timeSeriesRangeAndBucketingBehaveAsDefined() = runTest {
        val now = System.currentTimeMillis()
        insertSession(now - 2 * 60 * 60 * 1000L, avg = 30.0, peak = 40.0, breaths = 5.5)
        insertSession(now - 23 * 60 * 60 * 1000L, avg = 32.0, peak = 44.0, breaths = 5.5)
        insertSession(now - 3 * 24 * 60 * 60 * 1000L, avg = 34.0, peak = 48.0, breaths = 5.5)
        insertSession(now - 10 * 24 * 60 * 60 * 1000L, avg = 36.0, peak = 50.0, breaths = 5.5)

        val day = repository.observeTimeSeries(RangeFilter.Day, false).first()
        val week = repository.observeTimeSeries(RangeFilter.Week, false).first()
        val month = repository.observeTimeSeries(RangeFilter.Month, false).first()
        val year = repository.observeTimeSeries(RangeFilter.Year, false).first()

        assertThat(day).isNotEmpty()
        assertThat(week.size).isAtMost(month.size) // week/month both daily buckets
        assertThat(year.size).isAtMost(month.size) // year uses coarser weekly bucket
        assertThat(day.first().avgHRV).isGreaterThan(0.0)
        assertThat(day.first().peakHRV).isGreaterThan(0.0)
    }

    private suspend fun insertSession(
        startTime: Long,
        avg: Double,
        peak: Double,
        breaths: Double,
        deleted: Boolean = false
    ): Long {
        return sessionDao.insert(
            SessionEntity(
                startTime = startTime,
                durationMin = 5,
                metronomeBpm = 55.0,
                inhaleBeats = 4,
                exhaleBeats = 6,
                breathsPerMin = breaths,
                avgHRV = avg,
                peakHRV = peak,
                avgHR = 70.0,
                isDeleted = deleted
            )
        )
    }

    private class FakeSessionDao : SessionDao {
        private val sessions = mutableListOf<SessionEntity>()
        private val sessionFlow = MutableStateFlow<List<SessionEntity>>(emptyList())
        private var nextId = 1L

        override suspend fun insert(session: SessionEntity): Long {
            val stored = session.copy(id = nextId++)
            sessions += stored
            emit()
            return stored.id
        }

        override suspend fun update(session: SessionEntity) {
            val idx = sessions.indexOfFirst { it.id == session.id }
            if (idx >= 0) {
                sessions[idx] = session
                emit()
            }
        }

        override suspend fun getById(id: Long): SessionEntity? = sessions.find { it.id == id }

        override fun observeSessions(fromTime: Long, showExcluded: Boolean) = sessionFlow.map { all ->
            all.filter { it.startTime >= fromTime && (showExcluded || !it.isDeleted) }
                .sortedBy { it.startTime }
        }

        override suspend fun setDeleted(id: Long, deleted: Boolean) {
            val idx = sessions.indexOfFirst { it.id == id }
            if (idx >= 0) {
                sessions[idx] = sessions[idx].copy(isDeleted = deleted)
                emit()
            }
        }

        private fun emit() {
            sessionFlow.value = sessions.toList()
        }
    }

    private class FakeRrDao : RrDao {
        private val rr = mutableListOf<RrSampleEntity>()
        private var nextId = 1L

        override suspend fun insertAll(items: List<RrSampleEntity>) {
            items.forEach { rr += it.copy(id = nextId++) }
        }

        override suspend fun bySession(sessionId: Long): List<RrSampleEntity> {
            return rr.filter { it.sessionId == sessionId }.sortedBy { it.timestampMs }
        }
    }

    private class FakeEpochDao : EpochDao {
        private val epochs = mutableListOf<EpochStatEntity>()
        private var nextId = 1L

        override suspend fun insertAll(items: List<EpochStatEntity>) {
            items.forEach { epochs += it.copy(id = nextId++) }
        }

        override suspend fun bySession(sessionId: Long): List<EpochStatEntity> {
            return epochs.filter { it.sessionId == sessionId }.sortedBy { it.timestampMs }
        }
    }
}
