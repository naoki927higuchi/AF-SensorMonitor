package com.af.sensormonitor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.af.sensormonitor.export.InventoryCollector
import com.af.sensormonitor.export.InventoryFormat
import com.af.sensormonitor.sensor.SensorEntry
import com.af.sensormonitor.sensor.SensorHub
import com.af.sensormonitor.settings.DisplayArrangement
import com.af.sensormonitor.settings.DisplaySettings
import com.af.sensormonitor.settings.DisplaySettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

/** All sensors in the user's order, and which of them are shown. */
data class Arrangement(val all: List<SensorEntry> = emptyList(), val hidden: Set<String> = emptySet(), val loaded: Boolean = false) {
    val visible: List<SensorEntry> get() = all.filter { it.key !in hidden }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val hub = SensorHub(app)
    private val store = DisplaySettingsStore(app)
    private val saved = MutableStateFlow<DisplaySettings?>(null)

    val arrangement: StateFlow<Arrangement> = combine(hub.entries, saved) { entries, settings ->
        if (settings == null) return@combine Arrangement(entries, emptySet(), loaded = false)
        val byKey = entries.associateBy { it.key }
        val order = DisplayArrangement.arrange(entries.map { it.key }, settings.order)
        Arrangement(order.mapNotNull { byKey[it] }, settings.hidden, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Arrangement())

    init {
        viewModelScope.launch {
            store.settings.collect { saved.value = it }
        }
        viewModelScope.launch {
            arrangement.collect { a ->
                if (a.loaded) hub.setWanted(a.visible.map { it.key }.toSet())
            }
        }
    }

    private fun update(change: (DisplaySettings) -> DisplaySettings) {
        val current = saved.value ?: return
        val next = change(current)
        saved.value = next
        viewModelScope.launch { store.save(next) }
    }

    fun setVisible(key: String, visible: Boolean) = update { s ->
        s.copy(hidden = if (visible) s.hidden - key else s.hidden + key)
    }

    fun showAll() = update { it.copy(hidden = emptySet()) }

    fun resetOrder() = update { it.copy(order = emptyList()) }

    fun setOrder(keys: List<String>) = update { s ->
        s.copy(order = DisplayArrangement.orderToSave(keys, s.order))
    }

    /** Writes the sensor inventory (JSON or CSV) to a document the user picked. */
    fun export(uri: Uri, csv: Boolean, done: (Boolean) -> Unit) {
        val app = getApplication<Application>()
        val a = arrangement.value
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val inv = InventoryCollector.collect(app, a.all, a.hidden, OffsetDateTime.now())
                    val text = if (csv) InventoryFormat.csv(inv) else InventoryFormat.json(inv)
                    app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            done(ok)
        }
    }

    fun exportFileName(ext: String): String {
        val app = getApplication<Application>()
        val now = OffsetDateTime.now()
        val device = InventoryCollector.collect(app, emptyList(), emptySet(), now).device
        return InventoryFormat.fileName(device, InventoryCollector.stamp(now), ext)
    }

    override fun onCleared() {
        hub.release()
    }
}
