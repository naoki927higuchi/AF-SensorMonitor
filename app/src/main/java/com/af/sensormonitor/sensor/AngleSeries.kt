package com.af.sensormonitor.sensor

/** Azimuth for the scope and the remaining angles for the time graph, split out of a sensor's history. */
class AngleSplit(val azimuth: HistorySnapshot, val others: HistorySnapshot, val otherLabels: List<String>)

object AngleSeries {
    fun isAngular(kind: ValueKind) = kind == ValueKind.ANGLES || kind == ValueKind.ROTATION

    /**
     * ROTATION: azimuth / pitch / roll derived from each quaternion sample. ANGLES: channel 0 is the azimuth
     * (or heading), the rest go to the time graph as they are.
     */
    fun split(info: TypeInfo, data: HistorySnapshot): AngleSplit {
        val n = data.size
        val times = data.times.copyOf(n)
        if (info.kind == ValueKind.ROTATION) {
            val az = FloatArray(n)
            val rest = FloatArray(n * 2)
            val q = FloatArray(data.channels)
            for (i in 0 until n) {
                for (c in 0 until data.channels) q[c] = data.value(i, c)
                val a = ValueFormat.rotationAngles(q, minOf(data.channels, 4))
                if (a.size < 3) { az[i] = Float.NaN; rest[i * 2] = Float.NaN; rest[i * 2 + 1] = Float.NaN; continue }
                az[i] = a[0]; rest[i * 2] = a[1]; rest[i * 2 + 1] = a[2]
            }
            return AngleSplit(HistorySnapshot(times, az, n, 1), HistorySnapshot(times, rest, n, 2), listOf("PITCH", "ROLL"))
        }
        val ch = data.channels
        if (ch == 0) return AngleSplit(HistorySnapshot.EMPTY, HistorySnapshot.EMPTY, emptyList())
        val az = FloatArray(n) { data.value(it, 0) }
        val restCh = ch - 1
        val rest = FloatArray(n * restCh)
        for (i in 0 until n) for (c in 0 until restCh) rest[i * restCh + c] = data.value(i, c + 1)
        val labels = (1 until ch).map { SensorCatalog.channelLabel(info, it) }
        return AngleSplit(HistorySnapshot(times, az, n, 1), HistorySnapshot(times, rest, n, restCh), labels)
    }

    /** Smallest difference between two angles in degrees (0..180). */
    fun angularDistance(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d < 0f) d += 360f
        return if (d > 180f) 360f - d else d
    }
}
