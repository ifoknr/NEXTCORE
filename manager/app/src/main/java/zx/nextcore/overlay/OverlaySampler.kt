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

package zx.nextcore.overlay

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import zx.nextcore.ui.util.DeviceMonitor
import zx.nextcore.ui.util.DeviceProfile
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Collects one [OverlayState] per tick. Only the values the panel shows are
 * read. Root values (clocks, temperatures, GPU, CPU load) come from one script
 * on the frame meter's own root shell; battery, network and thermal status come
 * from Android APIs without root. Blocking; call from Dispatchers.IO.
 */
class OverlaySampler(private val context: Context) : AutoCloseable {

    private val meter = FrameMeter()
    private var profile: DeviceProfile? = null
    private var gpuTempPath: String? = null
    private var gpuTempSearched = false

    private var prevCpuTotal = 0L
    private var prevCpuIdle = 0L
    private var prevNetBytes = -1L
    private var prevNetAt = 0L

    private val history = ArrayDeque<Float>()
    private var sessionPkg: String? = null
    private var sessionStart = SystemClock.elapsedRealtime()
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.US)

    /** Set by the ping loop, which runs on its own slower schedule. */
    @Volatile var pingMs: Int? = null

    override fun close() = meter.close()

    fun sample(wanted: Set<OverlayMetric>, gamePkg: String?): OverlayState {
        val prof = profile ?: DeviceMonitor.loadProfile().also { profile = it }

        if (gamePkg != sessionPkg) {
            sessionPkg = gamePkg
            sessionStart = SystemClock.elapsedRealtime()
        }

        val frames = if (wanted.any { it in FRAME_METRICS }) meter.sample(gamePkg) else null
        frames?.fps?.let {
            history.addLast(it)
            while (history.size > HISTORY_SIZE) history.removeFirst()
        }

        val root = readRoot(wanted, prof)
        val battery = if (wanted.any { it in BATTERY_METRICS }) context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) else null
        val mem = if (OverlayMetric.RAM in wanted) readMeminfo() else emptyMap()

        val clusters = prof.clusters.mapNotNull { c -> ints(root["c${c.firstCpu}"]).firstOrNull()?.let { toMhz(it) } }

        return OverlayState(
            fps = frames?.fps,
            frameTimeMs = frames?.frameTimeMs,
            low1 = frames?.low1,
            jank = frames?.takeIf { it.method == FrameMeter.Method.LAYER }?.jank,
            cpuMaxMhz = clusters.maxOrNull(),
            clusterMhz = clusters,
            cpuLoad = cpuLoad(root["stat"]),
            cpuTempC = ints(root["ctemp"]).firstOrNull()?.let { toCelsius(it) }?.takeIf { it in 1f..150f },
            gpuLoad = ints(root["gload"]).firstOrNull()?.toInt()?.coerceIn(0, 100),
            gpuMhz = ints(root["gfreq"]).maxOrNull()?.let { toMhz(it) }?.takeIf { it > 0 },
            gpuTempC = ints(root["gtemp"]).firstOrNull()?.let { toCelsius(it) }?.takeIf { it in 1f..150f },
            ramUsedKb = mem["MemTotal"]?.let { t -> mem["MemAvailable"]?.let { t - it } },
            ramTotalKb = mem["MemTotal"],
            battTempC = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE && abs(it) < 2000 }?.let { it / 10f },
            battLevel = battery?.let { b ->
                val level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) level * 100 / scale else null
            },
            powerW = if (OverlayMetric.POWER in wanted) battery?.let { power(it) } else null,
            charging = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING,
            thermalStatus = if (OverlayMetric.THERMAL in wanted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                context.getSystemService(PowerManager::class.java)?.currentThermalStatus else null,
            netBytesPerSec = if (OverlayMetric.NET in wanted) netSpeed() else null,
            pingMs = pingMs,
            clock = clockFormat.format(Date()),
            sessionSec = (SystemClock.elapsedRealtime() - sessionStart) / 1000,
            refreshHz = refreshHz(),
            fpsHistory = history.toList(),
        )
    }

    /** Pings a public DNS server once; null on timeout. Blocking. */
    fun ping(): Int? {
        val out = runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-w", "2", "8.8.8.8"))
            try { p.inputStream.bufferedReader().readText() } finally { p.destroy() }
        }.getOrNull().orEmpty()
        return Regex("""time[=<]([0-9.]+)""").find(out)?.groupValues?.get(1)?.toFloatOrNull()?.toInt()
    }

    /* ---------- Root reads ---------- */

    private val safePath = Regex("^/(sys|proc)/[A-Za-z0-9_./:@-]+$")

    private fun readRoot(wanted: Set<OverlayMetric>, prof: DeviceProfile): Map<String, String> {
        val files = linkedMapOf<String, String>()
        if (OverlayMetric.CPU_FREQ in wanted || OverlayMetric.CPU_CLUSTERS in wanted) {
            prof.clusters.forEach { files["c${it.firstCpu}"] = "/sys/devices/system/cpu/cpu${it.firstCpu}/cpufreq/scaling_cur_freq" }
        }
        if (OverlayMetric.CPU_TEMP in wanted && prof.cpuTempPath.isNotEmpty()) files["ctemp"] = prof.cpuTempPath
        if (OverlayMetric.GPU_LOAD in wanted && prof.gpuLoadPath.isNotEmpty()) files["gload"] = prof.gpuLoadPath
        if (OverlayMetric.GPU_FREQ in wanted && prof.gpuFreqPath.isNotEmpty()) files["gfreq"] = prof.gpuFreqPath
        if (OverlayMetric.GPU_TEMP in wanted) findGpuTemp()?.let { files["gtemp"] = it }

        val lines = files.filterValues { safePath.matches(it) }
            .map { (k, p) -> "echo \"$k=\$(head -c 64 '$p' 2>/dev/null | tr '\\n' ' ')\"" }
            .toMutableList()
        if (OverlayMetric.CPU_LOAD in wanted) lines += "echo \"stat=\$(head -n 1 /proc/stat)\""
        if (lines.isEmpty()) return emptyMap()
        return DeviceMonitor.parseKeyValues(meter.exec(lines.joinToString("; ")).joinToString("\n"))
    }

    /** The first thermal zone whose type names the GPU, looked up once. */
    private fun findGpuTemp(): String? {
        if (gpuTempSearched) return gpuTempPath
        gpuTempSearched = true
        gpuTempPath = meter.exec("for z in /sys/class/thermal/thermal_zone*; do echo \"\$z \$(cat \$z/type 2>/dev/null)\"; done")
            .map { it.trim().split(' ', limit = 2) }
            .firstOrNull { it.size == 2 && it[1].contains("gpu", ignoreCase = true) }
            ?.let { "${it[0]}/temp" }
            ?.takeIf { safePath.matches(it) }
        return gpuTempPath
    }

    private fun cpuLoad(stat: String?): Int? {
        val v = stat?.trim()?.split(Regex("\\s+"))?.drop(1)?.mapNotNull { it.toLongOrNull() } ?: return null
        if (v.size < 4) return null
        val idle = v[3] + (v.getOrNull(4) ?: 0L)
        val total = v.take(8).sum()
        val dTotal = total - prevCpuTotal
        val dIdle = idle - prevCpuIdle
        val first = prevCpuTotal == 0L
        prevCpuTotal = total
        prevCpuIdle = idle
        if (first || dTotal <= 0L) return null
        return ((dTotal - dIdle) * 100 / dTotal).toInt().coerceIn(0, 100)
    }

    /* ---------- Without root ---------- */

    private fun power(battery: Intent): Float? {
        val bm = context.getSystemService(BatteryManager::class.java) ?: return null
        val raw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (raw == Int.MIN_VALUE || raw == 0) return null
        // µA on most kernels, mA on some (Samsung); normalise to amps.
        val amps = if (abs(raw) > 20_000) abs(raw) / 1_000_000f else abs(raw) / 1000f
        val volts = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0).takeIf { it > 0 }?.let { if (it > 1000) it / 1000f else it.toFloat() } ?: return null
        return (amps * volts).takeIf { it in 0.01f..150f }
    }

    private fun netSpeed(): Long? {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return null
        val bytes = rx + tx
        val now = SystemClock.elapsedRealtime()
        val prev = prevNetBytes
        val dt = now - prevNetAt
        prevNetBytes = bytes
        prevNetAt = now
        if (prev < 0 || dt <= 0 || bytes < prev) return null
        return (bytes - prev) * 1000 / dt
    }

    private fun refreshHz(): Int =
        context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.toInt()?.takeIf { it > 0 } ?: 60

    private fun readMeminfo(): Map<String, Long> = runCatching {
        File("/proc/meminfo").readLines().mapNotNull { line ->
            val value = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: return@mapNotNull null
            line.substringBefore(':').trim() to value
        }.toMap()
    }.getOrDefault(emptyMap())

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

    companion object {
        const val HISTORY_SIZE = 60
        private val FRAME_METRICS = setOf(OverlayMetric.FPS, OverlayMetric.FRAME_TIME, OverlayMetric.LOW1, OverlayMetric.JANK)
        private val BATTERY_METRICS = setOf(OverlayMetric.BATT_TEMP, OverlayMetric.BATT_LEVEL, OverlayMetric.POWER)
    }
}
