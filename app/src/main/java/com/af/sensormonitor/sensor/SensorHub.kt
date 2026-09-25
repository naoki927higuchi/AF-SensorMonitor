package com.af.sensormonitor.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.IdentityHashMap

/**
 * Enumerates every sensor the framework exposes (Sensor.TYPE_ALL plus dynamic sensors) and subscribes to the
 * visible ones while the app is in the foreground. No manufacturer or model is ever checked.
 */
class SensorHub(private val context: Context) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val thread = HandlerThread("sensors").apply { start() }
    private val sensorHandler = Handler(thread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _entries = MutableStateFlow<List<SensorEntry>>(emptyList())
    /** All sensors in the default display order. */
    val entries: StateFlow<List<SensorEntry>> = _entries

    private var bySensor = IdentityHashMap<Sensor, SensorEntry>()
    private val subscribed = HashSet<String>()
    private val triggerListeners = HashMap<String, TriggerEventListener>()
    private var wanted: Set<String>? = null
    private var running = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val entry = synchronized(this@SensorHub) { bySensor[event.sensor] } ?: return
            entry.onEvent(event.values, SystemClock.elapsedRealtimeNanos())
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            val entry = synchronized(this@SensorHub) { bySensor[sensor] } ?: return
            entry.onAccuracy(accuracy)
        }
    }

    private val dynamicCallback = object : SensorManager.DynamicSensorCallback() {
        override fun onDynamicSensorConnected(sensor: Sensor) = enumerate()
        override fun onDynamicSensorDisconnected(sensor: Sensor) = enumerate()
    }

    init {
        enumerate()
        manager.registerDynamicSensorCallback(dynamicCallback, mainHandler)
    }

    /** Rebuilds the list; entries for sensors that are still present keep their history. */
    fun enumerate() {
        val sensors = LinkedHashSet<Sensor>()
        sensors += manager.getSensorList(Sensor.TYPE_ALL)
        sensors += manager.getDynamicSensorList(Sensor.TYPE_ALL)
        val list = sensors.toList()
        val infos = list.map { SensorCatalog.info(it.type, it.stringType, it.name) }
        val keys = SensorKeys.keys(list.map { identity(it) })
        val shortNames = SensorKeys.uniqueShortNames(
            infos.map { it.shortName },
            list.mapIndexed { i, s -> infos[i].shortName + if (s.isWakeUpSensor) "|W" else "" },
        )
        val order = list.indices.sortedWith(compareBy({ infos[it].rank }, { it }))
        val old = synchronized(this) { bySensor }
        val built = order.mapIndexed { position, i ->
            val previous = old[list[i]]
            if (previous != null && previous.key == keys[i]) previous
            else SensorEntry(list[i], keys[i], infos[i], shortNames[i], position, i)
        }
        val removed = old.values.filter { e -> built.none { it === e } }
        synchronized(this) {
            bySensor = IdentityHashMap<Sensor, SensorEntry>().apply { built.forEach { put(it.sensor, it) } }
        }
        removed.forEach { unsubscribe(it) }
        _entries.value = built
        sync()
    }

    /** Keys of the sensors shown on the main screen; null until the saved settings are loaded. */
    fun setWanted(keys: Set<String>) {
        wanted = keys
        sync()
    }

    fun start() {
        running = true
        sync()
    }

    fun stop() {
        running = false
        sync()
    }

    /** Re-checks permissions (after the user answers a permission request). */
    fun refreshPermissions() {
        _entries.value.forEach { if (it.status() == Status.LOCKED) it.setStatus(Status.OFF) }
        sync()
    }

    fun hasPermission(access: Access): Boolean = when (access) {
        Access.OPEN -> true
        Access.ACTIVITY_RECOGNITION -> Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        // Body sensors are listed but not read in 1.0.0 (no permission is requested).
        Access.BODY -> false
    }

    private fun sync() {
        val wantedKeys = wanted ?: return
        for (entry in _entries.value) {
            val want = running && entry.key in wantedKeys
            val permitted = hasPermission(entry.info.access)
            when {
                want && !permitted -> {
                    unsubscribe(entry)
                    entry.setStatus(Status.LOCKED)
                }
                want && entry.key !in subscribed && entry.status() != Status.FAILED -> subscribe(entry)
                !want && entry.key in subscribed -> unsubscribe(entry)
                !want && entry.status() == Status.LOCKED -> entry.setStatus(Status.OFF)
            }
        }
    }

    private fun subscribe(entry: SensorEntry) {
        val ok = try {
            if (entry.isOneShot) armTrigger(entry)
            else manager.registerListener(listener, entry.sensor, samplingPeriodUs(entry.sensor), 0, sensorHandler) ||
                // Some sensors refuse an explicit rate; try the plain framework default once.
                manager.registerListener(listener, entry.sensor, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
        } catch (e: Exception) {
            Log.w(TAG, "subscribe failed: ${entry.key}", e)
            false
        }
        if (ok) {
            subscribed += entry.key
            entry.setStatus(if (entry.isOneShot) Status.ARMED else Status.WAITING)
        } else {
            entry.setStatus(Status.FAILED)
        }
    }

    private fun armTrigger(entry: SensorEntry): Boolean {
        val trigger = triggerListeners.getOrPut(entry.key) {
            object : TriggerEventListener() {
                override fun onTrigger(event: TriggerEvent) {
                    entry.onTrigger(event.values, SystemClock.elapsedRealtimeNanos())
                    // One-shot sensors disarm after firing; re-arm while still wanted. The pause keeps a sensor
                    // that fires again immediately (e.g. a "stationary" detector) from firing continuously.
                    val listener = this
                    mainHandler.postDelayed({
                        if (entry.key in subscribed) {
                            val again = try { manager.requestTriggerSensor(listener, entry.sensor) } catch (e: Exception) { false }
                            if (!again) {
                                subscribed -= entry.key
                                entry.setStatus(Status.FAILED)
                            }
                        }
                    }, TRIGGER_REARM_MS)
                }
            }
        }
        return manager.requestTriggerSensor(trigger, entry.sensor)
    }

    private fun unsubscribe(entry: SensorEntry) {
        if (subscribed.remove(entry.key)) {
            try {
                if (entry.isOneShot) triggerListeners[entry.key]?.let { manager.cancelTriggerSensor(it, entry.sensor) }
                else manager.unregisterListener(listener, entry.sensor)
            } catch (e: Exception) {
                Log.w(TAG, "unsubscribe failed: ${entry.key}", e)
            }
        }
        if (entry.status() != Status.FAILED) entry.setStatus(Status.OFF)
    }

    fun release() {
        running = false
        _entries.value.forEach { unsubscribe(it) }
        manager.unregisterDynamicSensorCallback(dynamicCallback)
        thread.quitSafely()
    }

    private fun samplingPeriodUs(sensor: Sensor): Int = maxOf(sensor.minDelay, SAMPLING_PERIOD_US)

    companion object {
        private const val TAG = "AFSensorMonitor"
        /** About 50 Hz: enough to see motion, far below the 200 Hz limit that needs a permission. */
        const val SAMPLING_PERIOD_US = 20_000
        const val TRIGGER_REARM_MS = 1000L

        fun identity(s: Sensor) = SensorIdentity(s.type, s.stringType ?: "", s.name ?: "", s.vendor ?: "", s.isWakeUpSensor)
    }
}
