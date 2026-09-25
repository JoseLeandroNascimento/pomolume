package com.joseleandro.pomolume.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.joseleandro.pomolume.data.local.dao.PomodoroDao
import com.joseleandro.pomolume.data.local.entity.PomodoroSessionEntity

@Database(
    entities = [PomodoroSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pomodoroDao(): PomodoroDao
}
