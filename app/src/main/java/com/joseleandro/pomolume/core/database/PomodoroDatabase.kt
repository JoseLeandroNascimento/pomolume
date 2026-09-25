package com.joseleandro.pomolume.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PomodoroSessionEntity::class], version = 2, exportSchema = true)
abstract class PomodoroDatabase : RoomDatabase() {
    abstract fun sessionDao(): PomodoroSessionDao

    companion object {
        const val NAME = "pomolume.db"

        /**
         * The original release stored focus duration, finish timestamp and completion flag.
         * Preserve every record and derive its start from the known planned duration.
         * The legacy schema did not capture elapsed time for cancelled sessions.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pomodoro_sessions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        endedAt INTEGER NOT NULL,
                        plannedDurationSeconds INTEGER NOT NULL,
                        actualDurationSeconds INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO pomodoro_sessions_new
                        (id, type, status, startedAt, endedAt, plannedDurationSeconds, actualDurationSeconds)
                    SELECT 'legacy-' || id,
                        'FOCUS',
                        CASE WHEN isCompleted = 1 THEN 'COMPLETED' ELSE 'CANCELLED' END,
                        MAX(0, timestamp - MAX(0, durationInMinutes) * 60000),
                        MAX(0, timestamp),
                        MAX(0, durationInMinutes) * 60,
                        CASE WHEN isCompleted = 1 THEN MAX(0, durationInMinutes) * 60 ELSE 0 END
                    FROM pomodoro_sessions
                """.trimIndent())
                db.execSQL("DROP TABLE pomodoro_sessions")
                db.execSQL("ALTER TABLE pomodoro_sessions_new RENAME TO pomodoro_sessions")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pomodoro_sessions_endedAt ON pomodoro_sessions (endedAt)")
            }
        }
    }
}
