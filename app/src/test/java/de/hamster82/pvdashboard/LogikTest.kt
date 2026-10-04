package de.hamster82.pvdashboard

import de.hamster82.pvdashboard.data.Flaeche
import de.hamster82.pvdashboard.data.Forecast
import de.hamster82.pvdashboard.data.Planner
import de.hamster82.pvdashboard.data.Plenticore
import de.hamster82.pvdashboard.data.Scram
import de.hamster82.pvdashboard.data.Settings
import de.hamster82.pvdashboard.data.qToTime
import de.hamster82.pvdashboard.data.settingsFromJson
import java.time.LocalDate
import kotlin.math.exp
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogikTest {

    /** Testvektor unabhängig mit Node.js crypto erzeugt */
    @Test
    fun scramWieWeboberflaeche() {
        val k = Scram.derive("geheim123", "Y2xpZW50bm9uY2UxMg==", "Y2xpZW50bm9uY2UxMg==U2VydmVy", "AAECAwQFBgcICQoLDA0ODw==", 1000)
        assertEquals("ewydOV+esSm+WD1VkcdjV32YMv+kiSkDqXwD8Ed/cxw=", k.proof)
        assertEquals("dkgEUR8m0iAiQN4TKFvaSy3NfnjQjSYRAq5oYOjWPkw=", Scram.b64(k.serverSignature))
        val (ct, tag) = Scram.encrypt(k.protocolKey, ByteArray(16) { 7 }, "tokentoken123".toByteArray())
        assertEquals("jE4Jhe7uISrbcvc47g==", Scram.b64(ct))
        assertEquals("K4Yze+gbm7udfj0UpOc1Hg==", Scram.b64(tag))
    }

    /** Synthetische Open-Meteo-Antwort: Ost-Peak 10 Uhr, West-Peak 15 Uhr */
    private fun antwort(azimut: Double, tage: Int = 5): JSONObject {
        val time = JSONArray(); val gti = JSONArray(); val temp = JSONArray(); val cloud = JSONArray()
        val start = LocalDate.of(2026, 9, 28)
        for (d in 0 until tage) for (h in 0 until 24) {
            time.put("${start.plusDays(d.toLong())}T%02d:00".format(h))
            val peak = if (azimut < 0) 10.0 else 15.0
            val x = (h - 0.5 - peak) / 2.4
            gti.put(if (h in 8..19) 620 * exp(-x * x) else 0.0)
            temp.put(14.0); cloud.put(30.0)
        }
        return JSONObject().put("hourly", JSONObject().put("time", time).put("global_tilted_irradiance", gti)
            .put("temperature_2m", temp).put("cloud_cover", cloud))
    }

    @Test
    fun prognoseUndPlanung() {
        val s = Settings()
        val m = Forecast.build(s, s.flaechen, listOf(antwort(-90.0), antwort(90.0)))
        val tag = m.days[LocalDate.of(2026, 9, 30)]!!
        assertTrue("Tagesertrag plausibel: ${tag.kwh}", tag.kwh in 8.0..30.0)
        assertTrue(tag.kw.all { it <= s.wrMaxKw })
        assertEquals("heiter bis wolkig", Forecast.wetterText(tag.cloud))

        val plan = Planner.planDay(tag.kw, s, 0)
        val w = plan.geraete["waschmaschine"]!!
        val sp = plan.geraete["spuelmaschine"]!!
        assertTrue("Waschmaschine tagsüber: ${qToTime(w.startQ)}", w.startQ in 28..64)
        assertTrue(w.vonQ <= w.startQ && w.startQ <= w.bisQ)
        assertTrue("Deckung ${w.coverage}", w.coverage in 0.3..1.0)
        // Heizphasen der Geräte dürfen sich nicht überlappen, solange Überschuss knapp ist
        assertNotNull(sp)
        assertTrue(plan.ueberschussKwh > 0)

        // Abends: Empfehlung für morgen
        val abends = Planner.planDay(tag.kw, s, 19 * 4)
        val morgen = Planner.planDay(tag.kw, s, 0)
        val e = Planner.recommend(abends, morgen, s)
        assertTrue(e.all { it.morgen })
    }

    @Test
    fun sonnenstunden() {
        val s = Settings()
        val r0 = antwort(-90.0).put("daily", JSONObject()
            .put("time", JSONArray().put("2026-09-28").put("2026-09-29"))
            .put("sunshine_duration", JSONArray().put(27000.0).put(JSONObject.NULL))
            .put("daylight_duration", JSONArray().put(43200.0).put(43000.0)))
        val m = Forecast.build(s, s.flaechen, listOf(r0, antwort(90.0)))
        assertEquals(1, m.sonne.size)
        assertEquals(7.5, m.sonne[0].sonneH, 1e-9)
        assertEquals(12.0, m.sonne[0].tagH!!, 1e-9)
    }

    @Test
    fun lastprofil() {
        val g = Settings().geraete.first()
        val q = Planner.quarterProfile(g)
        assertEquals(10, q.size)
        assertEquals(g.energieKwh, q.sum() * 0.25, 0.01)
    }

    @Test
    fun einstellungenJson() {
        val s = Settings(ortName = "Test", flaechen = listOf(Flaeche("Süd", 5.0, 35.0, 0.0)), plenticoreLokal = "192.168.178.40")
        assertEquals(s, settingsFromJson(JSONObject(s.toJson().toString())))
    }

    @Test
    fun plenticoreWerte() {
        val l = Plenticore.parse(mapOf(
            "devices:local:pv1/P" to 1300.0, "devices:local:pv2/P" to 1100.0, "devices:local/Home_P" to 600.0,
            "scb:statistic:EnergyFlow/Statistic:Yield:Day" to 8400.0, "scb:statistic:EnergyFlow/Statistic:Autarky:Day" to 81.0,
        ))
        assertEquals(2.4, l.pvKw!!, 1e-9)
        assertEquals(8.4, l.tagErtrag!!, 1e-9)
        assertEquals(81.0, l.tagAutarkie!!, 1e-9)
    }
}
