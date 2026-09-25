package com.af.sensormonitor

import com.af.sensormonitor.export.DeviceInfo
import com.af.sensormonitor.export.Inventory
import com.af.sensormonitor.export.InventoryFormat
import com.af.sensormonitor.export.JsonWriter
import com.af.sensormonitor.export.SensorRecord
import com.af.sensormonitor.sensor.ActivityDetector
import com.af.sensormonitor.sensor.AngleSeries
import com.af.sensormonitor.sensor.HistorySnapshot
import com.af.sensormonitor.sensor.History
import com.af.sensormonitor.sensor.Indicator
import com.af.sensormonitor.sensor.SensorCatalog
import com.af.sensormonitor.sensor.SensorIdentity
import com.af.sensormonitor.sensor.SensorKeys
import com.af.sensormonitor.sensor.ValueFormat
import com.af.sensormonitor.settings.DisplayArrangement
import com.af.sensormonitor.ui.GaugeLayoutCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.sin

class ActivityDetectorTest {
    private val stepNs = 20_000_000L // 50 Hz

    /** Feeds [seconds] of samples from [signal] and returns how many samples were judged a reaction. */
    private fun run(d: ActivityDetector, fromS: Double, seconds: Double, signal: (Double) -> FloatArray, t0: Long = 0L): Int {
        var hits = 0
        val n = (seconds * 50).toInt()
        val start = (fromS * 1e9).toLong() + t0
        for (i in 0 until n) {
            val t = start + i * stepNs
            val v = signal(t / 1e9)
            if (d.update(v, v.size, t) > 0f) hits++
        }
        return hits
    }

    @Test
    fun sensorNoiseAloneDoesNotReact() {
        val rnd = Random(1)
        val d = ActivityDetector(floor = 0.06f, decayOverGaps = false)
        val noise = { _: Double -> FloatArray(3) { (rnd.nextGaussian() * 0.03).toFloat() + if (it == 2) 9.8f else 0f } }
        val hits = run(d, 0.0, 60.0, noise)
        assertTrue("false reactions: $hits", hits <= 3)
    }

    @Test
    fun handHeldTremorIsLearnedThenShakeReacts() {
        val rnd = Random(2)
        val d = ActivityDetector(floor = 0.06f, decayOverGaps = false)
        val tremor = { _: Double -> FloatArray(3) { (rnd.nextGaussian() * 0.15).toFloat() } }
        run(d, 0.0, 5.0, tremor)
        val quiet = run(d, 5.0, 20.0, tremor)
        assertTrue("tremor reactions: $quiet", quiet <= 10)
        val shake = { s: Double -> FloatArray(3) { (8 * sin(s * 2 * Math.PI * 4) + rnd.nextGaussian() * 0.15).toFloat() } }
        val hits = run(d, 25.0, 2.0, shake)
        assertTrue("shake reactions: $hits", hits > 50)
    }

    @Test
    fun steadyShakingStaysVisibleForSeveralSeconds() {
        val rnd = Random(3)
        val d = ActivityDetector(floor = 0.06f, decayOverGaps = false)
        run(d, 0.0, 3.0, { FloatArray(3) { (rnd.nextGaussian() * 0.03).toFloat() } })
        val shake = { s: Double -> FloatArray(3) { (5 * sin(s * 2 * Math.PI * 3)).toFloat() } }
        run(d, 3.0, 4.0, shake)
        val after4s = run(d, 7.0, 1.0, shake)
        assertTrue("reactions 4 s into shaking: $after4s", after4s > 10)
    }

    @Test
    fun highRateSensorReactsLikeFiftyHertz() {
        // The same motion (3 m/s² at 2 Hz) over sensor noise, delivered at 500 Hz and at 50 Hz.
        fun hits(rateHz: Int): Int {
            val rnd = Random(5)
            val d = ActivityDetector(floor = 0.06f, decayOverGaps = false)
            val step = 1_000_000_000L / rateHz
            var count = 0
            for (i in 0 until rateHz * 12) {
                val t = i * step
                val s = t / 1e9
                val motion = if (s >= 8.0) 3 * sin(s * 2 * Math.PI * 2) else 0.0
                val v = floatArrayOf((motion + rnd.nextGaussian() * 0.02).toFloat())
                if (d.update(v, 1, t) > 0f && s >= 8.0) count++
            }
            return count
        }
        val slow = hits(50)
        val fast = hits(500)
        assertTrue("50 Hz reactions: $slow", slow > 20)
        assertTrue("500 Hz reactions: $fast", fast > 20)
    }

    @Test
    fun warmUpNeverReacts() {
        val d = ActivityDetector(floor = 0.01f, decayOverGaps = false)
        val hits = run(d, 0.0, 0.9, { s -> floatArrayOf((s * 100).toFloat()) })
        assertEquals(0, hits)
    }

    @Test
    fun quantizedDriftBelowFloorDoesNotReact() {
        // A slowly drifting value that flickers by one resolution step (floor = one step for continuous unknowns).
        val d = ActivityDetector(floor = 0.01f, decayOverGaps = false)
        val rnd = Random(4)
        val hits = run(d, 0.0, 60.0, { _ -> floatArrayOf(32.27f + 0.01f * (if (rnd.nextInt(20) == 0) 1 else 0)) })
        assertEquals(0, hits)
    }

    @Test
    fun levelMapping() {
        assertEquals(0f, ActivityDetector.levelFor(4.9f), 0f)
        assertEquals(ActivityDetector.MIN_LEVEL, ActivityDetector.levelFor(ActivityDetector.THRESHOLD), 1e-6f)
        assertEquals(1f, ActivityDetector.levelFor(1000f), 0f)
        assertEquals(0f, ActivityDetector.levelFor(Float.NaN), 0f)
    }

    @Test
    fun indicatorHoldsThenFades() {
        val i = Indicator()
        i.hit(1f, 0L)
        assertEquals(1f, i.level(100_000_000L), 0f)
        assertTrue(i.level(500_000_000L) in 0.3f..0.9f)
        assertEquals(0f, i.level(3_000_000_000L), 0f)
        // A weaker hit during the fade does not cut the fade short.
        i.hit(1f, 0L)
        i.hit(0.4f, 200_000_000L)
        assertTrue(i.level(200_000_000L) > 0.8f)
    }
}

class NamesAndKeysTest {
    @Test
    fun shortNamesFromStringType() {
        assertEquals("LIGHT_IR", SensorCatalog.deriveShortName("com.example.sensor.light_ir", "x"))
        assertEquals("PRESSUR_TEMP", SensorCatalog.deriveShortName("com.example.sensor.pressure_temp", "x"))
        assertEquals("CAMERA_VSYNC", SensorCatalog.deriveShortName("com.example.sensor.camera_vsync", "x"))
        assertEquals("LED_COV_EVEN", SensorCatalog.deriveShortName("vendor.led_cover_event", "x"))
        assertEquals("MOTIO_SENSOR", SensorCatalog.deriveShortName("", "Motion Sensor"))
        assertEquals("SENSOR", SensorCatalog.deriveShortName("", "  "))
        for (s in listOf("a.b.very_long_name_with_many_words_inside", "x.abcdefghijklmnopqrstuvwxyz")) {
            assertTrue(SensorCatalog.deriveShortName(s, "").length <= SensorCatalog.SHORT_NAME_CHARS)
        }
    }

    @Test
    fun unknownTypesAreNotDropped() {
        val info = SensorCatalog.info(123456, "com.example.sensor.thing", "Thing")
        assertEquals("THING", info.shortName)
        assertEquals(Int.MAX_VALUE, info.rank)
    }

    @Test
    fun keysAreUniqueAndStable() {
        val a = SensorIdentity(65541, "com.x.camera_vsync", "Camera VSync", "X", false)
        val keys = SensorKeys.keys(listOf(a, a, a))
        assertEquals(3, keys.toSet().size)
        assertEquals(keys, SensorKeys.keys(listOf(a, a, a)))
        assertTrue(keys.none { it.contains('\n') })
    }

    @Test
    fun duplicateShortNamesGetSuffix() {
        val names = listOf("CAMERA_VSYNC", "CAMERA_VSYNC", "ACCEL", "ACCEL")
        val groups = listOf("CAMERA_VSYNC", "CAMERA_VSYNC", "ACCEL", "ACCEL|W")
        assertEquals(listOf("CAMERA_VSYNC", "CAMERA_VSY#2", "ACCEL", "ACCEL"), SensorKeys.uniqueShortNames(names, groups))
    }
}

class FormatTest {
    @Test
    fun numbersFitTheirField() {
        assertEquals("+9.81", ValueFormat.number(9.806f, 2))
        assertEquals("+0.00", ValueFormat.number(-0.001f, 2))
        assertEquals("-129.0", ValueFormat.number(-129f, 1))
        assertEquals("+123456", ValueFormat.number(123456f, 2))
        assertTrue(ValueFormat.number(9_999_999f, 2).length <= ValueFormat.FIELD_CHARS)
        assertEquals("NaN", ValueFormat.number(Float.NaN, 2))
    }

    @Test
    fun rotationAnglesMatchKnownOrientations() {
        val identity = ValueFormat.rotationAngles(floatArrayOf(0f, 0f, 0f, 1f), 4)
        assertEquals(0f, identity[0], 1e-3f); assertEquals(0f, identity[1], 1e-3f); assertEquals(0f, identity[2], 1e-3f)
        // 90 degrees about Z: azimuth 270 (getOrientation measures clockwise from north).
        val s = Math.sqrt(0.5).toFloat()
        val yaw = ValueFormat.rotationAngles(floatArrayOf(0f, 0f, s, s), 4)
        assertEquals(270f, yaw[0], 1e-2f)
        // W derived when only x, y, z are given.
        val derived = ValueFormat.rotationAngles(floatArrayOf(0f, 0f, s), 3)
        assertEquals(270f, derived[0], 1e-2f)
    }

    @Test
    fun proximity() {
        assertTrue(ValueFormat.isNear(0f, 5f))
        assertFalse(ValueFormat.isNear(5f, 5f))
    }
}

class AngleSeriesTest {
    @Test
    fun angularDistanceWraps() {
        assertEquals(2f, AngleSeries.angularDistance(359f, 1f), 1e-4f)
        assertEquals(180f, AngleSeries.angularDistance(0f, 180f), 1e-4f)
        assertEquals(10f, AngleSeries.angularDistance(-5f, 5f), 1e-4f)
    }

    @Test
    fun rotationHistoryBecomesAzimuthPitchRoll() {
        val s = Math.sqrt(0.5).toFloat()
        val data = HistorySnapshot(longArrayOf(1L, 2L), floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, s, s, 0f), 2, 5)
        val split = AngleSeries.split(SensorCatalog.info(11, "android.sensor.rotation_vector", "r"), data)
        assertEquals(1, split.azimuth.channels)
        assertEquals(0f, split.azimuth.value(0, 0), 1e-3f)
        assertEquals(270f, split.azimuth.value(1, 0), 1e-2f)
        assertEquals(listOf("PITCH", "ROLL"), split.otherLabels)
    }

    @Test
    fun orientationChannelsAreSplit() {
        val data = HistorySnapshot(longArrayOf(1L), floatArrayOf(350f, -10f, 20f), 1, 3)
        val split = AngleSeries.split(SensorCatalog.info(3, "android.sensor.orientation", "o"), data)
        assertEquals(350f, split.azimuth.value(0, 0), 0f)
        assertEquals(2, split.others.channels)
        assertEquals(20f, split.others.value(0, 1), 0f)
    }
}

class HistoryTest {
    @Test
    fun keepsWindowAndSkipsTooDenseSamples() {
        val h = History(capacity = 100)
        for (i in 0 until 300) h.add(i * 5_000_000L, floatArrayOf(i.toFloat()), 1) // 200 Hz -> every other kept
        val all = h.snapshot(0L)
        assertEquals(100, all.size)
        assertTrue(all.times.toList().zipWithNext().all { (a, b) -> b > a })
        assertEquals(298f, all.value(all.size - 1, 0), 0f)
        val recent = h.snapshot(1_000_000_000L)
        assertTrue(recent.times.all { it >= 1_000_000_000L })
    }
}

class ArrangementTest {
    @Test
    fun savedOrderFirstNewSensorsAppended() {
        val defaults = listOf("a", "b", "c", "d")
        assertEquals(defaults, DisplayArrangement.arrange(defaults, emptyList()))
        assertEquals(listOf("c", "a", "b", "d"), DisplayArrangement.arrange(defaults, listOf("c", "x", "a", "b")))
    }

    @Test
    fun absentKeysAreKept() {
        assertEquals(listOf("b", "a", "x"), DisplayArrangement.orderToSave(listOf("b", "a"), listOf("a", "x", "b")))
    }
}

class GaugeLayoutTest {
    @Test
    fun fortyFiveRowsFitAPhoneWithoutScrolling() {
        val l = GaugeLayoutCalc.compute(45, 384f, 714.7f)
        assertEquals(1, l.columns)
        assertFalse(l.scroll)
        assertTrue(l.fontSize >= 11f)
    }

    @Test
    fun tooManyRowsScrollInsteadOfShrinking() {
        val l = GaugeLayoutCalc.compute(80, 384f, 714f)
        assertTrue(l.scroll)
        assertEquals(GaugeLayoutCalc.MIN_ROW, l.rowHeight, 1e-3f)
    }

    @Test
    fun wideScreensUseColumns() {
        val l = GaugeLayoutCalc.compute(45, 1200f, 700f)
        assertTrue(l.columns >= 2)
        assertFalse(l.scroll)
    }

    @Test
    fun largerSystemFontScrollsSooner() {
        assertTrue(GaugeLayoutCalc.compute(45, 384f, 714.7f, fontScale = 1.3f).scroll)
    }
}

class ExportTest {
    private fun inventory() = Inventory(
        appVersion = "1.0.0",
        exportedAt = "2026-09-25T12:00:00+09:00",
        device = DeviceInfo("Maker", "Brand", "Model \"X\"", "dev", "prod", "hw", null, null, "16", 36, "2026-09-01",
            "fp", 1080, 2340, 450),
        sensors = listOf(
            SensorRecord(0, "1|a|b|c|N#1", "ACCEL", "Accelerometer", "Accel, 3-axis", "V", 1, 1, "android.sensor.accelerometer",
                0.001f, 78.4f, 0.1f, 5000, 1000000, "CONTINUOUS", false, false, 1, 0, 0, false, 0, true, "LIVE", 10, 3, 3),
        ),
    )

    @Test
    fun jsonIsEscapedAndComplete() {
        val json = InventoryFormat.json(inventory())
        assertTrue(json.contains("\"model\": \"Model \\\"X\\\"\""))
        assertTrue(json.contains("\"sensorCount\": 1"))
        assertTrue(json.contains("\"socModel\": null"))
        assertEquals(json.count { it == '{' }, json.count { it == '}' })
        assertEquals(json.count { it == '[' }, json.count { it == ']' })
        assertEquals("\"a\\u0001\"", JsonWriter.quote("a\u0001"))
    }

    @Test
    fun csvQuotesAndRepeatsDeviceColumns() {
        val csv = InventoryFormat.csv(inventory())
        assertTrue(csv.startsWith("﻿manufacturer,model,"))
        val row = csv.lines()[1]
        assertTrue(row.startsWith("Maker,\"Model \"\"X\"\"\",16,36,"))
        assertTrue(row.contains("\"Accel, 3-axis\""))
    }

    @Test
    fun fileNameIsSafe() {
        val d = inventory().device
        assertEquals("AF-SensorMonitor_Maker_Model-X_20260925-120000.json", InventoryFormat.fileName(d, "20260925-120000", "json"))
    }
}
