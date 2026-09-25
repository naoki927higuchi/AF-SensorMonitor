package com.af.sensormonitor.sensor

import android.hardware.Sensor
import kotlin.math.ln
import kotlin.math.max

enum class Status {
    /** Not subscribed (hidden, or the app is in the background). */
    OFF,
    /** Subscribed, no value yet (on-change sensors stay here until something changes). */
    WAITING,
    LIVE,
    /** One-shot sensor waiting for its trigger. */
    ARMED,
    /** Needs a runtime permission that has not been granted (or is not requested in this version). */
    LOCKED,
    /** The framework refused the subscription. */
    FAILED,
}

/** What the screens read from a sensor; a copy, safe to keep. */
class EntrySnapshot(
    val status: Status,
    val values: FloatArray,
    val count: Int,
    val events: Long,
    val accuracy: Int,
    val rateHz: Float,
    val level: Float,
)

/**
 * One sensor as returned by the framework, with its live state. Sensor callbacks write from the sensor
 * thread; screens read copies through [snapshot] and [history]. All state is guarded by [lock].
 */
class SensorEntry(
    val sensor: Sensor,
    val key: String,
    val info: TypeInfo,
    val shortName: String,
    /** Position in the default display order. */
    val defaultIndex: Int,
    /** Position in the framework's list (Sensor.TYPE_ALL order, dynamic sensors last). */
    val osIndex: Int,
) {
    private val lock = Any()
    private val history = History()
    private val indicator = Indicator()
    private val detector = ActivityDetector(reactionFloor(), sensor.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE)
    private var latest = FloatArray(0)
    private var latestCount = 0
    private var transformed = FloatArray(0)
    private var prevQuat = FloatArray(4)
    private var status = Status.OFF
    private var events = 0L
    private var accuracy = -1
    private var lastEventT = 0L
    private var intervalEma = 0f
    private var maxValuesLength = 0

    val isOneShot: Boolean get() = sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT

    /** Events are reactions by themselves (no meaningful value to compare). */
    private val eventDriven: Boolean
        get() = info.kind == ValueKind.EVENT || isOneShot || sensor.reportingMode == Sensor.REPORTING_MODE_SPECIAL_TRIGGER

    fun setStatus(s: Status) = synchronized(lock) {
        if (s == Status.OFF || s == Status.LOCKED || s == Status.FAILED) indicator.clear()
        if (s != status && (s == Status.WAITING || s == Status.ARMED)) detector.reset()
        status = if ((s == Status.WAITING) && events > 0) Status.LIVE else s
    }

    fun status(): Status = synchronized(lock) { status }

    fun onEvent(values: FloatArray, tNanos: Long) = synchronized(lock) {
        val n = values.size
        if (latest.size < n) latest = FloatArray(n)
        values.copyInto(latest, 0, 0, n)
        latestCount = n
        maxValuesLength = max(maxValuesLength, n)
        if (lastEventT != 0L) {
            val dt = (tNanos - lastEventT).toFloat()
            intervalEma = if (intervalEma == 0f) dt else intervalEma + (dt - intervalEma) * 0.1f
        }
        val previousEvents = events
        lastEventT = tNanos
        events++
        if (status == Status.WAITING) status = Status.LIVE
        history.add(tNanos, values, n)
        val level = when {
            eventDriven -> 1f
            info.kind == ValueKind.PROXIMITY -> {
                val changed = previousEvents > 0 && transformed.isNotEmpty() && transformed[0] != nearFlag(values)
                if (transformed.isEmpty()) transformed = FloatArray(1)
                transformed[0] = nearFlag(values)
                if (changed) 1f else 0f
            }
            info.kind == ValueKind.COUNTER -> {
                val changed = previousEvents > 0 && transformed.isNotEmpty() && transformed[0] != values[0]
                if (transformed.isEmpty()) transformed = FloatArray(1)
                transformed[0] = values[0]
                if (changed) 1f else 0f
            }
            else -> {
                val m = detectorInput(values, n)
                detector.update(transformed, m, tNanos)
            }
        }
        indicator.hit(level, tNanos)
    }

    fun onTrigger(values: FloatArray, tNanos: Long) {
        onEvent(values, tNanos)
        synchronized(lock) { status = Status.ARMED }
    }

    fun onAccuracy(value: Int) = synchronized(lock) { accuracy = value }

    fun snapshot(nowNanos: Long): EntrySnapshot = synchronized(lock) {
        EntrySnapshot(
            status = status,
            values = latest.copyOf(latestCount),
            count = latestCount,
            events = events,
            accuracy = accuracy,
            rateHz = if (intervalEma > 0f) 1e9f / intervalEma else 0f,
            level = if (status == Status.LIVE || status == Status.ARMED) indicator.level(nowNanos) else 0f,
        )
    }

    /** Current indicator level only (cheap enough to call every frame). */
    fun level(nowNanos: Long): Float = synchronized(lock) {
        if (status == Status.LIVE || status == Status.ARMED) indicator.level(nowNanos) else 0f
    }

    fun history(fromNanos: Long): HistorySnapshot = synchronized(lock) { history.snapshot(fromNanos) }

    fun maxValuesLength(): Int = synchronized(lock) { maxValuesLength }

    private fun nearFlag(values: FloatArray) =
        if (values.isNotEmpty() && ValueFormat.isNear(values[0], sensor.maximumRange)) 1f else 0f

    /** Values in the space the detector compares; returns the channel count. */
    private fun detectorInput(values: FloatArray, n: Int): Int {
        val m = when (info.kind) {
            ValueKind.VECTOR3 -> minOf(n, 3)
            ValueKind.ROTATION -> minOf(n, 4)
            ValueKind.SCALAR, ValueKind.LOG_SCALAR -> minOf(n, 1)
            else -> minOf(n, History.MAX_CHANNELS)
        }
        if (transformed.size < m) transformed = FloatArray(m)
        when (info.kind) {
            ValueKind.LOG_SCALAR -> transformed[0] = ln(1f + max(0f, values[0]))
            ValueKind.ANGLES -> for (i in 0 until m) {
                // Unwrap against the previous angle so 359 -> 0 is a change of 1 degree.
                var v = values[i]
                val p = transformed[i]
                while (v - p > 180f) v -= 360f
                while (v - p < -180f) v += 360f
                transformed[i] = v
            }
            ValueKind.ROTATION -> {
                // q and -q are the same rotation; keep the sign continuous.
                if (m == 4) {
                    var dot = 0f
                    for (i in 0 until 4) dot += values[i] * prevQuat[i]
                    val s = if (dot < 0f) -1f else 1f
                    for (i in 0 until 4) {
                        transformed[i] = values[i] * s
                        prevQuat[i] = transformed[i]
                    }
                } else for (i in 0 until m) transformed[i] = values[i]
            }
            else -> for (i in 0 until m) transformed[i] = values[i]
        }
        return m
    }

    /**
     * For unknown types: a continuous sensor must move by several steps of its resolution (a slowly drifting
     * value flickering by one step is not a reaction); for an on-change sensor any change of one step counts,
     * because it reports only when something changed.
     */
    private fun reactionFloor(): Float {
        if (info.reactionFloor > 0f) return info.reactionFloor
        val onChange = sensor.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE
        val resolution = sensor.resolution
        if (resolution > 0f && resolution.isFinite()) return if (onChange) resolution / 5f else resolution
        val range = sensor.maximumRange
        if (range > 0f && range.isFinite()) return range * 1e-5f
        return 1e-6f
    }
}
