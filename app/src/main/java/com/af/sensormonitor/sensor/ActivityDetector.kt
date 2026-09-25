package com.af.sensormonitor.sensor

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Self-learning reaction detector for one sensor.
 *
 * For every channel it averages the sample-to-sample change over about a tenth of a second ("activity") and
 * compares it with the level learned while nothing happens ("noise"). The noise level follows activity down
 * quickly but up only very slowly, so it settles on the quiet level (including a hand's tremor) within about a
 * second, while continuous shaking stays visible for many seconds before it becomes the new normal. [floor] is
 * the smallest change that counts at all, so a perfectly quiet or quantized sensor does not react to its last
 * digit. Averaging the activity keeps single noisy samples from looking like a reaction.
 */
class ActivityDetector(
    private val floor: Float,
    /** For on-change sensors: silence between events means no change, so the noise estimate decays over gaps. */
    private val decayOverGaps: Boolean,
) {
    private var prev = FloatArray(0)
    private var activity = FloatArray(0)
    private var noise = FloatArray(0)
    private var lastT = 0L
    private var startT = 0L
    private var started = false

    fun reset() {
        started = false
    }

    /** Returns the reaction level for this sample: 0 = none, otherwise [MIN_LEVEL]..1. */
    fun update(values: FloatArray, count: Int, tNanos: Long): Float {
        if (!started || count != prev.size) {
            prev = values.copyOf(count)
            activity = FloatArray(count)
            noise = FloatArray(count) { floor }
            lastT = tNanos
            startT = tNanos
            started = true
            return 0f
        }
        val dt = max(0L, tNanos - lastT) / 1e9f
        lastT = tNanos
        val aActivity = 1f - exp(-dt / TAU_ACTIVITY_S)
        val aDown = 1f - exp(-dt / TAU_DOWN_S)
        val aUp = 1f - exp(-dt / TAU_UP_S)
        var score = 0f
        for (i in 0 until count) {
            val v = values[i]
            val d = abs(v - prev[i])
            prev[i] = v
            if (d.isNaN() || d.isInfinite()) continue
            if (decayOverGaps) noise[i] *= exp(-dt / TAU_DOWN_S)
            val a = activity[i] + (d - activity[i]) * aActivity
            activity[i] = a
            score = max(score, a / max(noise[i], floor))
            noise[i] += (a - noise[i]) * (if (a < noise[i]) aDown else aUp)
        }
        if (tNanos - startT < WARMUP_NS) return 0f
        return levelFor(score)
    }

    companion object {
        const val TAU_ACTIVITY_S = 0.12f
        const val TAU_DOWN_S = 0.6f
        const val TAU_UP_S = 30f
        const val WARMUP_NS = 1_000_000_000L
        const val THRESHOLD = 5f
        const val MIN_LEVEL = 0.35f

        /** Maps a score (change / noise) to a level: THRESHOLD -> MIN_LEVEL, 8x THRESHOLD and above -> 1. */
        fun levelFor(score: Float): Float {
            if (!(score >= THRESHOLD)) return 0f
            val t = ln(score / THRESHOLD) / ln(8f)
            return min(1f, MIN_LEVEL + (1f - MIN_LEVEL) * t)
        }
    }
}

/** Fast attack, short hold, smooth decay: what the activity dot and row highlight show. */
class Indicator {
    private var peak = 0f
    private var peakT = 0L

    fun hit(level: Float, tNanos: Long) {
        if (level <= 0f) return
        if (level >= level(tNanos)) {
            peak = level
            peakT = tNanos
        }
    }

    fun level(tNanos: Long): Float {
        if (peak <= 0f) return 0f
        val age = (tNanos - peakT) / 1e9f
        if (age <= HOLD_S) return peak
        val v = peak * exp(-(age - HOLD_S) / DECAY_S)
        return if (v < 0.02f) 0f else v
    }

    fun clear() {
        peak = 0f
    }

    companion object {
        const val HOLD_S = 0.15f
        const val DECAY_S = 0.45f
    }
}
