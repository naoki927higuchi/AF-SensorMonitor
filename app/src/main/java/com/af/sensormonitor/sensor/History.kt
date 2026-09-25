package com.af.sensormonitor.sensor

/** A copy of part of a [History], oldest first. Values are stored row-major: sample i, channel c at i * channels + c. */
class HistorySnapshot(
    val times: LongArray,
    val values: FloatArray,
    val size: Int,
    val channels: Int,
) {
    fun value(i: Int, c: Int): Float = values[i * channels + c]

    companion object {
        val EMPTY = HistorySnapshot(LongArray(0), FloatArray(0), 0, 0)
    }
}

/**
 * Fixed-size ring buffer of recent samples (about [HISTORY_NS] of data), kept for every subscribed sensor so
 * the detail graph already shows what happened just before it was opened. Samples closer than
 * [MIN_INTERVAL_NS] to the previous one are skipped. Not thread-safe; the owner synchronizes.
 */
class History(private val capacity: Int = CAPACITY) {
    private var channels = 0
    private var times = LongArray(0)
    private var data = FloatArray(0)
    private var head = 0
    private var size = 0

    fun clear() {
        size = 0
        head = 0
    }

    fun add(tNanos: Long, values: FloatArray, count: Int) {
        val n = count.coerceAtMost(MAX_CHANNELS)
        if (n != channels || times.isEmpty()) {
            channels = n
            times = LongArray(capacity)
            data = FloatArray(capacity * maxOf(n, 1))
            clear()
        }
        if (size > 0) {
            val last = times[(head - 1 + capacity) % capacity]
            if (tNanos - last < MIN_INTERVAL_NS) return
        }
        times[head] = tNanos
        values.copyInto(data, head * channels, 0, n)
        head = (head + 1) % capacity
        if (size < capacity) size++
    }

    /** Samples not older than [fromNanos], oldest first. */
    fun snapshot(fromNanos: Long): HistorySnapshot {
        if (size == 0) return HistorySnapshot.EMPTY
        val start = (head - size + capacity) % capacity
        var skip = 0
        while (skip < size && times[(start + skip) % capacity] < fromNanos) skip++
        val n = size - skip
        val outT = LongArray(n)
        val outV = FloatArray(n * channels)
        for (i in 0 until n) {
            val src = (start + skip + i) % capacity
            outT[i] = times[src]
            data.copyInto(outV, i * channels, src * channels, src * channels + channels)
        }
        return HistorySnapshot(outT, outV, n, channels)
    }

    companion object {
        const val HISTORY_NS = 20_000_000_000L
        const val MIN_INTERVAL_NS = 9_000_000L
        const val CAPACITY = 2400
        const val MAX_CHANNELS = 16
    }
}
