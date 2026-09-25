package com.af.sensormonitor.export

import android.content.Context
import android.hardware.Sensor
import android.os.Build
import android.os.SystemClock
import com.af.sensormonitor.BuildConfig
import com.af.sensormonitor.sensor.SensorEntry
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object InventoryCollector {
    fun collect(context: Context, entries: List<SensorEntry>, hidden: Set<String>, now: OffsetDateTime): Inventory {
        val metrics = context.resources.displayMetrics
        val wm = context.getSystemService(android.view.WindowManager::class.java)
        val bounds = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds else null
        val device = DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            device = Build.DEVICE,
            product = Build.PRODUCT,
            hardware = Build.HARDWARE,
            socManufacturer = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else null,
            socModel = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            fingerprint = Build.FINGERPRINT,
            screenWidthPx = bounds?.width() ?: metrics.widthPixels,
            screenHeightPx = bounds?.height() ?: metrics.heightPixels,
            densityDpi = metrics.densityDpi,
        )
        val t = SystemClock.elapsedRealtimeNanos()
        val sensors = entries.sortedBy { it.osIndex }.map { e ->
            val s = e.sensor
            val snap = e.snapshot(t)
            SensorRecord(
                index = e.osIndex,
                key = e.key,
                shortName = e.shortName,
                label = e.info.label,
                name = s.name ?: "",
                vendor = s.vendor ?: "",
                version = s.version,
                type = s.type,
                stringType = s.stringType ?: "",
                resolution = s.resolution,
                maximumRange = s.maximumRange,
                powerMa = s.power,
                minDelayUs = s.minDelay,
                maxDelayUs = s.maxDelay,
                reportingMode = reportingModeName(s.reportingMode),
                isWakeUp = s.isWakeUpSensor,
                isDynamic = s.isDynamicSensor,
                id = s.id,
                fifoReserved = s.fifoReservedEventCount,
                fifoMax = s.fifoMaxEventCount,
                additionalInfoSupported = s.isAdditionalInfoSupported,
                directReportRateLevel = s.highestDirectReportRateLevel,
                visible = e.key !in hidden,
                status = snap.status.name,
                eventsReceived = snap.events,
                valuesLength = e.maxValuesLength(),
                lastAccuracy = snap.accuracy,
            )
        }
        return Inventory(
            appVersion = BuildConfig.VERSION_NAME,
            exportedAt = now.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            device = device,
            sensors = sensors,
        )
    }

    fun stamp(now: OffsetDateTime): String = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))

    fun reportingModeName(mode: Int): String = when (mode) {
        Sensor.REPORTING_MODE_CONTINUOUS -> "CONTINUOUS"
        Sensor.REPORTING_MODE_ON_CHANGE -> "ON_CHANGE"
        Sensor.REPORTING_MODE_ONE_SHOT -> "ONE_SHOT"
        Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "SPECIAL_TRIGGER"
        else -> "UNKNOWN($mode)"
    }
}
