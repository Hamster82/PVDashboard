package de.hamster82.pvdashboard.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.hamster82.pvdashboard.R
import de.hamster82.pvdashboard.data.DashboardData
import de.hamster82.pvdashboard.data.Settings
import de.hamster82.pvdashboard.data.fmt
import de.hamster82.pvdashboard.data.qToTime
import de.hamster82.pvdashboard.ui.MainActivity

/** Meldet einmal pro Tag und Gerät, wenn dessen empfohlenes Startfenster begonnen hat. */
object Notifier {
    private const val KANAL = "startzeiten"

    fun pruefen(ctx: Context, s: Settings, d: DashboardData) {
        if (!s.benachrichtigungen) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        kanal(ctx)
        val prefs = ctx.getSharedPreferences("gemeldet", Context.MODE_PRIVATE)
        val heute = d.zeit.toLocalDate().toString()
        val nowQ = d.zeit.hour * 4 + d.zeit.minute / 15
        d.empfehlungen.forEachIndexed { i, e ->
            val p = e.plan ?: return@forEachIndexed
            if (e.morgen || p.coverage < 0.5) return@forEachIndexed
            if (nowQ < p.vonQ || nowQ > p.bisQ) return@forEachIndexed
            val key = "$heute/${e.geraet.id}"
            if (prefs.getBoolean(key, false)) return@forEachIndexed
            val text = "Jetzt starten – Startfenster bis ${qToTime(p.bisQ)}, läuft zu ≈ ${fmt(p.coverage * 100, 0)} % mit Sonnenstrom."
            val intent = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, KANAL)
                .setSmallIcon(R.drawable.ic_stat_sonne)
                .setContentTitle("${e.geraet.name}: gute Sonnenzeit")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            try {
                NotificationManagerCompat.from(ctx).notify(1000 + i, n)
                prefs.edit().putBoolean(key, true).apply()
            } catch (_: SecurityException) {
            }
        }
        // alte Einträge aufräumen
        prefs.all.keys.filter { !it.startsWith(heute) }.let { alt -> if (alt.isNotEmpty()) prefs.edit().apply { alt.forEach { remove(it) } }.apply() }
    }

    private fun kanal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(KANAL) == null) {
            nm.createNotificationChannel(NotificationChannel(KANAL, "Startzeiten der Geräte", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
}
