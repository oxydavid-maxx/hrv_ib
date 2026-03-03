package com.hrvib.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query(
        "SELECT * FROM sessions " +
            "WHERE startTime >= :fromTime " +
            "AND (:showExcluded = 1 OR isDeleted = 0) " +
            "ORDER BY startTime ASC"
    )
    fun observeSessions(fromTime: Long, showExcluded: Boolean): Flow<List<SessionEntity>>

    @Query("UPDATE sessions SET isDeleted = :deleted WHERE id = :id")
    suspend fun setDeleted(id: Long, deleted: Boolean)
}

@Dao
interface RrDao {
    @Insert
    suspend fun insertAll(items: List<RrSampleEntity>)

    @Query("SELECT * FROM rr_samples WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun bySession(sessionId: Long): List<RrSampleEntity>
}

@Dao
interface EpochDao {
    @Insert
    suspend fun insertAll(items: List<EpochStatEntity>)

    @Query("SELECT * FROM epoch_stats WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun bySession(sessionId: Long): List<EpochStatEntity>
}
