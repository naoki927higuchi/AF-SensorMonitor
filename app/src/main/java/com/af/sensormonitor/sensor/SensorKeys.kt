package com.af.sensormonitor.sensor

/** The identifying metadata of one sensor, independent of the Android framework (for tests and keys). */
data class SensorIdentity(
    val type: Int,
    val stringType: String,
    val name: String,
    val vendor: String,
    val isWakeUp: Boolean,
)

object SensorKeys {
    /**
     * Stable keys for saving display settings. Sensor.getId() is not guaranteed to be stable, so the key is
     * built from metadata; identical sensors are told apart by their order of appearance.
     */
    fun keys(identities: List<SensorIdentity>): List<String> {
        val seen = HashMap<String, Int>()
        return identities.map { id ->
            val base = listOf(id.type.toString(), id.stringType, id.name, id.vendor, if (id.isWakeUp) "W" else "N")
                .joinToString("|") { sanitize(it) }
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            "$base#$n"
        }
    }

    /**
     * Short names made unique with a "#2", "#3"... suffix (the first one keeps the plain name). Names are
     * compared within [groups] (e.g. the wake-up variant is already marked separately, so it is its own group).
     */
    fun uniqueShortNames(names: List<String>, groups: List<String> = names): List<String> {
        val total = groups.groupingBy { it }.eachCount()
        val seen = HashMap<String, Int>()
        return names.mapIndexed { i, name ->
            val group = groups[i]
            if ((total[group] ?: 0) <= 1) return@mapIndexed name
            val n = (seen[group] ?: 0) + 1
            seen[group] = n
            if (n == 1) name else {
                val suffix = "#$n"
                name.take(SensorCatalog.SHORT_NAME_CHARS - suffix.length) + suffix
            }
        }
    }

    private fun sanitize(s: String) = s.replace('|', '/').replace('\n', ' ').replace('\r', ' ')
}
