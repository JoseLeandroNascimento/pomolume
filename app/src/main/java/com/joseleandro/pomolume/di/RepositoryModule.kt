package com.joseleandro.pomolume.di

import com.joseleandro.pomolume.data.repository.PomodoroRepositoryImpl
import com.joseleandro.pomolume.domain.repository.PomodoroRepository
import org.koin.dsl.module

val repositoryModule = module {
    single<PomodoroRepository> { PomodoroRepositoryImpl(get()) }
}
