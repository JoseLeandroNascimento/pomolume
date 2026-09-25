package com.joseleandro.pomolume.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.joseleandro.pomolume.core.database.PomodoroDatabase
import com.joseleandro.pomolume.core.datastore.DataStoreTimerStateStore
import com.joseleandro.pomolume.core.notification.PomodoroNotifications
import com.joseleandro.pomolume.core.service.AndroidPomodoroServiceController
import com.joseleandro.pomolume.feature.history.data.RoomHistoryRepository
import com.joseleandro.pomolume.feature.history.domain.*
import com.joseleandro.pomolume.feature.history.presentation.HistoryViewModel
import com.joseleandro.pomolume.feature.pomodoro.data.PomodoroRepositoryImpl
import com.joseleandro.pomolume.feature.pomodoro.domain.*
import com.joseleandro.pomolume.feature.pomodoro.presentation.PomodoroViewModel
import com.joseleandro.pomolume.feature.settings.data.DataStoreSettingsRepository
import com.joseleandro.pomolume.feature.settings.domain.*
import com.joseleandro.pomolume.feature.settings.presentation.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private val pomodoroModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<DataStore<Preferences>> {
        val context = androidContext()
        PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = get(),
            produceFile = { context.preferencesDataStoreFile("pomolume") }
        )
    }
    single<SettingsRepository> { DataStoreSettingsRepository(get()) }
    single<TimerStateStore> { DataStoreTimerStateStore(get()) }
    single {
        Room.databaseBuilder(androidContext(), PomodoroDatabase::class.java, PomodoroDatabase.NAME)
            .addMigrations(PomodoroDatabase.MIGRATION_1_2)
            .build()
    }
    single { get<PomodoroDatabase>().sessionDao() }
    single { RoomHistoryRepository(get()) }
    single<HistoryRepository> { get<RoomHistoryRepository>() }
    single<HistoryStatsRepository> { get<RoomHistoryRepository>() }
    single<PomodoroClock> { PomodoroClock(System::currentTimeMillis) }
    single<PomodoroRepository> { PomodoroRepositoryImpl(get(), get(), get(), get(), get()) }
    single<PomodoroServiceController> { AndroidPomodoroServiceController(androidContext(), get(), get()) }
    single { PomodoroNotifications(androidContext()) }
    factory { GetPomodoroStateUseCase(get()) }
    factory { GetPomodoroCompletionsUseCase(get()) }
    factory { ControlPomodoroUseCase(get()) }
    factory { GetHistoryUseCase(get()) }
    factory { GetHistoryStatsUseCase(get()) }
    factory { GetSettingsUseCase(get()) }
    factory { UpdateSettingsUseCase(get()) }
    viewModel { PomodoroViewModel(get(), get(), get(), get()) }
    viewModel { HistoryViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get()) }
}

val appModules = listOf(pomodoroModule)
