package com.af.sensormonitor.ui

import android.Manifest
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.af.sensormonitor.export.InventoryCollector
import com.af.sensormonitor.sensor.Access
import com.af.sensormonitor.sensor.AngleSeries
import com.af.sensormonitor.sensor.EntrySnapshot
import com.af.sensormonitor.sensor.History
import com.af.sensormonitor.sensor.HistorySnapshot
import com.af.sensormonitor.sensor.SensorCatalog
import com.af.sensormonitor.sensor.SensorEntry
import com.af.sensormonitor.sensor.Status
import com.af.sensormonitor.sensor.ValueFormat
import com.af.sensormonitor.sensor.ValueKind
import java.util.Locale

/** Sensors whose permission was already asked for in this session (asked once, when first opened). */
private val askedPermission = HashSet<String>()

@Composable
fun DetailScreen(entry: SensorEntry, onBack: () -> Unit, onPermissionResult: () -> Unit) {
    val p = LocalPalette.current
    var paused by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var data by remember { mutableStateOf(HistorySnapshot.EMPTY) }
    var snap by remember { mutableStateOf(entry.snapshot(now)) }

    LaunchedEffect(entry, paused) {
        while (!paused) {
            withFrameNanos { }
            val t = SystemClock.elapsedRealtimeNanos()
            now = t
            data = entry.history(t - History.HISTORY_NS)
            snap = entry.snapshot(t)
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onPermissionResult() }
    val needsActivityPermission = entry.info.access == Access.ACTIVITY_RECOGNITION && Build.VERSION.SDK_INT >= 29 &&
        snap.status == Status.LOCKED
    LaunchedEffect(entry.key, needsActivityPermission) {
        if (needsActivityPermission && askedPermission.add(entry.key)) {
            launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    Column(Modifier.fillMaxSize().background(p.background)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                BackIcon(22.dp, p.text)
            }
            Column(Modifier.weight(1f)) {
                Text(entry.shortName, color = p.text, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace, maxLines = 1)
                Text(entry.info.label, color = p.dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ActionButton(if (paused) "RESUME" else "PAUSE", if (paused) p.warn else p.text) { paused = !paused }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            StatusLine(entry, snap, paused)
            if (needsActivityPermission) {
                Spacer(Modifier.height(6.dp))
                ActionButton("ALLOW", p.active) { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
            }
            Spacer(Modifier.height(6.dp))
            ValueList(entry, snap)
            Spacer(Modifier.height(8.dp))
            val style = when {
                entry.info.kind == ValueKind.EVENT || entry.isOneShot -> GraphStyle.MARKS
                entry.sensor.reportingMode == android.hardware.Sensor.REPORTING_MODE_ON_CHANGE -> GraphStyle.STEP
                else -> GraphStyle.LINE
            }
            if (AngleSeries.isAngular(entry.info.kind)) {
                // Azimuth on a PPI-style scope (no 0/360 jump); the other angles on the time graph.
                val split = remember(data) { AngleSeries.split(entry.info, data) }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AzimuthScope(
                        data = split.azimuth,
                        endNanos = now,
                        windowNanos = History.HISTORY_NS,
                        northUp = entry.sensor.type != android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR,
                        modifier = Modifier.size(280.dp),
                    )
                }
                if (split.others.channels > 0) {
                    Spacer(Modifier.height(8.dp))
                    SensorGraph(
                        data = split.others,
                        endNanos = now,
                        windowNanos = History.HISTORY_NS,
                        channels = split.others.channels,
                        style = style,
                        decimals = 1,
                        modifier = Modifier.fillMaxWidth().height(160.dp).border(1.dp, p.faint),
                        wrapDegrees = true,
                        colorOffset = 1,
                    )
                }
            } else {
                SensorGraph(
                    data = data,
                    endNanos = now,
                    windowNanos = History.HISTORY_NS,
                    channels = graphChannels(entry, data.channels),
                    style = style,
                    decimals = entry.info.decimals + 1,
                    modifier = Modifier.fillMaxWidth().height(220.dp).border(1.dp, p.faint),
                )
            }
            Spacer(Modifier.height(12.dp))
            MetaTable(entry, snap)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

private fun graphChannels(entry: SensorEntry, available: Int): Int = when (entry.info.kind) {
    ValueKind.VECTOR3 -> minOf(available, 3)
    ValueKind.ROTATION -> minOf(available, 4)
    ValueKind.SCALAR, ValueKind.LOG_SCALAR, ValueKind.PROXIMITY, ValueKind.COUNTER -> minOf(available, 1)
    else -> minOf(available, 8)
}

@Composable
private fun StatusLine(entry: SensorEntry, snap: EntrySnapshot, paused: Boolean) {
    val p = LocalPalette.current
    val parts = buildList {
        add(if (paused) "PAUSED" else snap.status.name)
        if (snap.rateHz > 0f) add(String.format(Locale.ROOT, "%.1f Hz", snap.rateHz))
        add("${snap.events} EVENTS")
        accuracyName(snap.accuracy)?.let { add("ACC $it") }
    }
    Text(parts.joinToString("  "), color = if (snap.status == Status.LOCKED) p.warn else p.dim,
        fontSize = 12.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun ValueList(entry: SensorEntry, snap: EntrySnapshot) {
    val p = LocalPalette.current
    val rows = ArrayList<Triple<Color?, String, String>>()
    if (entry.info.kind == ValueKind.ROTATION && snap.count >= 3) {
        val a = ValueFormat.rotationAngles(snap.values, snap.count)
        listOf("AZIMUTH", "PITCH", "ROLL").forEachIndexed { i, l ->
            rows += Triple(p.channels[i], l, ValueFormat.number(a[i], 2, 10, false) + " °")
        }
    }
    if (entry.info.kind == ValueKind.PROXIMITY && snap.count > 0) {
        rows += Triple(null, "STATE", if (ValueFormat.isNear(snap.values[0], entry.sensor.maximumRange)) "NEAR" else "FAR")
    }
    if (entry.info.kind == ValueKind.EVENT || entry.isOneShot) rows += Triple(null, "EVENTS", snap.events.toString())
    // Rotation vectors are drawn as the derived angles above, not as raw quaternion channels.
    val plotted = if (entry.info.kind == ValueKind.ROTATION) 0 else graphChannels(entry, snap.count)
    for (i in 0 until snap.count) {
        val color = if (i < plotted) p.channels[i % p.channels.size] else null
        val unit = if (entry.info.kind == ValueKind.ROTATION || entry.info.unit.isEmpty()) "" else " " + entry.info.unit
        rows += Triple(color, SensorCatalog.channelLabel(entry.info, i), ValueFormat.number(snap.values[i], entry.info.decimals + 2, 12, plus = entry.info.kind != ValueKind.ANGLES) + unit)
    }
    if (rows.isEmpty()) {
        Text("NO DATA", color = p.dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        for ((color, label, value) in rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color ?: Color.Transparent))
                Spacer(Modifier.width(6.dp))
                Text(label.padEnd(12), color = p.dim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                Text(value, color = p.text, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MetaTable(entry: SensorEntry, snap: EntrySnapshot) {
    val p = LocalPalette.current
    val s = entry.sensor
    val rows = listOf(
        "NAME" to (s.name ?: ""),
        "VENDOR" to (s.vendor ?: ""),
        "TYPE" to "${s.type}" + (SensorCatalog.known(s.type)?.let { "  ${it.label}" } ?: "  (unknown)"),
        "STRING TYPE" to (s.stringType ?: ""),
        "VERSION" to s.version.toString(),
        "RESOLUTION" to s.resolution.toString(),
        "MAX RANGE" to s.maximumRange.toString(),
        "POWER" to "${s.power} mA",
        "MIN DELAY" to "${s.minDelay} µs",
        "MAX DELAY" to "${s.maxDelay} µs",
        "REPORTING" to InventoryCollector.reportingModeName(s.reportingMode),
        "WAKE-UP" to yesNo(s.isWakeUpSensor),
        "DYNAMIC" to yesNo(s.isDynamicSensor),
        "ID" to s.id.toString(),
        "FIFO RESERVED" to s.fifoReservedEventCount.toString(),
        "FIFO MAX" to s.fifoMaxEventCount.toString(),
        "ADDITIONAL INFO" to yesNo(s.isAdditionalInfoSupported),
        "DIRECT REPORT" to directRateName(s.highestDirectReportRateLevel),
        "VALUES LENGTH" to entry.maxValuesLength().toString(),
        "ACCESS" to when (entry.info.access) {
            Access.OPEN -> "OPEN"
            Access.ACTIVITY_RECOGNITION -> "ACTIVITY RECOGNITION"
            Access.BODY -> "BODY SENSORS (NOT REQUESTED)"
        },
    )
    // Wide enough for the longest label ("ADDITIONAL INFO") at the system font size.
    val labelWidth = 128.dp * LocalDensity.current.fontScale
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for ((k, v) in rows) {
            Row {
                Text(k, color = p.dim, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, softWrap = false,
                    modifier = Modifier.width(labelWidth))
                Text(v, color = p.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun yesNo(b: Boolean) = if (b) "YES" else "NO"

private fun accuracyName(a: Int): String? = when (a) {
    SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
    else -> null
}

private fun directRateName(level: Int) = when (level) {
    0 -> "NONE"
    1 -> "NORMAL"
    2 -> "FAST"
    3 -> "VERY FAST"
    else -> level.toString()
}
