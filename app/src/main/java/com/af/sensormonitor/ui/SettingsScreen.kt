package com.af.sensormonitor.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.af.sensormonitor.Arrangement as SensorArrangement
import com.af.sensormonitor.BuildConfig
import com.af.sensormonitor.sensor.SensorEntry
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SettingsScreen(
    arrangement: SensorArrangement,
    onBack: () -> Unit,
    onSetVisible: (String, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onResetOrder: () -> Unit,
    onSetOrder: (List<String>) -> Unit,
    exportFileName: (String) -> String,
    onExport: (Uri, Boolean, (Boolean) -> Unit) -> Unit,
) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var list by remember(arrangement.all) { mutableStateOf(arrangement.all) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    fun exported(ok: Boolean) {
        Toast.makeText(context, if (ok) "EXPORTED" else "EXPORT FAILED", Toast.LENGTH_SHORT).show()
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) onExport(uri, false, ::exported)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onExport(uri, true, ::exported)
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                BackIcon(22.dp, p.text)
            }
            Text("SENSORS", color = p.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Text("${arrangement.visible.size}/${arrangement.all.size}", color = p.dim, fontSize = 12.sp,
                fontFamily = FontFamily.Monospace)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton("ALL", p.text, onShowAll)
            ActionButton("RESET ORDER", p.text, onResetOrder)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("EXPORT", color = p.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            ActionButton("JSON", p.text) { jsonLauncher.launch(exportFileName("json")) }
            ActionButton("CSV", p.text) { csvLauncher.launch(exportFileName("csv")) }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
            items(list, key = { it.key }) { entry ->
                ReorderableItem(reorder, key = entry.key) { dragging ->
                    SettingsRow(
                        entry = entry,
                        visible = entry.key !in arrangement.hidden,
                        dragging = dragging,
                        handle = Modifier.draggableHandle(onDragStopped = { onSetOrder(list.map { it.key }) }),
                        onSetVisible = { onSetVisible(entry.key, it) },
                    )
                }
            }
            item(key = "footer") {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MascotBadge(28.dp, excited = false)
                    Spacer(Modifier.width(8.dp))
                    Text("AF-SensorMonitor ${BuildConfig.VERSION_NAME}", color = p.dim, fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    entry: SensorEntry,
    visible: Boolean,
    dragging: Boolean,
    handle: Modifier,
    onSetVisible: (Boolean) -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(if (dragging) p.stripe else p.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
            Text("☰", color = p.dim, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                entry.shortName + if (entry.sensor.isWakeUpSensor) " w" else "",
                color = if (visible) p.text else p.dim, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, maxLines = 1,
            )
            Text(entry.sensor.name ?: entry.info.label, color = p.dim, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = visible,
            onCheckedChange = onSetVisible,
            modifier = Modifier.scale(0.8f).padding(end = 4.dp),
            colors = SwitchDefaults.colors(checkedTrackColor = p.active),
        )
    }
}
