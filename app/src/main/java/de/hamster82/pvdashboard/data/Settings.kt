package de.hamster82.pvdashboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Eine Modulfläche. azimut: -90 = Osten, 0 = Süden, 90 = Westen */
data class Flaeche(val name: String, val kwp: Double, val neigung: Double, val azimut: Double)

/**
 * Ein Hauptverbraucher. Das Lastprofil wird vereinfacht beschrieben:
 * eine Heizphase am Anfang (heizKw für heizMin Minuten), danach der Rest der Energie gleichmäßig.
 */
data class Geraet(
    val id: String,
    val name: String,
    val dauerMin: Int,
    val energieKwh: Double,
    val heizKw: Double,
    val heizMin: Int,
    val farbe: Long,
) {
    /** Lastprofil als Liste (Minuten, kW) */
    fun profil(): List<Pair<Int, Double>> {
        val dauer = dauerMin.coerceAtLeast(15)
        val heiz = heizMin.coerceIn(0, dauer)
        val heizEnergie = heizKw * heiz / 60.0
        val restMin = dauer - heiz
        val out = ArrayList<Pair<Int, Double>>()
        if (heiz > 0) out.add(heiz to heizKw)
        if (restMin > 0) out.add(restMin to ((energieKwh - heizEnergie).coerceAtLeast(0.0) / (restMin / 60.0)))
        return out
    }
}

data class Settings(
    val ortName: String = "Osterhofen",
    val lat: Double = 48.7010,
    val lon: Double = 13.0200,
    val flaechen: List<Flaeche> = listOf(
        Flaeche("Ost", 4.8, 30.0, -90.0),
        Flaeche("West", 4.8, 30.0, 90.0),
    ),
    val wrMaxKw: Double = 8.5,
    val performanceRatio: Double = 0.86,
    val tempKoeff: Double = -0.0037,
    val akkuKwh: Double = 7.1,
    val grundlastKw: Double = 0.35,
    val jahresverbrauchKwh: Double = 4500.0,
    val fruehesterStart: String = "07:00",
    val spaetesterStart: String = "19:00",
    val geraete: List<Geraet> = STANDARD_GERAETE,
    val plenticoreAktiv: Boolean = false,
    val plenticoreLokal: String = "",
    val plenticoreExtern: String = "",
    val plenticorePasswort: String = "",
    val zertifikatPin: String = "",
    val benachrichtigungen: Boolean = true,
) {
    companion object {
        val FARBEN = listOf(0xFF4CC38AL, 0xFF5AA9E6L, 0xFFC58AF9L, 0xFFE8875BL, 0xFFF2B134L, 0xFF6FD3D3L)
        val STANDARD_GERAETE = listOf(
            Geraet("waschmaschine", "Waschmaschine", 150, 1.0, 2.0, 20, FARBEN[0]),
            Geraet("spuelmaschine", "Spülmaschine", 180, 1.2, 1.8, 20, FARBEN[1]),
            Geraet("trockner", "Trockner", 150, 1.6, 0.65, 0, FARBEN[2]),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("ortName", ortName); put("lat", lat); put("lon", lon)
        put("flaechen", JSONArray().apply {
            flaechen.forEach { put(JSONObject().put("name", it.name).put("kwp", it.kwp).put("neigung", it.neigung).put("azimut", it.azimut)) }
        })
        put("wrMaxKw", wrMaxKw); put("performanceRatio", performanceRatio); put("tempKoeff", tempKoeff)
        put("akkuKwh", akkuKwh); put("grundlastKw", grundlastKw); put("jahresverbrauchKwh", jahresverbrauchKwh)
        put("fruehesterStart", fruehesterStart); put("spaetesterStart", spaetesterStart)
        put("geraete", JSONArray().apply {
            geraete.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("dauerMin", it.dauerMin).put("energieKwh", it.energieKwh)
                    .put("heizKw", it.heizKw).put("heizMin", it.heizMin).put("farbe", it.farbe))
            }
        })
        put("plenticoreAktiv", plenticoreAktiv); put("plenticoreLokal", plenticoreLokal); put("plenticoreExtern", plenticoreExtern)
        put("plenticorePasswort", plenticorePasswort); put("zertifikatPin", zertifikatPin); put("benachrichtigungen", benachrichtigungen)
    }
}

fun settingsFromJson(o: JSONObject): Settings {
    val d = Settings()
    val fl = o.optJSONArray("flaechen")?.let { a ->
        (0 until a.length()).map { i ->
            val f = a.getJSONObject(i)
            Flaeche(f.optString("name", "Fläche ${i + 1}"), f.optDouble("kwp", 0.0), f.optDouble("neigung", 30.0), f.optDouble("azimut", 0.0))
        }
    } ?: d.flaechen
    val ge = o.optJSONArray("geraete")?.let { a ->
        (0 until a.length()).map { i ->
            val g = a.getJSONObject(i)
            Geraet(
                g.optString("id", "geraet$i"), g.optString("name", "Gerät"), g.optInt("dauerMin", 120),
                g.optDouble("energieKwh", 1.0), g.optDouble("heizKw", 0.0), g.optInt("heizMin", 0),
                g.optLong("farbe", Settings.FARBEN[i % Settings.FARBEN.size]),
            )
        }
    } ?: d.geraete
    return Settings(
        ortName = o.optString("ortName", d.ortName),
        lat = o.optDouble("lat", d.lat),
        lon = o.optDouble("lon", d.lon),
        flaechen = fl,
        wrMaxKw = o.optDouble("wrMaxKw", d.wrMaxKw),
        performanceRatio = o.optDouble("performanceRatio", d.performanceRatio),
        tempKoeff = o.optDouble("tempKoeff", d.tempKoeff),
        akkuKwh = o.optDouble("akkuKwh", d.akkuKwh),
        grundlastKw = o.optDouble("grundlastKw", d.grundlastKw),
        jahresverbrauchKwh = o.optDouble("jahresverbrauchKwh", d.jahresverbrauchKwh),
        fruehesterStart = o.optString("fruehesterStart", d.fruehesterStart),
        spaetesterStart = o.optString("spaetesterStart", d.spaetesterStart),
        geraete = ge,
        plenticoreAktiv = o.optBoolean("plenticoreAktiv", false),
        plenticoreLokal = o.optString("plenticoreLokal", ""),
        plenticoreExtern = o.optString("plenticoreExtern", ""),
        plenticorePasswort = o.optString("plenticorePasswort", ""),
        zertifikatPin = o.optString("zertifikatPin", ""),
        benachrichtigungen = o.optBoolean("benachrichtigungen", true),
    )
}

object SettingsStore {
    private const val PREFS = "einstellungen"
    private val lock = Any()

    fun load(ctx: Context): Settings = synchronized(lock) {
        val txt = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", null)
            ?: return Settings()
        runCatching { settingsFromJson(JSONObject(txt)) }.getOrDefault(Settings())
    }

    fun save(ctx: Context, s: Settings) = synchronized(lock) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("json", s.toJson().toString()).commit()
    }

    /** Merkt sich den Zertifikats-Fingerabdruck des Wechselrichters beim ersten erfolgreichen Login. */
    fun savePin(ctx: Context, pin: String) = synchronized(lock) {
        val s = load(ctx)
        if (s.zertifikatPin.isBlank()) save(ctx, s.copy(zertifikatPin = pin))
    }
}
