package com.hrvib.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, RrSampleEntity::class, EpochStatEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun rrDao(): RrDao
    abstract fun epochDao(): EpochDao
}
