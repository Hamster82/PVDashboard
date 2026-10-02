package de.hamster82.pvdashboard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.hamster82.pvdashboard.data.Dashboard
import de.hamster82.pvdashboard.data.DashboardData
import de.hamster82.pvdashboard.data.Plenticore
import de.hamster82.pvdashboard.data.Settings
import de.hamster82.pvdashboard.data.SettingsStore
import de.hamster82.pvdashboard.data.fmt
import de.hamster82.pvdashboard.data.kurz
import de.hamster82.pvdashboard.work.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(val data: DashboardData? = null, val laedt: Boolean = false, val fehler: String? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val _settings = MutableStateFlow(SettingsStore.load(app))
    val settings: StateFlow<Settings> = _settings
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state
    private val _test = MutableStateFlow<String?>(null)
    val testErgebnis: StateFlow<String?> = _test
    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = _state.value.copy(laedt = true)
            _settings.value = SettingsStore.load(getApplication())
            try {
                val d = Dashboard.collect(getApplication(), _settings.value)
                _settings.value = SettingsStore.load(getApplication()) // evtl. neu gemerkter Zertifikats-Pin
                _state.value = UiState(d, false, null)
                Notifier.pruefen(getApplication(), _settings.value, d)
            } catch (e: Exception) {
                _state.value = _state.value.copy(laedt = false, fehler = e.kurz())
            }
        }
    }

    fun save(s: Settings) {
        SettingsStore.save(getApplication(), s)
        _settings.value = s
        job?.cancel()
        refresh()
    }

    fun testePlenticore(s: Settings) {
        _test.value = "Verbinde …"
        viewModelScope.launch {
            _test.value = withContext(Dispatchers.IO) {
                try {
                    val r = Plenticore.read(s.copy(plenticoreAktiv = true))
                    val l = r.live
                    "Verbunden über ${r.adresse}\nPV ${fmt(l.pvKw)} kW · Haus ${fmt(l.hausKw)} kW · Akku ${fmt(l.akkuSoc, 0)} %\n" +
                        "Zertifikat: ${r.pin.take(23)}…"
                } catch (e: Exception) {
                    "Fehler: ${e.kurz()}"
                }
            }
        }
    }
}
