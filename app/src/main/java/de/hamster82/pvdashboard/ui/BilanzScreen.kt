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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hamster82.pvdashboard.data.Bilanz
import de.hamster82.pvdashboard.data.Live
import de.hamster82.pvdashboard.data.fmt
import de.hamster82.pvdashboard.data.fmtKwh

@Composable
fun BilanzScreen(b: Bilanz, live: Live?, jahr: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bil = b.ertrag - b.verbrauch
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kachel("Ertrag", fmtKwh(b.ertrag), Farbe.Sun, Modifier.weight(1f))
            Kachel("Verbrauch", fmtKwh(b.verbrauch), Farbe.Cons, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kachel(if (bil >= 0) "Überschuss" else "Fehlmenge", fmtKwh(kotlin.math.abs(bil)), if (bil >= 0) Farbe.Good else Farbe.Warn, Modifier.weight(1f))
            if (b.autarkie != null) Kachel("Autarkie", "${fmt(b.autarkie * 100, 0)} %", Farbe.Text, Modifier.weight(1f))
            else Kachel("Deckung rechnerisch", if (b.verbrauch > 0) "${fmt(b.ertrag / b.verbrauch * 100, 0)} %" else "–", Farbe.Text, Modifier.weight(1f))
        }

        Karte {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Titel(b.titel); Spacer(Modifier.weight(1f)); Legende()
            }
            Spacer(Modifier.height(10.dp))
            BalkenChart(b)
            Spacer(Modifier.height(6.dp))
            Klein(
                if (b.geschaetzt) "Blasse Balken sind geschätzt (Wettermodell bzw. Jahresverbrauch). Mit Wechselrichter-Anbindung werden sie nach und nach durch echte Werte ersetzt."
                else "Echte Werte vom Wechselrichter.",
            )
        }

        Karte {
            Row { Klein("Zeitraum", modifier = Modifier.weight(1.2f)); Klein("Ertrag", modifier = Modifier.weight(1f)); Klein("Verbrauch", modifier = Modifier.weight(1f)); Klein("Bilanz", modifier = Modifier.weight(1f)) }
            HorizontalDivider(color = Farbe.Night, modifier = Modifier.padding(vertical = 4.dp))
            b.balken.forEach { r ->
                val diff = if (r.ertrag != null && r.verbrauch != null) r.ertrag - r.verbrauch else null
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(r.label + if (r.geschaetzt) " *" else "", fontSize = 14.sp, color = Farbe.Text, modifier = Modifier.weight(1.2f))
                    Text(fmt(r.ertrag), fontSize = 14.sp, color = Farbe.Sun, modifier = Modifier.weight(1f))
                    Text(fmt(r.verbrauch), fontSize = 14.sp, color = Farbe.Cons, modifier = Modifier.weight(1f))
                    Text(diff?.let { (if (it >= 0) "+" else "") + fmt(it) } ?: "–", fontSize = 14.sp,
                        color = if ((diff ?: 0.0) >= 0) Farbe.Good else Farbe.Warn, modifier = Modifier.weight(1f))
                }
            }
            Klein("Werte in kWh · * = geschätzt", modifier = Modifier.padding(top = 4.dp))
        }

        if (jahr && live?.jahrErtrag != null) {
            Karte {
                Titel("Dieses Jahr (Wechselrichter)")
                Spacer(Modifier.height(6.dp))
                Zeile("Ertrag", fmtKwh(live.jahrErtrag))
                live.jahrHaus?.let { Zeile("Hausverbrauch", fmtKwh(it)) }
                live.jahrAutarkie?.let { Zeile("Autarkie", "${fmt(it, 0)} %") }
                live.monatAutarkie?.let { Zeile("Autarkie dieser Monat", "${fmt(it, 0)} %") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Kachel(label: String, wert: String, farbe: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Farbe.Card).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Klein(label)
        Text(wert, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = farbe, maxLines = 1)
    }
}

@Composable
private fun BalkenChart(b: Bilanz) {
    val max = b.balken.maxOf { maxOf(it.ertrag ?: 0.0, it.verbrauch ?: 0.0) }.coerceAtLeast(1.0)
    Row(Modifier.fillMaxWidth().height(230.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        b.balken.forEach { r ->
            val a = if (r.geschaetzt) 0.5f else 1f
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(r.ertrag?.let { fmt(it, if (it >= 100) 0 else 1) } ?: "k. D.", fontSize = 10.sp, color = Farbe.Sun, maxLines = 1,
                    textAlign = TextAlign.Center)
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally)) {
                    Box(Modifier.weight(1f).fillMaxHeight(((r.ertrag ?: 0.0) / max).toFloat().coerceIn(0.01f, 1f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Farbe.Sun.copy(alpha = a)))
                    Box(Modifier.weight(1f).fillMaxHeight(((r.verbrauch ?: 0.0) / max).toFloat().coerceIn(0.01f, 1f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Farbe.Cons.copy(alpha = a)))
                }
                Spacer(Modifier.height(4.dp))
                Text(r.label, fontSize = 12.sp, color = Farbe.Muted, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.width(0.dp))
}
