package de.hamster82.pvdashboard.data

import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject

/** kw[h] = mittlere PV-Leistung von h:00 bis h+1:00 (noch ohne Kalibrierung) */
class DayForecast(val kw: DoubleArray, val cloud: Double?) {
    val kwh: Double get() = kw.sum()
}

/** Prognostizierte Sonnenstunden und Tageslänge (Stunden) */
class SonnenTag(val datum: LocalDate, val sonneH: Double, val tagH: Double?)

class ForecastModel(val days: Map<LocalDate, DayForecast>, val firstDate: LocalDate, val sonne: List<SonnenTag> = emptyList())

/**
 * Einstrahlung auf die geneigten Modulflächen von Open-Meteo (kostenlos, ohne Schlüssel)
 * für die letzten 92 Tage + heute + 2 Tage, umgerechnet in PV-Leistung.
 */
object Forecast {
    fun load(s: Settings): ForecastModel {
        val aktive = s.flaechen.filter { it.kwp > 0 }
        require(aktive.isNotEmpty()) { "Keine Modulfläche mit kWp > 0 eingetragen" }
        val zone = URLEncoder.encode(ZoneId.systemDefault().id, "UTF-8")
        val resps = aktive.mapIndexed { i, f ->
            val hourly = if (i == 0) "global_tilted_irradiance,temperature_2m,cloud_cover" else "global_tilted_irradiance"
            val daily = if (i == 0) "&daily=sunshine_duration,daylight_duration" else ""
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${s.lat}&longitude=${s.lon}" +
                "&hourly=$hourly&tilt=${f.neigung}&azimuth=${f.azimut}&timezone=$zone&past_days=92&forecast_days=7$daily"
            Http.get(url) as JSONObject
        }
        return build(s, aktive, resps)
    }

    fun build(s: Settings, flaechen: List<Flaeche>, resps: List<JSONObject>): ForecastModel {
        val h0 = resps[0].getJSONObject("hourly")
        val time = h0.getJSONArray("time")
        val temp = h0.optJSONArray("temperature_2m")
        val cloud = h0.optJSONArray("cloud_cover")
        val gtis = resps.map { it.getJSONObject("hourly").getJSONArray("global_tilted_irradiance") }
        val kwMap = LinkedHashMap<LocalDate, DoubleArray>()
        val cSum = HashMap<LocalDate, Double>()
        val cN = HashMap<LocalDate, Int>()
        for (i in 0 until time.length()) {
            var kw = 0.0
            var any = false
            flaechen.forEachIndexed { j, f ->
                val g = gtis[j].optDouble(i, Double.NaN)
                if (g.isNaN()) return@forEachIndexed
                any = true
                val t = temp?.optDouble(i, 15.0)?.takeIf { !it.isNaN() } ?: 15.0
                val tCell = t + g / 800.0 * 25.0 // vereinfachtes NOCT-Modell
                kw += f.kwp * (g / 1000.0) * s.performanceRatio * (1 + s.tempKoeff * (tCell - 25))
            }
            if (!any) continue
            kw = kw.coerceIn(0.0, s.wrMaxKw)
            // Open-Meteo liefert Mittelwerte der vorangegangenen Stunde -> Startstunde = Zeitstempel - 1 h
            val start = LocalDateTime.parse(time.getString(i)).minusHours(1)
            val d = start.toLocalDate()
            val h = start.hour
            kwMap.getOrPut(d) { DoubleArray(24) }[h] = kw
            val c = cloud?.optDouble(i, Double.NaN) ?: Double.NaN
            if (h in 8..17 && !c.isNaN()) {
                cSum[d] = (cSum[d] ?: 0.0) + c
                cN[d] = (cN[d] ?: 0) + 1
            }
        }
        val days = kwMap.mapValues { (d, kw) -> DayForecast(kw, cN[d]?.let { n -> cSum[d]!! / n }) }
        val first = LocalDate.parse(time.getString(0).substring(0, 10))
        val sonne = ArrayList<SonnenTag>()
        resps[0].optJSONObject("daily")?.let { dl ->
            val dt = dl.optJSONArray("time")
            val sd = dl.optJSONArray("sunshine_duration")
            val tl = dl.optJSONArray("daylight_duration")
            if (dt != null && sd != null) for (i in 0 until dt.length()) {
                val sec = sd.optDouble(i, Double.NaN)
                if (sec.isNaN()) continue
                val tag = tl?.optDouble(i, Double.NaN)?.takeIf { !it.isNaN() }?.div(3600.0)
                sonne.add(SonnenTag(LocalDate.parse(dt.getString(i)), sec / 3600.0, tag))
            }
        }
        return ForecastModel(days, first, sonne)
    }

    fun wetterText(cloud: Double?): String = when {
        cloud == null -> ""
        cloud < 25 -> "sonnig"
        cloud < 55 -> "heiter bis wolkig"
        cloud < 85 -> "wechselhaft"
        else -> "bedeckt"
    }
}
