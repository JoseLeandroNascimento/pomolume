package com.joseleandro.pomolume.di

import androidx.room.Room
import com.joseleandro.pomolume.data.local.AppDatabase
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidApplication(),
            AppDatabase::class.java,
            "pomolume.db"
        ).build()
    }

    single { get<AppDatabase>().pomodoroDao() }
}
