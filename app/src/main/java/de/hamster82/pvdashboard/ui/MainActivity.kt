package de.hamster82.pvdashboard.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PvTheme { AppInhalt() } }
    }
}

private data class Reiter(val titel: String, val icon: ImageVector)

private val REITER = listOf(
    Reiter("Heute", Icons.Filled.Home),
    Reiter("Woche", Icons.Filled.DateRange),
    Reiter("Monate", Icons.AutoMirrored.Filled.List),
    Reiter("Einstellungen", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInhalt(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val test by vm.testErgebnis.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(settings.benachrichtigungen) {
        if (settings.benachrichtigungen && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(
        containerColor = Farbe.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("PV Dashboard", fontWeight = FontWeight.Bold)
                        val d = state.data
                        Text(
                            if (d != null) "Stand ${d.zeit.format(DateTimeFormatter.ofPattern("HH:mm"))}" else "",
                            fontSize = 12.sp, color = Farbe.Muted,
                        )
                    }
                },
                actions = { IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Farbe.Bg, titleContentColor = Farbe.Text, actionIconContentColor = Farbe.Text),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Farbe.Card) {
                REITER.forEachIndexed { i, r ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(r.icon, contentDescription = r.titel) }, label = { Text(r.titel, fontSize = 11.sp) })
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.laedt && state.data != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            state.fehler?.let { if (state.data != null) Klein("Aktualisierung fehlgeschlagen: $it", color = Farbe.Warn, modifier = Modifier.padding(horizontal = 16.dp)) }
            val d = state.data
            when {
                tab == 3 -> SettingsScreen(settings, test, onTest = { vm.testePlenticore(it) }, onSave = { vm.save(it) })
                d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.fehler != null) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text(state.fehler ?: "", color = Farbe.Warn, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.refresh() }) { Text("Erneut versuchen") }
                    } else CircularProgressIndicator()
                }
                tab == 0 -> HeuteScreen(d, settings)
                tab == 1 -> BilanzScreen(d.woche, d.live, jahr = false)
                else -> BilanzScreen(d.monat, d.live, jahr = true)
            }
        }
    }
}
