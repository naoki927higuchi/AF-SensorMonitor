package com.af.sensormonitor

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.af.sensormonitor.export.InventoryCollector
import com.af.sensormonitor.export.InventoryFormat
import com.af.sensormonitor.ui.AppTheme
import com.af.sensormonitor.ui.DetailScreen
import com.af.sensormonitor.ui.LocalPalette
import com.af.sensormonitor.ui.MainScreen
import com.af.sensormonitor.ui.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.OffsetDateTime

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // The user shakes and turns the phone without touching it; keep the screen on while the app is visible.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            AppTheme {
                val arrangement by vm.arrangement.collectAsStateWithLifecycle()
                // "main", "settings" or "detail:<key>"
                var screen by rememberSaveable { mutableStateOf("main") }
                var held by rememberSaveable { mutableStateOf(false) }
                val p = LocalPalette.current
                Box(Modifier.fillMaxSize().background(p.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val detailKey = screen.removePrefix("detail:").takeIf { screen.startsWith("detail:") }
                    val detailEntry = detailKey?.let { k -> arrangement.all.firstOrNull { it.key == k } }
                    when {
                        screen == "settings" -> {
                            BackHandler { screen = "main" }
                            SettingsScreen(
                                arrangement = arrangement,
                                onBack = { screen = "main" },
                                onSetVisible = vm::setVisible,
                                onShowAll = vm::showAll,
                                onResetOrder = vm::resetOrder,
                                onSetOrder = vm::setOrder,
                                exportFileName = vm::exportFileName,
                                onExport = vm::export,
                            )
                        }
                        detailEntry != null -> {
                            BackHandler { screen = "main" }
                            DetailScreen(detailEntry, onBack = { screen = "main" }, onPermissionResult = { vm.hub.refreshPermissions() })
                        }
                        else -> MainScreen(
                            arrangement = arrangement,
                            held = held,
                            onToggleHold = { held = !held },
                            onOpen = { screen = "detail:" + it.key },
                            onSettings = { screen = "settings" },
                        )
                    }
                }
            }
        }
        if (BuildConfig.DEBUG) writeDebugInventory()
    }

    override fun onStart() {
        super.onStart()
        vm.hub.start()
    }

    override fun onStop() {
        vm.hub.stop()
        super.onStop()
    }

    /** Debug builds only: after a few seconds, write the inventory to the app's files for development checks. */
    private fun writeDebugInventory() {
        lifecycleScope.launch {
            delay(8000)
            val a = vm.arrangement.value
            val inv = InventoryCollector.collect(this@MainActivity, a.all, a.hidden, OffsetDateTime.now())
            File(filesDir, "inventory.json").writeText(InventoryFormat.json(inv))
            File(filesDir, "inventory.csv").writeText(InventoryFormat.csv(inv))
            Log.i("AFSensorMonitor", "debug inventory written: ${inv.sensors.size} sensors")
        }
    }
}
