package de.hamster82.pvdashboard

import android.app.Application
import de.hamster82.pvdashboard.work.RefreshWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RefreshWorker.planen(this)
    }
}
