package com.joseleandro.pomolume.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.joseleandro.pomolume.data.local.entity.PomodoroSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroDao {
    @Query("SELECT * FROM pomodoro_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<PomodoroSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PomodoroSessionEntity)
}
