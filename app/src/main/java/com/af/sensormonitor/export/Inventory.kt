package com.af.sensormonitor.export

import java.util.Locale

/** Device fields of an inventory. No per-device identifiers (serial, ANDROID_ID) are collected. */
data class DeviceInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val hardware: String,
    val socManufacturer: String?,
    val socModel: String?,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val fingerprint: String,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
)

data class SensorRecord(
    val index: Int,
    val key: String,
    val shortName: String,
    val label: String,
    val name: String,
    val vendor: String,
    val version: Int,
    val type: Int,
    val stringType: String,
    val resolution: Float,
    val maximumRange: Float,
    val powerMa: Float,
    val minDelayUs: Int,
    val maxDelayUs: Int,
    val reportingMode: String,
    val isWakeUp: Boolean,
    val isDynamic: Boolean,
    val id: Int,
    val fifoReserved: Int,
    val fifoMax: Int,
    val additionalInfoSupported: Boolean,
    val directReportRateLevel: Int,
    val visible: Boolean,
    val status: String,
    val eventsReceived: Long,
    val valuesLength: Int,
    val lastAccuracy: Int,
)

data class Inventory(
    val appVersion: String,
    val exportedAt: String,
    val device: DeviceInfo,
    val sensors: List<SensorRecord>,
)

object InventoryFormat {
    const val FORMAT = "af-sensormonitor-inventory"
    const val FORMAT_VERSION = 1

    fun json(inv: Inventory): String {
        val w = JsonWriter()
        w.obj {
            field("format", FORMAT)
            field("formatVersion", FORMAT_VERSION)
            objField("app") {
                field("name", "AF-SensorMonitor")
                field("version", inv.appVersion)
            }
            field("exportedAt", inv.exportedAt)
            objField("device") {
                val d = inv.device
                field("manufacturer", d.manufacturer)
                field("brand", d.brand)
                field("model", d.model)
                field("device", d.device)
                field("product", d.product)
                field("hardware", d.hardware)
                field("socManufacturer", d.socManufacturer)
                field("socModel", d.socModel)
                field("androidRelease", d.androidRelease)
                field("sdkInt", d.sdkInt)
                field("securityPatch", d.securityPatch)
                field("fingerprint", d.fingerprint)
                objField("screen") {
                    field("widthPx", d.screenWidthPx)
                    field("heightPx", d.screenHeightPx)
                    field("densityDpi", d.densityDpi)
                }
            }
            field("sensorCount", inv.sensors.size)
            arrField("sensors") {
                for (s in inv.sensors) item {
                    field("index", s.index)
                    field("key", s.key)
                    field("shortName", s.shortName)
                    field("label", s.label)
                    field("name", s.name)
                    field("vendor", s.vendor)
                    field("version", s.version)
                    field("type", s.type)
                    field("stringType", s.stringType)
                    field("resolution", s.resolution)
                    field("maximumRange", s.maximumRange)
                    field("powerMa", s.powerMa)
                    field("minDelayUs", s.minDelayUs)
                    field("maxDelayUs", s.maxDelayUs)
                    field("reportingMode", s.reportingMode)
                    field("isWakeUp", s.isWakeUp)
                    field("isDynamic", s.isDynamic)
                    field("id", s.id)
                    field("fifoReservedEventCount", s.fifoReserved)
                    field("fifoMaxEventCount", s.fifoMax)
                    field("additionalInfoSupported", s.additionalInfoSupported)
                    field("highestDirectReportRateLevel", s.directReportRateLevel)
                    objField("observed") {
                        field("visible", s.visible)
                        field("status", s.status)
                        field("eventsReceived", s.eventsReceived)
                        field("valuesLength", s.valuesLength)
                        field("lastAccuracy", s.lastAccuracy)
                    }
                }
            }
        }
        return w.toString()
    }

    private val CSV_HEADER = listOf(
        "manufacturer", "model", "androidRelease", "sdkInt", "exportedAt", "sensorCount",
        "index", "shortName", "label", "name", "vendor", "version", "type", "stringType",
        "resolution", "maximumRange", "powerMa", "minDelayUs", "maxDelayUs", "reportingMode",
        "isWakeUp", "isDynamic", "id", "fifoReservedEventCount", "fifoMaxEventCount",
        "additionalInfoSupported", "highestDirectReportRateLevel",
        "visible", "status", "eventsReceived", "valuesLength", "lastAccuracy", "key",
    )

    /**
     * One row per sensor, each row repeating the device columns, so CSVs from several devices can be pasted
     * into one sheet and compared. UTF-8 with BOM for spreadsheet applications.
     */
    fun csv(inv: Inventory): String {
        val sb = StringBuilder("﻿")
        sb.append(CSV_HEADER.joinToString(",")).append("\r\n")
        val d = inv.device
        for (s in inv.sensors) {
            val row = listOf(
                d.manufacturer, d.model, d.androidRelease, d.sdkInt, inv.exportedAt, inv.sensors.size,
                s.index, s.shortName, s.label, s.name, s.vendor, s.version, s.type, s.stringType,
                num(s.resolution), num(s.maximumRange), num(s.powerMa), s.minDelayUs, s.maxDelayUs, s.reportingMode,
                s.isWakeUp, s.isDynamic, s.id, s.fifoReserved, s.fifoMax,
                s.additionalInfoSupported, s.directReportRateLevel,
                s.visible, s.status, s.eventsReceived, s.valuesLength, s.lastAccuracy, s.key,
            )
            sb.append(row.joinToString(",") { csvCell(it.toString()) }).append("\r\n")
        }
        return sb.toString()
    }

    fun csvCell(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || s.startsWith(' ') || s.endsWith(' '))
            "\"" + s.replace("\"", "\"\"") + "\""
        else s

    /** Floats as the shortest exact decimal (as Java prints them), never in a locale format. */
    fun num(v: Float): String = if (v.isFinite()) v.toString() else "null"

    fun fileName(d: DeviceInfo, stamp: String, ext: String): String {
        fun part(s: String) = s.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifEmpty { "unknown" }
        return "AF-SensorMonitor_${part(d.manufacturer)}_${part(d.model)}_$stamp.$ext"
    }
}

/** Minimal JSON writer (pretty-printed, two-space indent). */
class JsonWriter {
    private val sb = StringBuilder()
    private var depth = 0
    private val first = ArrayList<Boolean>()

    fun obj(body: JsonWriter.() -> Unit) {
        open('{'); body(); close('}')
    }

    fun objField(name: String, body: JsonWriter.() -> Unit) {
        key(name); obj(body)
    }

    fun arrField(name: String, body: JsonWriter.() -> Unit) {
        key(name); open('['); body(); close(']')
    }

    fun item(body: JsonWriter.() -> Unit) {
        separator(); obj(body)
    }

    fun field(name: String, value: String?) {
        key(name); sb.append(if (value == null) "null" else quote(value))
    }

    fun field(name: String, value: Int) { key(name); sb.append(value) }
    fun field(name: String, value: Long) { key(name); sb.append(value) }
    fun field(name: String, value: Boolean) { key(name); sb.append(value) }
    fun field(name: String, value: Float) { key(name); sb.append(if (value.isFinite()) value.toString() else "null") }

    private fun key(name: String) {
        separator(); sb.append(quote(name)).append(": ")
    }

    private fun separator() {
        if (first.isEmpty()) return
        if (first.last()) first[first.size - 1] = false else sb.append(',')
        newline()
    }

    private fun open(c: Char) {
        sb.append(c); depth++; first.add(true)
    }

    private fun close(c: Char) {
        depth--
        val empty = first.removeAt(first.size - 1)
        if (!empty) newline()
        sb.append(c)
    }

    private fun newline() {
        sb.append('\n'); repeat(depth) { sb.append("  ") }
    }

    override fun toString() = sb.toString() + "\n"

    companion object {
        fun quote(s: String): String {
            val out = StringBuilder("\"")
            for (ch in s) when {
                ch == '"' -> out.append("\\\"")
                ch == '\\' -> out.append("\\\\")
                ch == '\n' -> out.append("\\n")
                ch == '\r' -> out.append("\\r")
                ch == '\t' -> out.append("\\t")
                ch < ' ' -> out.append(String.format(Locale.ROOT, "\\u%04x", ch.code))
                else -> out.append(ch)
            }
            return out.append('"').toString()
        }
    }
}
