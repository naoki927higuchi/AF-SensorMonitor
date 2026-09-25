package com.af.sensormonitor.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Saved display settings. [order] is the user's order (empty = default order); [hidden] the sensors switched
 * off. Keys of sensors that are not present right now are kept, so they come back where they were.
 */
data class DisplaySettings(val order: List<String> = emptyList(), val hidden: Set<String> = emptySet())

object DisplayArrangement {
    /**
     * Present sensors in display order: the saved order first, then sensors never seen before in their default
     * order (at the end, so the user's arrangement is not disturbed).
     */
    fun arrange(defaultKeys: List<String>, savedOrder: List<String>): List<String> {
        if (savedOrder.isEmpty()) return defaultKeys
        val present = defaultKeys.toHashSet()
        val saved = savedOrder.filter { it in present }
        val savedSet = saved.toHashSet()
        return saved + defaultKeys.filter { it !in savedSet }
    }

    /** The order to save after the user arranged [presentOrder]: absent keys keep following their predecessors. */
    fun orderToSave(presentOrder: List<String>, previousOrder: List<String>): List<String> {
        val present = presentOrder.toHashSet()
        val absent = previousOrder.filter { it !in present }
        return presentOrder + absent
    }
}

private val Context.displayStore by preferencesDataStore(name = "display")

class DisplaySettingsStore(private val context: Context) {
    private val formatKey = intPreferencesKey("format")
    private val orderKey = stringPreferencesKey("order")
    private val hiddenKey = stringSetPreferencesKey("hidden")

    val settings: Flow<DisplaySettings> = context.displayStore.data.map { p ->
        DisplaySettings(
            order = p[orderKey]?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList(),
            hidden = p[hiddenKey] ?: emptySet(),
        )
    }

    suspend fun save(settings: DisplaySettings) {
        context.displayStore.edit { p ->
            p[formatKey] = FORMAT_VERSION
            if (settings.order.isEmpty()) p.remove(orderKey) else p[orderKey] = settings.order.joinToString("\n")
            p[hiddenKey] = settings.hidden
        }
    }

    companion object {
        const val FORMAT_VERSION = 1
    }
}
