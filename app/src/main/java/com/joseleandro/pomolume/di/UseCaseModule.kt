package com.joseleandro.pomolume.di

import com.joseleandro.pomolume.domain.usecase.GetPomodoroSessionsUseCase
import com.joseleandro.pomolume.domain.usecase.SavePomodoroSessionUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val useCaseModule = module {
    factoryOf(::GetPomodoroSessionsUseCase)
    factoryOf(::SavePomodoroSessionUseCase)
}
