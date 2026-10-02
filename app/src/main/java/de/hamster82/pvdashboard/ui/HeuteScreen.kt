package de.hamster82.pvdashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hamster82.pvdashboard.data.DashboardData
import de.hamster82.pvdashboard.data.Empfehlung
import de.hamster82.pvdashboard.data.Planner
import de.hamster82.pvdashboard.data.Settings
import de.hamster82.pvdashboard.data.TagPlan
import de.hamster82.pvdashboard.data.fmt
import de.hamster82.pvdashboard.data.qToTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HeuteScreen(d: DashboardData, s: Settings) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Kopf: Prognose
        Karte {
            Klein(d.zeit.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)) + " · " + s.ortName)
            Spacer(Modifier.height(4.dp))
            Text("Solarstrom heute", fontSize = 16.sp, color = Farbe.Text)
            Text("${fmt(d.heuteKwh)} kWh", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Farbe.Sun)
            if (d.heuteWetter.isNotBlank()) Klein(d.heuteWetter)
            Spacer(Modifier.height(6.dp))
            Text("Morgen ${fmt(d.morgenKwh)} kWh" + (if (d.morgenWetter.isNotBlank()) " · ${d.morgenWetter}" else ""),
                fontSize = 15.sp, color = Farbe.Text)
        }

        // Live bzw. Prognose jetzt
        Karte {
            val l = d.live
            if (l != null && l.pvKw != null) {
                Zeile("Sonne jetzt", "${fmt(l.pvKw)} kW")
                l.hausKw?.let { Zeile("Haus jetzt", "${fmt(it)} kW") }
                l.akkuSoc?.let { soc ->
                    val richtung = l.akkuKw?.let { p -> if (p < -0.05) " · lädt" else if (p > 0.05) " · entlädt" else "" } ?: ""
                    Zeile("Akku", "${fmt(soc, 0)} %$richtung")
                }
                l.netzKw?.let { g -> Zeile(if (g >= 0) "Netzbezug" else "Einspeisung", "${fmt(kotlin.math.abs(g))} kW") }
                l.tagErtrag?.let { Zeile("Ertrag bisher", "${fmt(it)} kWh") }
                l.tagAutarkie?.let { Zeile("Autarkie heute", "${fmt(it, 0)} %") }
            } else {
                val pvJetzt = d.ueberschussJetzt + s.grundlastKw
                Zeile("Sonne jetzt (Prognose)", "${fmt(pvJetzt.coerceAtLeast(0.0))} kW")
                Zeile("Überschuss heute noch", "${fmt(d.planHeute?.ueberschussKwh)} kWh")
            }
            Spacer(Modifier.height(6.dp))
            val u = d.ueberschussJetzt
            val (text, farbe) = when {
                u >= 1.5 -> "Jetzt ${fmt(u)} kW Überschuss – guter Moment zum Einschalten" to Farbe.Good
                u >= 0.5 -> "Jetzt ${fmt(u)} kW Überschuss – reicht für ein Gerät" to Farbe.Sun
                else -> "Jetzt kaum Überschuss – lieber warten" to Farbe.Warn
            }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = farbe)
        }

        // Empfehlungen
        Karte {
            Titel("Beste Startzeiten")
            Spacer(Modifier.height(6.dp))
            d.empfehlungen.forEach { EmpfehlungZeile(it) }
            Spacer(Modifier.height(4.dp))
            Klein(d.hinweis)
        }

        // Stundenprognose
        var zeigeMorgen by rememberSaveable { mutableStateOf(false) }
        Karte {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Titel("Sonnenstrom-Prognose")
                Spacer(Modifier.weight(1f))
                FilterChip(selected = !zeigeMorgen, onClick = { zeigeMorgen = false }, label = { Text("Heute") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = zeigeMorgen, onClick = { zeigeMorgen = true }, label = { Text("Morgen") })
            }
            val kw = if (zeigeMorgen) d.morgenKw else d.heuteKw
            val plan = if (zeigeMorgen) d.planMorgen else d.planHeute
            val ids = d.empfehlungen.filter { it.morgen == zeigeMorgen }.map { it.geraet.id }.toSet()
            if (kw != null) StundenChart(kw, plan, ids, if (zeigeMorgen) -1 else d.zeit.hour)
            Klein("Grün = empfohlene Laufzeit der Geräte · Balken = erwartete PV-Leistung je Stunde")
        }

        Klein(
            when {
                d.live != null -> "Live-Daten: Kostal Plenticore (${d.liveAdresse})"
                d.liveFehler != null -> "Wechselrichter nicht erreichbar: ${d.liveFehler} – Werte geschätzt"
                else -> "Wettermodell Open-Meteo · Wechselrichter nicht angebunden"
            } + (if (d.kalibrierung != 1.0) " · Prognose kalibriert ×${fmt(d.kalibrierung, 2)}" else ""),
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun EmpfehlungZeile(e: Empfehlung) {
    val p = e.plan
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Punkt(Color(e.geraet.farbe), 12)
            Spacer(Modifier.width(10.dp))
            Text(e.geraet.name, fontSize = 16.sp, color = Farbe.Text, modifier = Modifier.weight(1f))
            val wann = when {
                p == null -> "kein Zeitfenster"
                p.vonQ == p.bisQ -> "${if (e.morgen) "Morgen" else "Heute"} ab ${qToTime(p.startQ)}"
                else -> "${if (e.morgen) "Morgen" else "Heute"} ${qToTime(p.vonQ)}–${qToTime(p.bisQ)}"
            }
            Text(wann, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Farbe.Text)
        }
        if (p != null) {
            Row(Modifier.padding(start = 22.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { p.coverage.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (p.coverage >= 0.6) Farbe.Good else if (p.coverage >= 0.3) Farbe.Sun else Farbe.Warn,
                    trackColor = Farbe.Night,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.width(10.dp))
                Klein("≈ ${fmt(p.coverage * 100, 0)} % Sonne · Start ideal ${qToTime(p.startQ)}")
            }
            Klein(Planner.bewertung(p.coverage).replaceFirstChar { it.uppercase() }, modifier = Modifier.padding(start = 22.dp))
        }
    }
}

@Composable
private fun StundenChart(kw: DoubleArray, plan: TagPlan?, ids: Set<String>, jetztStunde: Int) {
    val laufStunden = HashSet<Int>()
    plan?.geraete?.forEach { (id, p) ->
        if (p != null && id in ids && p.coverage >= 0.3) for (q in p.startQ until p.startQ + p.durQ) laufStunden.add(q / 4)
    }
    val stunden = (6..20).toList()
    val max = (stunden.maxOf { kw[it] }).coerceAtLeast(1.0)
    Spacer(Modifier.height(8.dp))
    Klein("max. ${fmt(max)} kW")
    Row(Modifier.fillMaxWidth().height(170.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        stunden.forEach { h ->
            val v = kw[h]
            val farbe = when {
                h in laufStunden -> Farbe.Good
                v > 0.35 -> Farbe.Sun
                else -> Farbe.Night
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth(0.8f).fillMaxHeight((v / max).toFloat().coerceIn(0.015f, 1f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(farbe)
                            .alpha(1f),
                    )
                    if (h < jetztStunde) Box(Modifier.fillMaxWidth(0.8f).fillMaxHeight((v / max).toFloat().coerceIn(0.015f, 1f)).background(Farbe.Bg.copy(alpha = 0.6f)))
                }
                Text(if (h % 2 == 0) "$h" else "", fontSize = 11.sp, color = Farbe.Muted, modifier = Modifier.height(16.dp))
            }
        }
    }
}
