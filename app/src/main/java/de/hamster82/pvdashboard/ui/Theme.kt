package de.hamster82.pvdashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Farbe {
    val Bg = Color(0xFF11151B)
    val Card = Color(0xFF1B2129)
    val Text = Color(0xFFEEF1F4)
    val Muted = Color(0xFF98A2AD)
    val Sun = Color(0xFFF2B134)
    val Cons = Color(0xFF5AA9E6)
    val Good = Color(0xFF4CC38A)
    val Warn = Color(0xFFE8875B)
    val Night = Color(0xFF3A424D)
}

@Composable
fun PvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Farbe.Bg, surface = Farbe.Card, surfaceVariant = Farbe.Card, surfaceContainer = Farbe.Card,
            primary = Farbe.Sun, onPrimary = Farbe.Bg, secondary = Farbe.Cons, onBackground = Farbe.Text,
            onSurface = Farbe.Text, onSurfaceVariant = Farbe.Muted, secondaryContainer = Color(0xFF3A3220),
            onSecondaryContainer = Farbe.Sun,
        ),
        content = content,
    )
}

@Composable
fun Karte(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Farbe.Card).padding(14.dp),
        content = content,
    )
}

@Composable
fun Titel(text: String) = Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Farbe.Text)

@Composable
fun Klein(text: String, color: Color = Farbe.Muted, modifier: Modifier = Modifier) =
    Text(text, fontSize = 13.sp, color = color, modifier = modifier)

@Composable
fun Zeile(label: String, wert: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = Farbe.Muted, modifier = Modifier.weight(1f))
        Text(wert, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Farbe.Text)
    }
}

@Composable
fun Punkt(color: Color, size: Int = 10) =
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(size.dp)).background(color))

@Composable
fun Legende() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Farbe.Sun))
        Spacer(Modifier.width(5.dp)); Klein("Ertrag")
        Spacer(Modifier.width(14.dp))
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Farbe.Cons))
        Spacer(Modifier.width(5.dp)); Klein("Verbrauch")
    }
}
