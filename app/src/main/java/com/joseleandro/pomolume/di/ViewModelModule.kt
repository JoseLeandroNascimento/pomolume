package com.joseleandro.pomolume.di

import com.joseleandro.pomolume.ui.navigation.NavigationViewModel
import com.joseleandro.pomolume.ui.stats.StatsViewModel
import com.joseleandro.pomolume.ui.timer.TimerViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::NavigationViewModel)
    viewModelOf(::TimerViewModel)
    viewModelOf(::StatsViewModel)
}
