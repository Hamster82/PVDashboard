package de.hamster82.pvdashboard.data

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** startQ = empfohlene Startzeit, vonQ..bisQ = Startfenster mit ≥ 90 % des Bestwerts */
data class GeraetPlan(val startQ: Int, val durQ: Int, val vonQ: Int, val bisQ: Int, val coverage: Double)

data class TagPlan(val geraete: Map<String, GeraetPlan?>, val ueberschussKwh: Double)

data class Empfehlung(val geraet: Geraet, val morgen: Boolean, val plan: GeraetPlan?)

object Planner {
    /** PV-Leistung in der Viertelstunde q (linear zwischen den Stundenmitten) */
    fun pvAtQuarter(kw: DoubleArray, q: Int): Double {
        val c = q / 4.0 + 0.125 - 0.5
        val i = floor(c).toInt()
        val f = c - i
        val a = kw[i.coerceIn(0, 23)]
        val b = kw[(i + 1).coerceIn(0, 23)]
        return max(0.0, a + (b - a) * f)
    }

    /** Lastprofil -> mittlere Leistung je Viertelstunde */
    fun quarterProfile(g: Geraet): DoubleArray {
        val minutes = ArrayList<Double>()
        g.profil().forEach { (m, kw) -> repeat(m) { minutes.add(kw) } }
        val n = ceil(minutes.size / 15.0).toInt()
        return DoubleArray(n) { i ->
            var sum = 0.0
            for (k in i * 15 until min(minutes.size, i * 15 + 15)) sum += minutes[k]
            sum / 15.0
        }
    }

    fun planDay(kw: DoubleArray, s: Settings, fromQ: Int): TagPlan {
        val surplus = DoubleArray(96) { max(0.0, pvAtQuarter(kw, it) - s.grundlastKw) }
        val startQ = max(fromQ, timeToQ(s.fruehesterStart))
        val lastQ = timeToQ(s.spaetesterStart)
        var ueb = 0.0
        for (q in startQ until 96) ueb += surplus[q] * 0.25

        val res = LinkedHashMap<String, GeraetPlan?>()
        for (g in s.geraete) {
            val pw = quarterProfile(g)
            val durQ = pw.size
            val energie = pw.sum() * 0.25
            if (durQ == 0 || energie <= 0) { res[g.id] = null; continue }
            val qs = ArrayList<Int>(); val solar = ArrayList<Double>(); val margin = ArrayList<Double>()
            for (q in startQ..min(lastQ, 96 - durQ)) {
                var so = 0.0; var ma = 0.0
                for (k in 0 until durQ) {
                    so += min(surplus[q + k], pw[k]) * 0.25
                    ma += min(surplus[q + k] - pw[k], 2.0)
                }
                qs.add(q); solar.add(so); margin.add(ma)
            }
            if (qs.isEmpty()) { res[g.id] = null; continue }
            val best = solar.max()
            // unter den (fast) besten Startzeiten die mit dem meisten Puffer -> mittig im Sonnenfenster
            var bi = -1
            for (i in qs.indices) if (solar[i] >= best * 0.98 - 1e-9 && (bi < 0 || margin[i] > margin[bi])) bi = i
            var lo = bi; var hi = bi
            while (lo > 0 && solar[lo - 1] >= best * 0.9) lo--
            while (hi < qs.size - 1 && solar[hi + 1] >= best * 0.9) hi++
            for (k in 0 until durQ) {
                val q = qs[bi] + k
                surplus[q] = max(0.0, surplus[q] - pw[k])
            }
            res[g.id] = GeraetPlan(qs[bi], durQ, qs[lo], qs[hi], solar[bi] / energie)
        }
        return TagPlan(res, ueb)
    }

    /** Entscheidet je Gerät: heute oder morgen */
    fun recommend(heute: TagPlan?, morgen: TagPlan?, s: Settings): List<Empfehlung> = s.geraete.map { g ->
        val h = heute?.geraete?.get(g.id)
        val m = morgen?.geraete?.get(g.id)
        if (h == null || (m != null && h.coverage < 0.6 && m.coverage > h.coverage + 0.15)) Empfehlung(g, true, m)
        else Empfehlung(g, false, h)
    }

    fun bewertung(cov: Double): String = when {
        cov >= 0.9 -> "fast komplett mit Sonnenstrom"
        cov >= 0.6 -> "überwiegend mit Sonnenstrom"
        cov >= 0.3 -> "teilweise mit Sonnenstrom"
        else -> "kaum mit Sonnenstrom"
    }
}
