package com.hrvib.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.hrvib.app.data.db.AppDatabase
import com.hrvib.app.domain.SessionConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: SessionRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = SessionRepository(db.sessionDao(), db.rrDao(), db.epochDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun writeReadAndSoftDeleteRoundTrip() = runBlocking {
        val id = repo.createSession(SessionConfig(), startTime = 1_000L)
        repo.appendRr(id, 61_000L, 900.0, 70)
        repo.appendRr(id, 62_000L, 880.0, 71)
        repo.appendRr(id, 63_000L, 870.0, 69)
        repo.appendEpoch(id, 63_000L, 40.0, 70)
        repo.completeSession(id, 64_000L)

        val loaded = repo.getSession(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded?.avgHRV).isNotNull()

        repo.setDeleted(id, true)
        val deleted = repo.getSession(id)
        assertThat(deleted?.isDeleted).isTrue()
    }
}
