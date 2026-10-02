package de.hamster82.pvdashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hamster82.pvdashboard.data.Geraet
import de.hamster82.pvdashboard.data.Http
import de.hamster82.pvdashboard.data.Settings
import de.hamster82.pvdashboard.data.kurz
import de.hamster82.pvdashboard.data.parseNum
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun SettingsScreen(start: Settings, testErgebnis: String?, onTest: (Settings) -> Unit, onSave: (Settings) -> Unit) {
    var f by remember(start) { mutableStateOf(start) }
    var geaendert by remember(start) { mutableStateOf(false) }
    fun upd(n: Settings) { f = n; geaendert = true }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { onSave(f); geaendert = false }, enabled = geaendert, modifier = Modifier.fillMaxWidth()) {
            Text(if (geaendert) "Speichern und neu berechnen" else "Gespeichert")
        }

        // Standort
        Karte {
            Titel("Standort")
            var suche by remember { mutableStateOf("") }
            var meldung by remember { mutableStateOf<String?>(null) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Feld("Ort suchen", suche, { suche = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        meldung = try {
                            val o = withContext(Dispatchers.IO) {
                                Http.get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=de&name=" + URLEncoder.encode(suche, "UTF-8")) as JSONObject
                            }
                            val r = o.optJSONArray("results")?.optJSONObject(0)
                            if (r == null) "Nichts gefunden" else {
                                upd(f.copy(ortName = r.getString("name"), lat = r.getDouble("latitude"), lon = r.getDouble("longitude")))
                                "Gefunden: ${r.getString("name")} (${r.optString("admin1")})"
                            }
                        } catch (e: Exception) { "Fehler: ${e.kurz()}" }
                    }
                }) { Text("Suchen") }
            }
            meldung?.let { Klein(it) }
            Feld("Ortsname", f.ortName, { upd(f.copy(ortName = it)) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZahlFeld("Breite", f.lat, { upd(f.copy(lat = it)) }, Modifier.weight(1f))
                ZahlFeld("Länge", f.lon, { upd(f.copy(lon = it)) }, Modifier.weight(1f))
            }
        }

        // Anlage
        Karte {
            Titel("PV-Anlage")
            Klein("Ausrichtung: −90 = Osten, 0 = Süden, 90 = Westen. kWp 0 schaltet eine Fläche ab.")
            f.flaechen.forEachIndexed { i, fl ->
                Spacer(Modifier.height(6.dp))
                Text(fl.name, fontSize = 15.sp, color = Farbe.Text)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZahlFeld("kWp", fl.kwp, { v -> upd(f.copy(flaechen = f.flaechen.toMutableList().also { it[i] = fl.copy(kwp = v) })) }, Modifier.weight(1f))
                    ZahlFeld("Neigung °", fl.neigung, { v -> upd(f.copy(flaechen = f.flaechen.toMutableList().also { it[i] = fl.copy(neigung = v) })) }, Modifier.weight(1f))
                    ZahlFeld("Richtung °", fl.azimut, { v -> upd(f.copy(flaechen = f.flaechen.toMutableList().also { it[i] = fl.copy(azimut = v) })) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZahlFeld("Wechselrichter max. kW", f.wrMaxKw, { upd(f.copy(wrMaxKw = it)) }, Modifier.weight(1f))
                ZahlFeld("Akku kWh", f.akkuKwh, { upd(f.copy(akkuKwh = it)) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZahlFeld("Grundlast kW", f.grundlastKw, { upd(f.copy(grundlastKw = it)) }, Modifier.weight(1f))
                ZahlFeld("Jahresverbrauch kWh", f.jahresverbrauchKwh, { upd(f.copy(jahresverbrauchKwh = it)) }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Feld("Start frühestens", f.fruehesterStart, { upd(f.copy(fruehesterStart = it)) }, Modifier.weight(1f))
                Feld("Start spätestens", f.spaetesterStart, { upd(f.copy(spaetesterStart = it)) }, Modifier.weight(1f))
            }
        }

        // Geräte
        Karte {
            Titel("Geräte")
            Klein("Reihenfolge = Priorität. Heizphase = hohe Leistung am Programmanfang (Wasser aufheizen).")
            f.geraete.forEachIndexed { i, g ->
                fun set(n: Geraet) = upd(f.copy(geraete = f.geraete.toMutableList().also { it[i] = n }))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Punkt(Color(g.farbe), 12); Spacer(Modifier.width(8.dp))
                    Feld("Name", g.name, { set(g.copy(name = it)) }, Modifier.weight(1f))
                    TextButton(onClick = { upd(f.copy(geraete = f.geraete.filterIndexed { k, _ -> k != i })) }) { Text("Entfernen", color = Farbe.Warn) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZahlFeld("Dauer min", g.dauerMin.toDouble(), { set(g.copy(dauerMin = it.toInt().coerceAtLeast(15))) }, Modifier.weight(1f))
                    ZahlFeld("Energie kWh", g.energieKwh, { set(g.copy(energieKwh = it)) }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZahlFeld("Heizleistung kW", g.heizKw, { set(g.copy(heizKw = it)) }, Modifier.weight(1f))
                    ZahlFeld("Heizdauer min", g.heizMin.toDouble(), { set(g.copy(heizMin = it.toInt().coerceAtLeast(0))) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                val n = f.geraete.size
                upd(f.copy(geraete = f.geraete + Geraet("geraet${System.currentTimeMillis()}", "Neues Gerät", 120, 1.0, 0.0, 0,
                    Settings.FARBEN[n % Settings.FARBEN.size])))
            }) { Text("Gerät hinzufügen") }
        }

        // Wechselrichter
        Karte {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Titel("Kostal Plenticore"); Spacer(Modifier.weight(1f))
                Switch(checked = f.plenticoreAktiv, onCheckedChange = { upd(f.copy(plenticoreAktiv = it)) })
            }
            Klein("Im Heimnetz verbindet sich die App direkt mit dem Wechselrichter – keine Portfreigabe nötig. Die Adresse für unterwegs ist optional (MyFRITZ!-Adresse mit Port aus der Fritzbox-Freigabe).")
            Feld("Adresse im Heimnetz (z. B. 192.168.178.40)", f.plenticoreLokal, { upd(f.copy(plenticoreLokal = it.trim())) })
            Feld("Adresse unterwegs (optional, z. B. xxx.myfritz.net:48443)", f.plenticoreExtern, { upd(f.copy(plenticoreExtern = it.trim())) })
            Feld("Passwort Anlagenbetreiber", f.plenticorePasswort, { upd(f.copy(plenticorePasswort = it)) }, passwort = true)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onTest(f) }) { Text("Verbindung testen") }
                Spacer(Modifier.width(8.dp))
                if (f.zertifikatPin.isNotBlank()) TextButton(onClick = { upd(f.copy(zertifikatPin = "")) }) { Text("Zertifikat vergessen") }
            }
            testErgebnis?.let { Klein(it, color = if (it.startsWith("Fehler")) Farbe.Warn else Farbe.Good) }
            Klein(
                if (f.zertifikatPin.isBlank()) "Das Zertifikat des Wechselrichters wird bei der ersten erfolgreichen Verbindung gemerkt und danach geprüft."
                else "Gemerktes Zertifikat: ${f.zertifikatPin.take(23)}…",
            )
        }

        // Benachrichtigungen
        Karte {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Titel("Benachrichtigungen")
                    Klein("Meldung, sobald das empfohlene Startfenster eines Geräts beginnt.")
                }
                Switch(checked = f.benachrichtigungen, onCheckedChange = { upd(f.copy(benachrichtigungen = it)) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Feld(label: String, wert: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), passwort: Boolean = false) {
    OutlinedTextField(
        value = wert, onValueChange = onChange, label = { Text(label, fontSize = 12.sp) }, singleLine = true, modifier = modifier,
        visualTransformation = if (passwort) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (passwort) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
    )
}

@Composable
private fun ZahlFeld(label: String, wert: Double, onValue: (Double) -> Unit, modifier: Modifier = Modifier) {
    fun format(v: Double) = if (v == Math.floor(v) && kotlin.math.abs(v) < 1e6) v.toLong().toString() else v.toString().replace('.', ',')
    var text by remember { mutableStateOf(format(wert)) }
    // Wert wurde von außen geändert (z. B. Ortssuche) -> Feld nachziehen
    LaunchedEffect(wert) { if (parseNum(text) != wert) text = format(wert) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; parseNum(t)?.let(onValue) },
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        isError = parseNum(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
