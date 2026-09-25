package com.joseleandro.pomolume

import android.app.Application
import com.joseleandro.pomolume.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PomoLumeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@PomoLumeApplication)
            modules(appModules)
        }
    }
}
