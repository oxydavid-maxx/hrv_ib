package com.hrvib.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val durationMin: Int,
    val metronomeBpm: Double,
    val inhaleBeats: Int,
    val exhaleBeats: Int,
    val breathsPerMin: Double,
    val avgHRV: Double?,
    val peakHRV: Double?,
    val avgHR: Double?,
    val isDeleted: Boolean = false,
    val notes: String? = null
)

@Entity(
    tableName = "rr_samples",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class RrSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val rrMs: Double,
    val hrBpm: Int?
)

@Entity(
    tableName = "epoch_stats",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class EpochStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val rollingHrvRmssd: Double?,
    val hrBpm: Int?
)

data class ChartPoint(
    val x: Double,
    val y: Double,
    val sessionId: Long
)

data class TimeSeriesPoint(
    val bucket: Long,
    val avgHRV: Double,
    val peakHRV: Double
)
