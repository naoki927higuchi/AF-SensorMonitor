package com.af.sensormonitor.sensor

/** How the values of a sensor type are shown and how a "reaction" is judged. */
enum class ValueKind {
    /** x, y, z (uncalibrated / limited-axes types carry extra channels after these). */
    VECTOR3,
    /** Angles in degrees (wrap at 360). */
    ANGLES,
    /** Rotation-vector quaternion; the main screen shows derived azimuth / pitch / roll. */
    ROTATION,
    SCALAR,
    /** Scalar judged on a log scale (light). */
    LOG_SCALAR,
    /** Near / far. */
    PROXIMITY,
    /** Cumulative count; every increase is a reaction. */
    COUNTER,
    /** Each event is a reaction (detectors, gestures). */
    EVENT,
    /** Unknown or vendor type: raw values only. */
    GENERIC,
}

/** Whether values can be read without a runtime permission. */
enum class Access { OPEN, ACTIVITY_RECOGNITION, BODY }

data class TypeInfo(
    val shortName: String,
    val label: String,
    val unit: String,
    val kind: ValueKind,
    val decimals: Int,
    /** Smallest change (after the kind's transform) treated as meaningful; 0 derives it from the resolution. */
    val reactionFloor: Float,
    val channelLabels: List<String> = emptyList(),
    val access: Access = Access.OPEN,
    /** Position in the default display order; unknown types come last. */
    val rank: Int = Int.MAX_VALUE,
)

/**
 * Display information for the sensor types defined by the Android platform (AOSP constants, including
 * values that are @hide in the SDK). Nothing here depends on a manufacturer or model: a sensor whose
 * type is not listed is still shown, using [genericInfo].
 */
object SensorCatalog {
    private val XYZ = listOf("X", "Y", "Z")
    private val XYZ_BIAS = listOf("X", "Y", "Z", "BIAS X", "BIAS Y", "BIAS Z")
    private val XYZ_SUPPORTED = listOf("X", "Y", "Z", "X SUPPORTED", "Y SUPPORTED", "Z SUPPORTED")
    private val XYZ_BIAS_SUPPORTED = XYZ_BIAS + listOf("X SUPPORTED", "Y SUPPORTED", "Z SUPPORTED")
    private val QUAT = listOf("X", "Y", "Z", "W", "HEADING ACC")

    private val entries: List<Pair<Int, TypeInfo>> = listOf(
        // Motion
        1 to TypeInfo("ACCEL", "Accelerometer", "m/s²", ValueKind.VECTOR3, 2, 0.06f, XYZ),
        35 to TypeInfo("ACCEL-U", "Accelerometer (uncalibrated)", "m/s²", ValueKind.VECTOR3, 2, 0.06f, XYZ_BIAS),
        38 to TypeInfo("ACCEL-L", "Accelerometer (limited axes)", "m/s²", ValueKind.VECTOR3, 2, 0.06f, XYZ_SUPPORTED),
        40 to TypeInfo("ACCEL-LU", "Accelerometer (limited axes, uncalibrated)", "m/s²", ValueKind.VECTOR3, 2, 0.06f, XYZ_BIAS_SUPPORTED),
        10 to TypeInfo("LINACC", "Linear acceleration", "m/s²", ValueKind.VECTOR3, 2, 0.06f, XYZ),
        9 to TypeInfo("GRAVITY", "Gravity", "m/s²", ValueKind.VECTOR3, 2, 0.03f, XYZ),
        4 to TypeInfo("GYRO", "Gyroscope", "rad/s", ValueKind.VECTOR3, 2, 0.02f, XYZ),
        16 to TypeInfo("GYRO-U", "Gyroscope (uncalibrated)", "rad/s", ValueKind.VECTOR3, 2, 0.02f, XYZ_BIAS),
        39 to TypeInfo("GYRO-L", "Gyroscope (limited axes)", "rad/s", ValueKind.VECTOR3, 2, 0.02f, XYZ_SUPPORTED),
        41 to TypeInfo("GYRO-LU", "Gyroscope (limited axes, uncalibrated)", "rad/s", ValueKind.VECTOR3, 2, 0.02f, XYZ_BIAS_SUPPORTED),
        // Orientation
        11 to TypeInfo("ROTVEC", "Rotation vector", "°", ValueKind.ROTATION, 0, 0.002f, QUAT),
        15 to TypeInfo("GAMEROT", "Game rotation vector", "°", ValueKind.ROTATION, 0, 0.002f, QUAT),
        20 to TypeInfo("GEOROT", "Geomagnetic rotation vector", "°", ValueKind.ROTATION, 0, 0.002f, QUAT),
        3 to TypeInfo("ORIENT", "Orientation", "°", ValueKind.ANGLES, 0, 0.5f, listOf("AZIMUTH", "PITCH", "ROLL")),
        42 to TypeInfo("HEADING", "Heading", "°", ValueKind.ANGLES, 0, 0.5f, listOf("HEADING", "ACCURACY")),
        2 to TypeInfo("MAG", "Magnetic field", "μT", ValueKind.VECTOR3, 1, 0.6f, XYZ),
        14 to TypeInfo("MAG-U", "Magnetic field (uncalibrated)", "μT", ValueKind.VECTOR3, 1, 0.6f, XYZ_BIAS),
        27 to TypeInfo("DEVORIENT", "Device orientation", "", ValueKind.SCALAR, 0, 0.5f, listOf("ORIENTATION")),
        36 to TypeInfo("HINGE", "Hinge angle", "°", ValueKind.SCALAR, 0, 1f, listOf("ANGLE")),
        37 to TypeInfo("HEADTRACK", "Head tracker", "", ValueKind.GENERIC, 2, 0f,
            listOf("RX", "RY", "RZ", "VX", "VY", "VZ", "DISCONTINUITY")),
        28 to TypeInfo("POSE6DOF", "Pose 6DOF", "", ValueKind.GENERIC, 2, 0f),
        // Environment
        8 to TypeInfo("PROX", "Proximity", "cm", ValueKind.PROXIMITY, 1, 0f, listOf("DISTANCE")),
        5 to TypeInfo("LIGHT", "Light", "lx", ValueKind.LOG_SCALAR, 0, 0.04f, listOf("ILLUMINANCE")),
        6 to TypeInfo("PRESSURE", "Pressure", "hPa", ValueKind.SCALAR, 1, 0.03f, listOf("PRESSURE")),
        13 to TypeInfo("TEMP", "Ambient temperature", "°C", ValueKind.SCALAR, 1, 0.1f, listOf("TEMPERATURE")),
        7 to TypeInfo("TEMP-OLD", "Temperature", "°C", ValueKind.SCALAR, 1, 0.1f, listOf("TEMPERATURE")),
        12 to TypeInfo("HUMIDITY", "Relative humidity", "%", ValueKind.SCALAR, 1, 0.3f, listOf("HUMIDITY")),
        // Activity, detectors, gestures
        19 to TypeInfo("STEPS", "Step counter", "steps", ValueKind.COUNTER, 0, 0f, listOf("STEPS"), Access.ACTIVITY_RECOGNITION),
        18 to TypeInfo("STEP", "Step detector", "", ValueKind.EVENT, 0, 0f, listOf("STEP"), Access.ACTIVITY_RECOGNITION),
        17 to TypeInfo("SIGMOTION", "Significant motion", "", ValueKind.EVENT, 0, 0f),
        30 to TypeInfo("MOTION", "Motion detect", "", ValueKind.EVENT, 0, 0f),
        29 to TypeInfo("STATIONARY", "Stationary detect", "", ValueKind.EVENT, 0, 0f),
        22 to TypeInfo("TILT", "Tilt detector", "", ValueKind.EVENT, 0, 0f),
        23 to TypeInfo("WAKE-GEST", "Wake gesture", "", ValueKind.EVENT, 0, 0f),
        24 to TypeInfo("GLANCE", "Glance gesture", "", ValueKind.EVENT, 0, 0f),
        25 to TypeInfo("PICKUP", "Pick up gesture", "", ValueKind.EVENT, 0, 0f),
        26 to TypeInfo("WRISTTILT", "Wrist tilt gesture", "", ValueKind.EVENT, 0, 0f),
        34 to TypeInfo("OFFBODY", "Low latency off-body detect", "", ValueKind.EVENT, 0, 0f, listOf("STATE")),
        21 to TypeInfo("HEARTRATE", "Heart rate", "bpm", ValueKind.SCALAR, 0, 1f, listOf("BPM"), Access.BODY),
        31 to TypeInfo("HEARTBEAT", "Heart beat", "", ValueKind.EVENT, 0, 0f, listOf("CONFIDENCE"), Access.BODY),
        32 to TypeInfo("DYN-META", "Dynamic sensor meta", "", ValueKind.EVENT, 0, 0f),
        33 to TypeInfo("ADD-INFO", "Additional info", "", ValueKind.EVENT, 0, 0f),
    )

    private val byType: Map<Int, TypeInfo> =
        entries.mapIndexed { index, (type, info) -> type to info.copy(rank = index) }.toMap()

    fun known(type: Int): TypeInfo? = byType[type]

    fun info(type: Int, stringType: String?, name: String): TypeInfo =
        byType[type] ?: genericInfo(stringType, name)

    fun genericInfo(stringType: String?, name: String): TypeInfo = TypeInfo(
        shortName = deriveShortName(stringType, name),
        label = name.ifBlank { stringType ?: "Sensor" },
        unit = "",
        kind = ValueKind.GENERIC,
        decimals = 2,
        reactionFloor = 0f,
    )

    const val SHORT_NAME_CHARS = 12

    /**
     * A short upper-case name for an unknown type, from the last segment of its string type (or its name).
     * Too long names are shortened word by word (longest word first, down to 4, then 3 letters), then the
     * separators are dropped, then the rest is cut.
     */
    fun deriveShortName(stringType: String?, name: String): String {
        val fromType = stringType?.substringAfterLast('.')?.trim().orEmpty()
        val base = fromType.ifEmpty { name }
        val words = base.uppercase().split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }.toMutableList()
        if (words.isEmpty()) return "SENSOR"
        fun length() = words.sumOf { it.length } + words.size - 1
        for (minWord in intArrayOf(4, 3)) {
            while (length() > SHORT_NAME_CHARS) {
                val i = words.indices.maxBy { words[it].length }
                if (words[i].length <= minWord) break
                words[i] = words[i].dropLast(1)
            }
        }
        val joined = if (length() > SHORT_NAME_CHARS) words.joinToString("") else words.joinToString("_")
        return joined.take(SHORT_NAME_CHARS)
    }

    fun channelLabel(info: TypeInfo, index: Int): String =
        info.channelLabels.getOrNull(index) ?: "V$index"
}
