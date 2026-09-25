package com.joseleandro.pomolume.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroSessionDao {
    // A stable session UUID makes recovery after a process death safe to repeat.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: PomodoroSessionEntity): Long

    @Update
    suspend fun update(session: PomodoroSessionEntity)

    @Query("SELECT * FROM pomodoro_sessions WHERE id = :id")
    suspend fun findById(id: String): PomodoroSessionEntity?

    @Query("SELECT * FROM pomodoro_sessions ORDER BY endedAt DESC, id DESC")
    fun observeAll(): Flow<List<PomodoroSessionEntity>>

    @Query("""
        SELECT * FROM pomodoro_sessions
        WHERE (:startDate IS NULL OR endedAt >= :startDate)
          AND (:endDate IS NULL OR endedAt < :endDate)
        ORDER BY endedAt DESC, id DESC
    """)
    fun observePeriod(startDate: Long?, endDate: Long?): Flow<List<PomodoroSessionEntity>>

    // Caller supplies local date boundaries; SQL does not assume a fixed timezone or 24h day.
    @Query("""
        SELECT * FROM pomodoro_sessions
        WHERE endedAt >= :startOfDay AND endedAt < :nextDay
        ORDER BY endedAt DESC, id DESC
    """)
    fun observeDate(startOfDay: Long, nextDay: Long): Flow<List<PomodoroSessionEntity>>

    @Query("""
        SELECT COUNT(*) FROM pomodoro_sessions
        WHERE type = 'FOCUS' AND status = 'COMPLETED'
          AND endedAt >= :startDate AND endedAt < :endDate
    """)
    fun countCompletedFocus(startDate: Long, endDate: Long): Flow<Int>

    @Query("""
        SELECT COALESCE(SUM(actualDurationSeconds), 0) FROM pomodoro_sessions
        WHERE type = 'FOCUS' AND status = 'COMPLETED'
          AND endedAt >= :startDate AND endedAt < :endDate
    """)
    fun sumFocusSeconds(startDate: Long, endDate: Long): Flow<Long>
}
