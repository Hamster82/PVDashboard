package de.hamster82.pvdashboard.data

import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import org.json.JSONTokener

/** Viertelstunde (0..96) -> "10:15" */
fun qToTime(q: Int): String = String.format(Locale.ROOT, "%02d:%02d", (q / 4).coerceAtMost(24), (q % 4) * 15)

fun timeToQ(s: String): Int {
    val p = s.trim().split(":")
    val h = p.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    val m = p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    return (h * 4 + m / 15).coerceIn(0, 96)
}

private val DE = Locale.GERMANY

fun fmt(x: Double?, digits: Int = 1): String =
    if (x == null || x.isNaN() || x.isInfinite()) "–" else String.format(DE, "%,.${digits}f", x)

fun fmtKwh(x: Double?): String = if (x == null) "–" else "${fmt(x, if (kotlin.math.abs(x) >= 100) 0 else 1)} kWh"

/** Text in eine Zahl wandeln, Komma oder Punkt erlaubt */
fun parseNum(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** Geschätzter Tagesverbrauch, solange keine echten Daten vorliegen (Winter etwas mehr, Sommer etwas weniger). */
fun schaetzVerbrauch(s: Settings, d: LocalDate): Double {
    val doy = d.dayOfYear - 1
    return s.jahresverbrauchKwh / 365.0 * (1 + 0.15 * cos(2 * PI * (doy - 15) / 365.0))
}

object Http {
    fun get(url: String, timeoutMs: Int = 10000): Any {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "PVDashboard-Android")
        try {
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw java.io.IOException("HTTP $code von ${URL(url).host}")
            return JSONTokener(text).nextValue()
        } finally {
            c.disconnect()
        }
    }
}

/** Verständliche Fehlermeldung */
fun Throwable.kurz(): String = when (this) {
    is java.net.UnknownHostException -> "Adresse nicht gefunden – keine Internetverbindung?"
    is java.net.SocketTimeoutException -> "Zeitüberschreitung – Gerät nicht erreichbar"
    is java.net.ConnectException -> "Verbindung abgelehnt – Gerät nicht erreichbar"
    else -> message ?: javaClass.simpleName
}
