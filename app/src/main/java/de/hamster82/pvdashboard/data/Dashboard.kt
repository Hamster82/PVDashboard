package de.hamster82.pvdashboard.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class Balken(val label: String, val ertrag: Double?, val verbrauch: Double?, val geschaetzt: Boolean)

data class Bilanz(
    val titel: String,
    val balken: List<Balken>,
    val ertrag: Double,
    val verbrauch: Double,
    val autarkie: Double?,
    val geschaetzt: Boolean,
)

data class DashboardData(
    val zeit: LocalDateTime,
    val heuteKwh: Double?,
    val morgenKwh: Double?,
    val heuteWetter: String,
    val morgenWetter: String,
    val heuteKw: DoubleArray?,
    val morgenKw: DoubleArray?,
    val planHeute: TagPlan?,
    val planMorgen: TagPlan?,
    val empfehlungen: List<Empfehlung>,
    val live: Live?,
    val liveAdresse: String?,
    val liveFehler: String?,
    val ueberschussJetzt: Double,
    val hinweis: String,
    val woche: Bilanz,
    val monat: Bilanz,
    val kalibrierung: Double,
)

object Dashboard {
    private val DE = Locale.GERMANY

    suspend fun collect(ctx: Context, s: Settings): DashboardData = coroutineScope {
        val jetzt = LocalDateTime.now()
        val heute = jetzt.toLocalDate()
        val morgen = heute.plusDays(1)
        val nowQ = jetzt.hour * 4 + (jetzt.minute + 14) / 15

        val fcJob = async(Dispatchers.IO) { runCatching { Forecast.load(s) } }
        val invJob = async(Dispatchers.IO) { if (s.plenticoreAktiv) runCatching { Plenticore.read(s) } else null }
        val model = fcJob.await().getOrElse { throw Exception("Wetterprognose nicht erreichbar: ${it.kurz()}") }
        val inv = invJob.await()
        val res = inv?.getOrNull()
        val live = res?.live
        val liveFehler = inv?.exceptionOrNull()?.kurz()
        if (res != null && res.pin.isNotBlank()) SettingsStore.savePin(ctx, res.pin)

        val hist = History.update(ctx) { h ->
            if (live != null) h.eintragen(live, jetzt)
            History(java.util.TreeMap(h.days), java.util.TreeMap(h.months))
        }

        val k = kalibrierung(hist, model, heute)
        fun kwTag(d: LocalDate) = model.days[d]?.kw?.map { it * k }?.toDoubleArray()
        val heuteKw = kwTag(heute)
        val morgenKw = kwTag(morgen)
        val planHeute = heuteKw?.let { Planner.planDay(it, s, nowQ) }
        val planMorgen = morgenKw?.let { Planner.planDay(it, s, 0) }
        val empf = Planner.recommend(planHeute, planMorgen, s)

        val pvJetzt = heuteKw?.let { Planner.pvAtQuarter(it, jetzt.hour * 4 + jetzt.minute / 15) } ?: 0.0
        val ueberschussJetzt = if (live?.pvKw != null) live.pvKw - (live.hausKw ?: s.grundlastKw) else pvJetzt - s.grundlastKw

        val geraeteKwh = s.geraete.sumOf { Planner.quarterProfile(it).sum() * 0.25 }
        val rest = planHeute?.ueberschussKwh ?: 0.0
        val soc = live?.akkuSoc
        val hinweis = if (soc != null) {
            val bedarf = (1 - soc / 100.0) * s.akkuKwh
            when {
                rest >= bedarf + geraeteKwh -> "Akku wird heute voll und es bleibt genug Sonne für alle Geräte."
                rest >= bedarf -> "Akku wird voll, für Geräte bleiben ca. ${fmt(rest - bedarf)} kWh – das wichtigste Gerät zuerst."
                else -> "Akku wird heute nicht ganz voll – Geräte nur in die empfohlenen Zeitfenster legen."
            }
        } else "Restlicher Solarüberschuss heute ca. ${fmt(rest)} kWh."

        DashboardData(
            zeit = jetzt,
            heuteKwh = model.days[heute]?.kwh?.times(k),
            morgenKwh = model.days[morgen]?.kwh?.times(k),
            heuteWetter = Forecast.wetterText(model.days[heute]?.cloud),
            morgenWetter = Forecast.wetterText(model.days[morgen]?.cloud),
            heuteKw = heuteKw,
            morgenKw = morgenKw,
            planHeute = planHeute,
            planMorgen = planMorgen,
            empfehlungen = empf,
            live = live,
            liveAdresse = res?.adresse,
            liveFehler = liveFehler,
            ueberschussJetzt = ueberschussJetzt,
            hinweis = hinweis,
            woche = woche(s, hist, model, k, jetzt),
            monat = monate(s, hist, model, k, jetzt),
            kalibrierung = k,
        )
    }

    /** Echter Ertrag / Modellertrag der letzten vollständigen Tage (Median), begrenzt auf 0,6…1,4 */
    fun kalibrierung(h: History, m: ForecastModel, heute: LocalDate): Double {
        val r = ArrayList<Double>()
        var i = 1
        while (i <= 21 && r.size < 14) {
            val d = heute.minusDays(i.toLong())
            val real = h.days[d]
            val mod = m.days[d]?.kwh
            if (real != null && real.komplett && real.y > 0 && mod != null && mod > 2) r.add(real.y / mod)
            i++
        }
        if (r.size < 3) return 1.0
        r.sort()
        return r[r.size / 2].coerceIn(0.6, 1.4)
    }

    private fun bisJetzt(kw: DoubleArray, jetzt: LocalDateTime): Double =
        kw.take(jetzt.hour).sum() + kw[jetzt.hour] * jetzt.minute / 60.0

    private fun woche(s: Settings, h: History, m: ForecastModel, k: Double, jetzt: LocalDateTime): Bilanz {
        val heute = jetzt.toLocalDate()
        val rows = (6 downTo 0).map { i ->
            val d = heute.minusDays(i.toLong())
            val echt = h.days[d]
            val modY = m.days[d]?.kwh?.times(k)
            val label = if (i == 0) "Heute" else d.dayOfWeek.getDisplayName(TextStyle.SHORT, DE).trimEnd('.')
            when {
                echt != null && (echt.komplett || i == 0) -> Balken(label, echt.y, echt.h, false)
                echt != null -> Balken(label, maxOf(echt.y, modY ?: 0.0), maxOf(echt.h, schaetzVerbrauch(s, d)), true)
                i == 0 -> Balken(label, m.days[d]?.kw?.let { kw -> bisJetzt(kw, jetzt) * k },
                    schaetzVerbrauch(s, d) * (jetzt.hour + jetzt.minute / 60.0) / 24.0, true)
                else -> Balken(label, modY, schaetzVerbrauch(s, d), true)
            }
        }
        val echteTage = (6 downTo 0).mapNotNull { h.days[heute.minusDays(it.toLong())] }.filter { it.pv != null && it.h > 0 }
        val autarkie = if (echteTage.isNotEmpty())
            echteTage.sumOf { (it.pv ?: 0.0) + (it.bat ?: 0.0) } / echteTage.sumOf { it.h } else null
        return Bilanz("Letzte 7 Tage", rows, rows.sumOf { it.ertrag ?: 0.0 }, rows.sumOf { it.verbrauch ?: 0.0 },
            autarkie?.coerceAtMost(1.0), rows.any { it.geschaetzt })
    }

    private fun monate(s: Settings, h: History, m: ForecastModel, k: Double, jetzt: LocalDateTime): Bilanz {
        val heute = jetzt.toLocalDate()
        val diesM = YearMonth.from(heute)
        val rows = (5 downTo 0).map { i ->
            val ym = diesM.minusMonths(i.toLong())
            val label = ym.month.getDisplayName(TextStyle.SHORT, DE).trimEnd('.')
            val tage = if (ym == diesM) heute.dayOfMonth else ym.lengthOfMonth()
            val echt = h.months[ym]
            fun modellTag(d: LocalDate): Double? = when {
                d == heute -> m.days[d]?.kw?.let { bisJetzt(it, jetzt) * k }
                d >= m.firstDate -> m.days[d]?.kwh?.times(k)
                else -> null
            }
            val verbrauchSchaetz = (1..tage).sumOf { t -> schaetzVerbrauch(s, ym.atDay(t)) }
            when {
                echt != null && (ym == diesM || echt.bis >= tage) -> Balken(label, echt.y, echt.h, false)
                echt != null -> {
                    var y = echt.y; var hh = echt.h; var fehlt = false
                    for (t in (echt.bis + 1)..tage) {
                        val d = ym.atDay(t)
                        val v = modellTag(d)
                        if (v != null) y += v else fehlt = true
                        hh += schaetzVerbrauch(s, d)
                    }
                    Balken(label, if (fehlt) echt.y else y, hh, true)
                }
                else -> {
                    var y = 0.0; var n = 0
                    for (t in 1..tage) modellTag(ym.atDay(t))?.let { y += it; n++ }
                    Balken(label, if (n >= tage * 0.8) y * tage / n else null, verbrauchSchaetz, true)
                }
            }
        }
        val echteMonate = (5 downTo 0).mapNotNull { h.months[diesM.minusMonths(it.toLong())] }
        return Bilanz("Letzte 6 Monate", rows, rows.sumOf { it.ertrag ?: 0.0 }, rows.sumOf { it.verbrauch ?: 0.0 },
            null, rows.any { it.geschaetzt } || echteMonate.isEmpty())
    }
}
