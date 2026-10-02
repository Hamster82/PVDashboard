package de.hamster82.pvdashboard.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.hamster82.pvdashboard.data.Dashboard
import de.hamster82.pvdashboard.data.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Läuft etwa alle 30 Minuten im Hintergrund: speichert Tageswerte vom Wechselrichter
 * (für Wochen- und Monatsauswertung) und meldet gute Startzeiten.
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val s = SettingsStore.load(applicationContext)
        return try {
            val d = Dashboard.collect(applicationContext, s)
            Notifier.pruefen(applicationContext, SettingsStore.load(applicationContext), d)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun planen(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("aktualisieren", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
