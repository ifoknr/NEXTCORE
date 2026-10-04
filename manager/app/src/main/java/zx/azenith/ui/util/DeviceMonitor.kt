/*
 * Copyright (C) 2026-2027 NextCore
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zx.azenith.ui.util


import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.topjohnwu.superuser.Shell
import java.io.File
import kotlin.math.abs


/** Where the module writes what it detected at install / boot (see mainfiles/devprobe.sh). */
private const val DEVICE_PROFILE_PATH = "/data/adb/.config/AZenith/device_profile"

enum class SupportLevel { FULL, PARTIAL, UNKNOWN }

data class CpuCluster(
    val firstCpu: Int,
    val lastCpu: Int,
    val maxKhz: Long,
) {
    val cores: Int get() = lastCpu - firstCpu + 1
}

/** Static facts about the device, read once from the module's device_profile. */
data class DeviceProfile(
    val raw: Map<String, String> = emptyMap(),
    val support: SupportLevel = SupportLevel.UNKNOWN,
    val socVendor: String = "",
    val socModel: String = "",
    val clusters: List<CpuCluster> = emptyList(),
    val coreNames: Map<Int, String> = emptyMap(),
    val cpuTempPath: String = "",
    val cpuTempLabel: String = "",
    val gpuName: String = "",
    val gpuFreqPath: String = "",
    val gpuLoadPath: String = "",
    val batteryDir: String = "",
    val batteryCurrentPath: String = "",
) {
    val found: Boolean get() = raw.isNotEmpty()

    /** Short label for a cluster, e.g. "A720 ×4". */
    fun clusterLabel(c: CpuCluster): String {
        val name = coreNames[c.firstCpu] ?: "CPU${c.firstCpu}"
        return "$name ×${c.cores}"
    }

    /** "4× A720 · 3× X4 · 1× X4" */
    val clusterSummary: String
        get() = clusters.joinToString(" · ") { "${it.cores}× ${coreNames[it.firstCpu] ?: "CPU${it.firstCpu}"}" }
}

data class ClusterStat(val label: String, val curKhz: Long, val maxKhz: Long)

/** One sample of live values. Null means "could not read". */
data class LiveStats(
    val clusters: List<ClusterStat> = emptyList(),
    val governor: String = "",
    val cpuTempC: Float? = null,
    val gpuMhz: Int? = null,
    val gpuLoad: Int? = null,
    val batteryPct: Int? = null,
    val batteryTempC: Float? = null,
    val batteryCurrentMa: Int? = null,
    val refreshHz: Int? = null,
    val maxRefreshHz: Int? = null,
)

object DeviceMonitor {

    /** ARM "CPU part" ids to marketing core names. */
    private val armParts = mapOf(
        "0xd03" to "A53", "0xd04" to "A35", "0xd05" to "A55", "0xd07" to "A57", "0xd08" to "A72",
        "0xd09" to "A73", "0xd0a" to "A75", "0xd0b" to "A76", "0xd0d" to "A77", "0xd0e" to "A76AE",
        "0xd40" to "V1", "0xd41" to "A78", "0xd44" to "X1", "0xd46" to "A510", "0xd47" to "A710",
        "0xd48" to "X2", "0xd4b" to "A78C", "0xd4d" to "A715", "0xd4e" to "X3", "0xd80" to "A520",
        "0xd81" to "A720", "0xd82" to "X4", "0xd85" to "X925", "0xd87" to "A725", "0xd8e" to "A720AE",
        "0x001" to "Oryon",
    )

    fun parseKeyValues(text: String): Map<String, String> =
        text.lineSequence()
            .mapNotNull { line ->
                val i = line.indexOf('=')
                if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
            }
            .toMap()

    private fun parseCoreNames(cpuinfo: String): Map<Int, String> {
        val result = mutableMapOf<Int, String>()
        var cpu = -1
        for (line in cpuinfo.lineSequence()) {
            val key = line.substringBefore(':').trim()
            val value = line.substringAfter(':', "").trim()
            when (key) {
                "processor" -> cpu = value.toIntOrNull() ?: -1
                "CPU part" -> if (cpu >= 0) armParts[value.lowercase()]?.let { result[cpu] = it }
            }
        }
        return result
    }

    private fun parseClusters(spec: String): List<CpuCluster> =
        spec.split(' ').filter { it.isNotBlank() }.mapNotNull { part ->
            val range = part.substringBefore(':')
            val max = part.substringAfter(':', "0").toLongOrNull() ?: 0L
            val first = range.substringBefore('-').toIntOrNull() ?: return@mapNotNull null
            val last = range.substringAfter('-', range).toIntOrNull() ?: first
            CpuCluster(first, last, max)
        }

    /** Fallback when the module has not written a profile yet: read cpufreq policies directly. */
    private fun probeClusters(): List<CpuCluster> {
        val dir = File("/sys/devices/system/cpu/cpufreq")
        val policies = dir.listFiles { f -> f.name.startsWith("policy") } ?: return emptyList()
        return policies.sortedBy { it.name.removePrefix("policy").toIntOrNull() ?: 0 }.mapNotNull { p ->
            val cpus = runCatching { File(p, "related_cpus").readText().trim().split(Regex("\\s+")) }
                .getOrNull()?.mapNotNull { it.toIntOrNull() } ?: return@mapNotNull null
            if (cpus.isEmpty()) return@mapNotNull null
            val max = runCatching { File(p, "cpuinfo_max_freq").readText().trim().toLong() }.getOrDefault(0L)
            CpuCluster(cpus.first(), cpus.last(), max)
        }
    }

    /** Blocking. Call from Dispatchers.IO. */
    fun loadProfile(): DeviceProfile {
        val text = RootUtils.readRootFile(DEVICE_PROFILE_PATH).orEmpty()
        val kv = parseKeyValues(text)
        val cpuinfo = runCatching { File("/proc/cpuinfo").readText() }.getOrDefault("")
        val clusters = parseClusters(kv["cpu_clusters"].orEmpty()).ifEmpty { probeClusters() }
        return DeviceProfile(
            raw = kv,
            support = when (kv["support"]) {
                "FULL" -> SupportLevel.FULL
                "PARTIAL" -> SupportLevel.PARTIAL
                else -> SupportLevel.UNKNOWN
            },
            socVendor = kv["soc_vendor"].orEmpty(),
            socModel = kv["soc_model"].orEmpty(),
            clusters = clusters,
            coreNames = parseCoreNames(cpuinfo),
            cpuTempPath = kv["cpu_temp_path"].orEmpty(),
            cpuTempLabel = kv["cpu_temp_label"].orEmpty(),
            gpuName = kv["gpu_name"].orEmpty(),
            gpuFreqPath = kv["gpu_freq_path"].orEmpty(),
            gpuLoadPath = kv["gpu_load_path"].orEmpty(),
            batteryDir = kv["battery_dir"].orEmpty().ifEmpty { "/sys/class/power_supply/battery" },
            batteryCurrentPath = kv["battery_current_path"].orEmpty(),
        )
    }

    private val safePath = Regex("^/(sys|proc)/[A-Za-z0-9_./:@-]+$")

    private fun ints(s: String?): List<Long> =
        Regex("-?\\d+").findAll(s.orEmpty()).mapNotNull { it.value.toLongOrNull() }.toList()

    /** Any unit (Hz, kHz, MHz) to MHz. */
    private fun toMhz(v: Long): Int = when {
        v >= 10_000_000L -> (v / 1_000_000L).toInt()
        v >= 10_000L -> (v / 1_000L).toInt()
        else -> v.toInt()
    }

    /** Millidegrees or degrees to degrees. */
    private fun toCelsius(v: Long): Float = if (abs(v) >= 1000) v / 1000f else v.toFloat()

    private fun refreshRates(context: Context): Pair<Int?, Int?> {
        val dm = context.getSystemService(DisplayManager::class.java) ?: return null to null
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return null to null
        val cur = display.refreshRate.toInt()
        val max = display.supportedModes.maxOfOrNull { it.refreshRate }?.toInt()
        return cur to max
    }

    /**
     * Reads every live value with a single root shell round trip, so a poll
     * costs one command instead of a dozen. Blocking; call from Dispatchers.IO.
     */
    fun sample(context: Context, profile: DeviceProfile): LiveStats {
        val files = linkedMapOf<String, String>()
        profile.clusters.forEach { c ->
            files["c${c.firstCpu}"] = "/sys/devices/system/cpu/cpu${c.firstCpu}/cpufreq/scaling_cur_freq"
        }
        files["gov"] = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
        if (profile.cpuTempPath.isNotEmpty()) files["ctemp"] = profile.cpuTempPath
        if (profile.gpuFreqPath.isNotEmpty()) files["gfreq"] = profile.gpuFreqPath
        if (profile.gpuLoadPath.isNotEmpty()) files["gload"] = profile.gpuLoadPath
        files["bcap"] = "${profile.batteryDir}/capacity"
        files["btemp"] = "${profile.batteryDir}/temp"
        if (profile.batteryCurrentPath.isNotEmpty()) files["bcur"] = profile.batteryCurrentPath

        val script = files.entries
            .filter { safePath.matches(it.value) }
            .joinToString("; ") { (k, p) -> "echo \"$k=\$(head -c 128 '$p' 2>/dev/null | tr '\\n' ' ')\"" }
        val out = runCatching { Shell.cmd(script).exec().out.joinToString("\n") }.getOrDefault("")
        val v = parseKeyValues(out)

        val clusters = profile.clusters.map { c ->
            ClusterStat(
                label = profile.clusterLabel(c),
                curKhz = ints(v["c${c.firstCpu}"]).firstOrNull() ?: 0L,
                maxKhz = c.maxKhz,
            )
        }

        // GPU frequency nodes differ: devfreq/kgsl give one number, GED gives
        // "<index> <freq>". The largest number is the frequency in every format.
        val gpuMhz = ints(v["gfreq"]).maxOrNull()?.let { toMhz(it) }
        val gpuLoad = ints(v["gload"]).firstOrNull()?.toInt()?.coerceIn(0, 100)

        // current_now is µA on most kernels but mA on Samsung; normalise to mA.
        val currentMa = ints(v["bcur"]).firstOrNull()?.let { raw ->
            if (abs(raw) > 20_000) (raw / 1000).toInt() else raw.toInt()
        }

        val (refresh, maxRefresh) = runCatching { refreshRates(context) }.getOrDefault(null to null)

        return LiveStats(
            clusters = clusters,
            governor = v["gov"].orEmpty().trim(),
            cpuTempC = ints(v["ctemp"]).firstOrNull()?.let { toCelsius(it) },
            gpuMhz = gpuMhz,
            gpuLoad = gpuLoad,
            batteryPct = ints(v["bcap"]).firstOrNull()?.toInt(),
            batteryTempC = ints(v["btemp"]).firstOrNull()?.let { it / 10f },
            batteryCurrentMa = currentMa,
            refreshHz = refresh,
            maxRefreshHz = maxRefresh,
        )
    }
}
