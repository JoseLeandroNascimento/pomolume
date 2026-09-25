package com.joseleandro.pomolume.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joseleandro.pomolume.feature.history.data.RoomHistoryRepository
import com.joseleandro.pomolume.feature.history.domain.PomodoroSession
import com.joseleandro.pomolume.feature.history.domain.SessionStatus
import com.joseleandro.pomolume.feature.pomodoro.domain.SessionType
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PomodoroDatabaseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun duplicateCompletionIsSavedExactlyOnceAndPeriodBoundariesAreExclusive() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, PomodoroDatabase::class.java).build()
        try {
            val repository = RoomHistoryRepository(database.sessionDao())
            val focus = PomodoroSession(
                id = "focus-1", type = SessionType.FOCUS, status = SessionStatus.COMPLETED,
                startedAt = 100, endedAt = 1_500_100, plannedDurationSeconds = 1_500,
                actualDurationSeconds = 1_500
            )
            repository.save(focus)
            repository.save(focus)
            repository.save(focus.copy(id = "break", type = SessionType.SHORT_BREAK, endedAt = 1_600_100))
            repository.save(focus.copy(id = "skipped", status = SessionStatus.SKIPPED, endedAt = 1_700_100))
            assertEquals(3, repository.observeSessions().first().size)
            assertEquals(1, repository.observeSessions(focus.endedAt, focus.endedAt + 1).first().size)
            assertEquals(0, repository.observeSessions(0, focus.endedAt).first().size)
            assertEquals(1, database.sessionDao().countCompletedFocus(0, Long.MAX_VALUE).first())
            assertEquals(1_500L, database.sessionDao().sumFocusSeconds(0, Long.MAX_VALUE).first())
            assertNotNull(database.sessionDao().findById("focus-1"))
        } finally {
            database.close()
        }
    }

    @Test fun dailyAggregationUsesLocalMidnightAndExcludesBreaksCancelledAndSkipped() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, PomodoroDatabase::class.java).build()
        try {
            val repository = RoomHistoryRepository(database.sessionDao())
            val zone = java.time.ZoneId.systemDefault()
            val day = java.time.LocalDate.of(2026, 9, 24)
            val midnight = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val tomorrow = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val base = PomodoroSession("at-start", SessionType.FOCUS, SessionStatus.COMPLETED,
                midnight - 1_500_000, midnight, 1_500, 1_500)
            repository.save(base.copy(id = "before-start", endedAt = midnight - 1))
            repository.save(base)
            repository.save(base.copy(id = "during", endedAt = midnight + 3_600_000, actualDurationSeconds = 900))
            repository.save(base.copy(id = "at-end", endedAt = tomorrow))
            repository.save(base.copy(id = "break", type = SessionType.SHORT_BREAK, endedAt = midnight + 1))
            repository.save(base.copy(id = "long-break", type = SessionType.LONG_BREAK, endedAt = midnight + 2))
            repository.save(base.copy(id = "cancelled", status = SessionStatus.CANCELLED, endedAt = midnight + 3))
            repository.save(base.copy(id = "skipped", status = SessionStatus.SKIPPED, endedAt = midnight + 4))
            val aggregated = repository.observeDailyFocus(midnight, tomorrow).first()
            assertEquals(1, aggregated.size)
            assertEquals(day, aggregated.single().date)
            assertEquals(2_400L, aggregated.single().focusSeconds)
            assertEquals(2, aggregated.single().completedPomodoros)
            val allDays = repository.observeDailyFocus(null, null).first()
            assertEquals(3, allDays.size)
            assertEquals(day.minusDays(1), allDays.first().date)
            assertEquals(day.plusDays(1), allDays.last().date)
        } finally {
            database.close()
        }
    }

    @Test fun versionOneMigrationPreservesCompletedAndCancelledHistory() = runBlocking {
        val databaseName = "migration-${UUID.randomUUID()}.db"
        val legacy = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE pomodoro_sessions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                durationInMinutes INTEGER NOT NULL,
                                timestamp INTEGER NOT NULL,
                                isCompleted INTEGER NOT NULL
                            )
                        """.trimIndent())
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        legacy.writableDatabase.execSQL(
            "INSERT INTO pomodoro_sessions (id, durationInMinutes, timestamp, isCompleted) VALUES (1, 25, 2000000, 1), (2, 10, 3000000, 0)"
        )
        legacy.close()
        val database = Room.databaseBuilder(context, PomodoroDatabase::class.java, databaseName)
            .addMigrations(PomodoroDatabase.MIGRATION_1_2)
            .build()
        try {
            val sessions = RoomHistoryRepository(database.sessionDao()).observeSessions().first()
            assertEquals(2, sessions.size)
            val completed = sessions.single { it.id == "legacy-1" }
            assertEquals(SessionStatus.COMPLETED, completed.status)
            assertEquals(SessionType.FOCUS, completed.type)
            assertEquals(500_000L, completed.startedAt)
            assertEquals(2_000_000L, completed.endedAt)
            assertEquals(1_500L, completed.actualDurationSeconds)
            val cancelled = sessions.single { it.id == "legacy-2" }
            assertEquals(SessionStatus.CANCELLED, cancelled.status)
            assertEquals(600L, cancelled.plannedDurationSeconds)
            assertEquals(0L, cancelled.actualDurationSeconds)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }
}
