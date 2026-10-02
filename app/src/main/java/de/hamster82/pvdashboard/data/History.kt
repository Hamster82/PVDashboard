package de.hamster82.pvdashboard.data

import android.content.Context
import java.time.LocalDate
import java.time.YearMonth
import java.util.TreeMap
import org.json.JSONObject

/** Echte Tageswerte vom Wechselrichter (kWh). komplett = nach 22 Uhr erfasst, also ganzer Tag. */
data class DayRec(val y: Double, val h: Double, val pv: Double?, val bat: Double?, val komplett: Boolean)

/** Echte Monatswerte; bis = bis zu diesem Tag vollständig erfasst */
data class MonthRec(val y: Double, val h: Double, val bis: Int)

class History(val days: TreeMap<LocalDate, DayRec>, val months: TreeMap<YearMonth, MonthRec>) {

    /** Neue Werte vom Wechselrichter eintragen. Tageszähler steigen über den Tag, daher Maximum behalten. */
    fun eintragen(live: Live, jetzt: java.time.LocalDateTime) {
        val heute = jetzt.toLocalDate()
        val spaet = jetzt.hour >= 22
        live.tagErtrag?.let { y ->
            val alt = days[heute]
            days[heute] = DayRec(
                y = maxOf(y, alt?.y ?: 0.0),
                h = maxOf(live.tagHaus ?: 0.0, alt?.h ?: 0.0),
                pv = live.tagPv ?: alt?.pv,
                bat = live.tagBat ?: alt?.bat,
                komplett = spaet || (alt?.komplett ?: false),
            )
        }
        live.monatErtrag?.let { y ->
            val ym = YearMonth.from(heute)
            val bis = heute.dayOfMonth - (if (spaet) 0 else 1)
            months[ym] = MonthRec(y, live.monatHaus ?: 0.0, maxOf(bis, months[ym]?.bis ?: 0))
        }
        val grenze = heute.minusDays(400)
        days.headMap(grenze).clear()
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("days", JSONObject().apply {
            days.forEach { (d, r) ->
                put(d.toString(), JSONObject().put("y", r.y).put("h", r.h).put("pv", r.pv ?: JSONObject.NULL)
                    .put("bat", r.bat ?: JSONObject.NULL).put("komplett", r.komplett))
            }
        })
        put("months", JSONObject().apply {
            months.forEach { (m, r) -> put(m.toString(), JSONObject().put("y", r.y).put("h", r.h).put("bis", r.bis)) }
        })
    }

    companion object {
        private const val PREFS = "verlauf"
        private val lock = Any()

        fun fromJson(o: JSONObject): History {
            val days = TreeMap<LocalDate, DayRec>()
            o.optJSONObject("days")?.let { d ->
                d.keys().forEach { k ->
                    val r = d.getJSONObject(k)
                    days[LocalDate.parse(k)] = DayRec(
                        r.optDouble("y", 0.0), r.optDouble("h", 0.0),
                        r.optDouble("pv", Double.NaN).takeIf { !it.isNaN() },
                        r.optDouble("bat", Double.NaN).takeIf { !it.isNaN() },
                        r.optBoolean("komplett", false),
                    )
                }
            }
            val months = TreeMap<YearMonth, MonthRec>()
            o.optJSONObject("months")?.let { m ->
                m.keys().forEach { k ->
                    val r = m.getJSONObject(k)
                    months[YearMonth.parse(k)] = MonthRec(r.optDouble("y", 0.0), r.optDouble("h", 0.0), r.optInt("bis", 0))
                }
            }
            return History(days, months)
        }

        /** Liest, verändert und speichert den Verlauf in einem Schritt (UI und Hintergrunddienst teilen ihn). */
        fun <T> update(ctx: Context, block: (History) -> T): T = synchronized(lock) {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val h = prefs.getString("json", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() }
                ?: History(TreeMap(), TreeMap())
            val r = block(h)
            prefs.edit().putString("json", h.toJson().toString()).commit()
            r
        }
    }
}
