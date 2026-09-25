package com.af.sensormonitor.sensor

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/** Up to three right-aligned value fields for the main screen, plus a "+N" note for values not shown. */
data class MainValues(val fields: List<String>, val extra: String = "")

object ValueFormat {
    const val FIELD_CHARS = 7

    /** Fixed-width-friendly number: fewer decimals, then scientific notation, when it would not fit [width]. */
    fun number(v: Float, decimals: Int, width: Int = FIELD_CHARS, plus: Boolean = true): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "+Inf" else "-Inf"
        for (d in decimals downTo 0) {
            val zeroed = if (abs(v) < 0.5f * 10f.pow(-d)) 0f else v
            val s = String.format(Locale.ROOT, if (plus) "%+.${d}f" else "%.${d}f", zeroed)
            if (s.length <= width) return s
        }
        return String.format(Locale.ROOT, "%.1e", v)
    }

    fun isNear(distance: Float, maxRange: Float): Boolean =
        if (maxRange > 0f) distance < maxRange else distance <= 0f

    /** Decimals for a light level: more for dim light, none for bright. */
    fun lightDecimals(lux: Float): Int = when {
        lux < 10f -> 1
        else -> 0
    }

    fun mainValues(info: TypeInfo, values: FloatArray, count: Int, maxRange: Float, events: Long): MainValues {
        if (count <= 0 && info.kind != ValueKind.EVENT) return MainValues(emptyList())
        return when (info.kind) {
            ValueKind.VECTOR3 -> MainValues((0 until minOf(3, count)).map { number(values[it], info.decimals) })
            ValueKind.ANGLES -> MainValues((0 until minOf(3, count)).map { number(values[it], info.decimals, plus = false) })
            ValueKind.ROTATION -> {
                val a = rotationAngles(values, count)
                MainValues(a.map { number(it, info.decimals, plus = false) })
            }
            ValueKind.SCALAR -> MainValues(listOf(number(values[0], info.decimals, plus = false)))
            ValueKind.LOG_SCALAR -> MainValues(listOf(number(values[0], lightDecimals(values[0]), plus = false)))
            ValueKind.PROXIMITY -> MainValues(
                listOf(if (isNear(values[0], maxRange)) "NEAR" else "FAR", number(values[0], info.decimals, plus = false))
            )
            ValueKind.COUNTER -> MainValues(listOf(number(values[0], 0, plus = false)))
            ValueKind.EVENT -> MainValues(listOf("×$events"))
            ValueKind.GENERIC -> MainValues(
                (0 until minOf(3, count)).map { number(values[it], info.decimals) },
                if (count > 3) "+${count - 3}" else "",
            )
        }
    }

    /**
     * Azimuth, pitch and roll in degrees from a rotation-vector quaternion, following
     * SensorManager.getRotationMatrixFromVector + getOrientation (azimuth normalized to 0..360).
     */
    fun rotationAngles(values: FloatArray, count: Int): FloatArray {
        if (count < 3) return FloatArray(0)
        val q1 = values[0]; val q2 = values[1]; val q3 = values[2]
        val q0 = if (count >= 4) values[3] else sqrt(max(0f, 1f - q1 * q1 - q2 * q2 - q3 * q3))
        val sqQ1 = 2 * q1 * q1; val sqQ2 = 2 * q2 * q2; val sqQ3 = 2 * q3 * q3
        val q1q2 = 2 * q1 * q2; val q3q0 = 2 * q3 * q0; val q1q3 = 2 * q1 * q3
        val q2q0 = 2 * q2 * q0; val q2q3 = 2 * q2 * q3; val q1q0 = 2 * q1 * q0
        val r1 = q1q2 - q3q0
        val r4 = 1 - sqQ1 - sqQ3
        val r6 = q1q3 - q2q0
        val r7 = q2q3 + q1q0
        val r8 = 1 - sqQ1 - sqQ2
        var azimuth = Math.toDegrees(atan2(r1, r4).toDouble()).toFloat()
        if (azimuth < 0f) azimuth += 360f
        val pitch = Math.toDegrees(asin((-r7).coerceIn(-1f, 1f)).toDouble()).toFloat()
        val roll = Math.toDegrees(atan2(-r6, r8).toDouble()).toFloat()
        return floatArrayOf(azimuth, pitch, roll)
    }
}
