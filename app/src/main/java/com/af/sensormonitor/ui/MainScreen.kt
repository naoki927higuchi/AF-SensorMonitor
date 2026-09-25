package com.af.sensormonitor.ui

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.af.sensormonitor.Arrangement as SensorArrangement
import com.af.sensormonitor.BuildConfig
import com.af.sensormonitor.sensor.EntrySnapshot
import com.af.sensormonitor.sensor.SensorCatalog
import com.af.sensormonitor.sensor.SensorEntry
import com.af.sensormonitor.sensor.Status
import com.af.sensormonitor.sensor.ValueFormat
import com.af.sensormonitor.sensor.ValueKind

/** Text refresh interval: numbers changing faster than this cannot be read anyway. */
private const val TEXT_REFRESH_NS = 80_000_000L
/** The mascot looks surprised when at least this many sensors react at once. */
private const val EXCITED_COUNT = 4
private val HEADER_HEIGHT = 32.dp

@Composable
fun MainScreen(
    arrangement: SensorArrangement,
    held: Boolean,
    onToggleHold: () -> Unit,
    onOpen: (SensorEntry) -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    val visible = arrangement.visible
    val frame = remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    var snaps by remember { mutableStateOf<Map<String, EntrySnapshot>>(emptyMap()) }
    val currentVisible by rememberUpdatedState(visible)
    val currentHeld by rememberUpdatedState(held)

    LaunchedEffect(Unit) {
        var lastText = 0L
        while (true) {
            withFrameNanos { }
            val now = SystemClock.elapsedRealtimeNanos()
            if (!currentHeld) {
                frame.longValue = now
                if (now - lastText >= TEXT_REFRESH_NS) {
                    lastText = now
                    snaps = currentVisible.associate { it.key to it.snapshot(now) }
                }
            }
        }
    }

    val excited = snaps.values.count { it.level > 0.5f } >= EXCITED_COUNT
    Column(Modifier.fillMaxSize().background(p.background)) {
        Header(arrangement, held, excited, onToggleHold, onSettings)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val fontScale = LocalDensity.current.fontScale
            val layout = GaugeLayoutCalc.compute(visible.size, maxWidth.value, maxHeight.value, fontScale)
            if (BuildConfig.DEBUG) {
                LaunchedEffect(layout, visible.size) {
                    Log.i("AFSensorMonitor", "layout count=${visible.size} area=${maxWidth.value}x${maxHeight.value}dp " +
                        "fontScale=$fontScale -> $layout")
                }
            }
            if (visible.isEmpty()) {
                Text(
                    if (arrangement.all.isEmpty()) "NO SENSORS" else "ALL HIDDEN",
                    Modifier.align(Alignment.Center), color = p.dim, fontFamily = FontFamily.Monospace,
                )
            } else {
                val columns = visible.chunked(layout.rows)
                val body = @Composable {
                    Row(Modifier.fillMaxWidth()) {
                        columns.forEachIndexed { c, list ->
                            Column(Modifier.weight(1f)) {
                                list.forEachIndexed { r, entry ->
                                    GaugeRow(
                                        entry = entry,
                                        snap = snaps[entry.key],
                                        layout = layout,
                                        stripe = (r % 2 == 1),
                                        frame = frame,
                                        held = held,
                                        onClick = { onOpen(entry) },
                                    )
                                }
                            }
                            if (c < columns.size - 1) Spacer(Modifier.width(1.dp).height(1.dp))
                        }
                        repeat(layout.columns - columns.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (layout.scroll) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { body() }
                else body()
            }
        }
    }
}

@Composable
private fun Header(
    arrangement: SensorArrangement,
    held: Boolean,
    excited: Boolean,
    onToggleHold: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().height(HEADER_HEIGHT).padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MascotBadge(20.dp, excited)
        Text("AF-SensorMonitor", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            "${Build.MODEL}  ${arrangement.visible.size}/${arrangement.all.size}",
            color = p.dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.height(HEADER_HEIGHT).clickable(onClick = onToggleHold).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (held) "❚❚ HOLD" else "● LIVE",
                color = if (held) p.warn else p.active, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box(Modifier.height(HEADER_HEIGHT).clickable(onClick = onSettings).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            GearIcon(20.dp, p.dim, p.background)
        }
    }
}

@Composable
private fun GaugeRow(
    entry: SensorEntry,
    snap: EntrySnapshot?,
    layout: GaugeLayout,
    stripe: Boolean,
    frame: LongState,
    held: Boolean,
    onClick: () -> Unit,
) {
    val p = LocalPalette.current
    val indicatorWidth: Dp = GaugeLayoutCalc.indicatorWidth(layout.rowHeight).dp
    val fontSize = with(LocalDensity.current) { layout.fontSize.dp.toSp() }
    val status = snap?.status ?: Status.OFF
    Box(
        Modifier
            .fillMaxWidth()
            .height(layout.rowHeight.dp)
            .background(if (stripe) p.stripe else p.background)
            .drawBehind {
                // Read the frame clock here so the highlight redraws every frame without recomposing the text.
                val level = if (held) snap?.level ?: 0f else entry.level(frame.longValue)
                if (level > 0f) drawRect(p.active.copy(alpha = 0.16f * level))
                val w = indicatorWidth.toPx()
                drawIndicator(status, level, p, Offset(w * 0.6f, size.height / 2f), size.height * 0.2f)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            rowText(entry, snap, p),
            Modifier.padding(start = indicatorWidth + 2.dp, end = 4.dp),
            fontSize = fontSize, lineHeight = fontSize, fontFamily = FontFamily.Monospace,
            maxLines = 1, softWrap = false, color = p.text,
        )
    }
}

private fun rowText(entry: SensorEntry, snap: EntrySnapshot?, p: Palette): AnnotatedString = buildAnnotatedString {
    append(entry.shortName.padEnd(SensorCatalog.SHORT_NAME_CHARS).take(SensorCatalog.SHORT_NAME_CHARS))
    withStyle(SpanStyle(color = p.dim)) { append(if (entry.sensor.isWakeUpSensor) " w" else "  ") }
    append(' ')
    val status = snap?.status ?: Status.OFF
    val fields: List<String>
    var extra = ""
    var statusWord: String? = null
    when {
        snap == null || status == Status.OFF -> fields = emptyList()
        status == Status.LOCKED -> { fields = emptyList(); statusWord = "LOCKED" }
        status == Status.FAILED -> { fields = emptyList(); statusWord = "N/A" }
        status == Status.ARMED && snap.events == 0L -> { fields = emptyList(); statusWord = "ARMED" }
        snap.count == 0 && entry.info.kind != ValueKind.EVENT -> { fields = emptyList(); statusWord = "---" }
        snap.events == 0L -> { fields = emptyList(); statusWord = "---" }
        else -> {
            val mv = ValueFormat.mainValues(entry.info, snap.values, snap.count, entry.sensor.maximumRange, snap.events)
            fields = mv.fields
            extra = mv.extra
        }
    }
    val w = ValueFormat.FIELD_CHARS
    if (statusWord != null) {
        withStyle(SpanStyle(color = if (status == Status.LOCKED) p.warn else p.dim)) {
            append(statusWord.padStart(w * 3 + 2))
        }
    } else {
        val slots = List(3 - fields.size) { "" } + fields
        slots.forEachIndexed { i, f ->
            if (i > 0) append(' ')
            append(f.padStart(w))
        }
    }
    append(' ')
    withStyle(SpanStyle(color = p.dim, fontSize = 0.85f.em())) {
        append(entry.info.unit)
        if (extra.isNotEmpty()) append(if (entry.info.unit.isEmpty()) extra else " $extra")
    }
}

private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)
