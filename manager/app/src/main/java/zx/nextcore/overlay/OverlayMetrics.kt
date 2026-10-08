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
import android.content.SharedPreferences
import androidx.annotation.StringRes
import zx.nextcore.R
import java.util.Locale

/**
 * Everything the floating monitor can show. [key] is saved in preferences and
 * must never change. [label] is the short tag drawn on the panel itself; it
 * stays in English in every language, like the numbers next to it.
 */
enum class OverlayMetric(val key: String, val label: String, @StringRes val title: Int, @StringRes val desc: Int, val sample: String) {
    FPS("fps", "FPS", R.string.ov_m_fps, R.string.ov_m_fps_desc, "120"),
    FRAME_TIME("frametime", "FT", R.string.ov_m_frametime, R.string.ov_m_frametime_desc, "8.3ms"),
    LOW1("low1", "1%", R.string.ov_m_low1, R.string.ov_m_low1_desc, "97"),
    JANK("jank", "JANK", R.string.ov_m_jank, R.string.ov_m_jank_desc, "0"),
    CPU_FREQ("cpu", "CPU", R.string.ov_m_cpu, R.string.ov_m_cpu_desc, "2.8G"),
    CPU_CLUSTERS("clusters", "CL", R.string.ov_m_clusters, R.string.ov_m_clusters_desc, "2.0 2.6 3.1"),
    CPU_LOAD("cpuload", "LOAD", R.string.ov_m_cpuload, R.string.ov_m_cpuload_desc, "42%"),
    CPU_TEMP("cputemp", "TEMP", R.string.ov_m_cputemp, R.string.ov_m_cputemp_desc, "61°"),
    GPU_LOAD("gpu", "GPU", R.string.ov_m_gpu, R.string.ov_m_gpu_desc, "58%"),
    GPU_FREQ("gpufreq", "GCLK", R.string.ov_m_gpufreq, R.string.ov_m_gpufreq_desc, "850M"),
    GPU_TEMP("gputemp", "GT", R.string.ov_m_gputemp, R.string.ov_m_gputemp_desc, "57°"),
    RAM("ram", "RAM", R.string.ov_m_ram, R.string.ov_m_ram_desc, "5.1G"),
    BATT_TEMP("batttemp", "BAT°", R.string.ov_m_batttemp, R.string.ov_m_batttemp_desc, "38°"),
    BATT_LEVEL("battlevel", "BAT", R.string.ov_m_battlevel, R.string.ov_m_battlevel_desc, "84%"),
    POWER("power", "PWR", R.string.ov_m_power, R.string.ov_m_power_desc, "4.2W"),
    THERMAL("thermal", "THERM", R.string.ov_m_thermal, R.string.ov_m_thermal_desc, "OK"),
    NET("net", "NET", R.string.ov_m_net, R.string.ov_m_net_desc, "1.2M"),
    PING("ping", "PING", R.string.ov_m_ping, R.string.ov_m_ping_desc, "35ms"),
    CLOCK("clock", "TIME", R.string.ov_m_clock, R.string.ov_m_clock_desc, "21:40"),
    SESSION("session", "SESS", R.string.ov_m_session, R.string.ov_m_session_desc, "24:10");

    companion object {
        fun fromKey(key: String): OverlayMetric? = entries.firstOrNull { it.key == key }

        /** What a new user sees: the numbers that matter while playing. */
        val defaults = listOf(FPS, LOW1, CPU_FREQ, CPU_TEMP, GPU_LOAD, RAM, BATT_TEMP)
    }
}

/** Panel layouts. Long-press the panel to cycle through them in game. */
enum class OverlayMode(val key: String, @StringRes val title: Int) {
    COMPACT("compact", R.string.ov_mode_compact),
    MINIMAL("minimal", R.string.ov_mode_minimal),
    EXPANDED("expanded", R.string.ov_mode_expanded);

    fun next(): OverlayMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): OverlayMode = entries.firstOrNull { it.key == key } ?: COMPACT
    }
}

/** Floating monitor preferences, kept in the app's "settings" SharedPreferences. */
object OverlayPrefs {
    const val ENABLED = "overlay_enabled"
    const val GAMES_ONLY = "overlay_games_only"
    const val MODE = "overlay_mode"
    /** Comma list of every metric key in display order. */
    const val ORDER = "overlay_order"
    /** Comma list of the shown metric keys. */
    const val SHOWN = "overlay_shown"
    const val GRAPH = "overlay_graph"
    const val SCALE = "overlay_scale"
    const val OPACITY = "overlay_opacity"
    const val INTERVAL_MS = "overlay_interval_ms"
    const val ACCENT = "overlay_accent"
    const val COLOR_CODE = "overlay_color_code"
    const val BORDER = "overlay_border"
    const val POS_X = "overlay_x"
    const val POS_Y = "overlay_y"
    const val POS_X_LAND = "overlay_x_land"
    const val POS_Y_LAND = "overlay_y_land"

    // Before the module list existed each metric had its own switch.
    private const val OLD_SHOW_FPS = "overlay_show_fps"
    private const val OLD_SHOW_CPU_TEMP = "overlay_show_cpu_temp"
    private const val OLD_SHOW_GPU = "overlay_show_gpu"
    private const val OLD_SHOW_RAM = "overlay_show_ram"
    private const val OLD_SHOW_BATT_TEMP = "overlay_show_batt_temp"
    private const val OLD_VERTICAL = "overlay_vertical"

    val intervalsMs = listOf(250L, 500L, 1000L, 2000L)
    const val DEFAULT_INTERVAL_MS = 500L

    /** Accent choices for labels and the graph: NextCore orange, cyan, green, pink, white. */
    val accents = listOf(0xFFFF6B2C.toInt(), 0xFF22D3EE.toInt(), 0xFF4ADE80.toInt(), 0xFFF472B6.toInt(), 0xFFFFFFFF.toInt())

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun order(p: SharedPreferences): List<OverlayMetric> {
        val saved = p.getString(ORDER, null)?.split(',')?.mapNotNull { OverlayMetric.fromKey(it) }.orEmpty().distinct()
        // Metrics added in a later version go to the end of a saved order.
        return saved + OverlayMetric.entries.filterNot { it in saved }
    }

    fun shown(p: SharedPreferences): Set<OverlayMetric> {
        p.getString(SHOWN, null)?.let { s -> return s.split(',').mapNotNull { OverlayMetric.fromKey(it) }.toSet() }
        if (!p.contains(OLD_SHOW_FPS) && !p.contains(OLD_SHOW_RAM)) return OverlayMetric.defaults.toSet()
        // Carry over the old per-metric switches.
        return buildSet {
            if (p.getBoolean(OLD_SHOW_FPS, true)) add(OverlayMetric.FPS)
            if (p.getBoolean(OLD_SHOW_CPU_TEMP, true)) add(OverlayMetric.CPU_TEMP)
            if (p.getBoolean(OLD_SHOW_GPU, false)) add(OverlayMetric.GPU_LOAD)
            if (p.getBoolean(OLD_SHOW_RAM, true)) add(OverlayMetric.RAM)
            if (p.getBoolean(OLD_SHOW_BATT_TEMP, true)) add(OverlayMetric.BATT_TEMP)
        }
    }

    fun mode(p: SharedPreferences): OverlayMode =
        if (!p.contains(MODE) && p.getBoolean(OLD_VERTICAL, false)) OverlayMode.EXPANDED
        else OverlayMode.fromKey(p.getString(MODE, null))

    fun saveOrder(p: SharedPreferences, order: List<OverlayMetric>) =
        p.edit().putString(ORDER, order.joinToString(",") { it.key }).apply()

    fun saveShown(p: SharedPreferences, shown: Set<OverlayMetric>) =
        p.edit().putString(SHOWN, shown.joinToString(",") { it.key }).apply()

    /** Shown metrics in display order. */
    fun visible(p: SharedPreferences): List<OverlayMetric> {
        val shown = shown(p)
        return order(p).filter { it in shown }
    }

    fun scale(p: SharedPreferences): Float = p.getFloat(SCALE, 1f).coerceIn(0.7f, 1.8f)

    fun interval(p: SharedPreferences): Long = p.getLong(INTERVAL_MS, DEFAULT_INTERVAL_MS).coerceIn(250L, 5000L)

    fun accent(p: SharedPreferences): Int = accents.getOrElse(p.getInt(ACCENT, 0)) { accents[0] }
}

/** One reading of everything the panel can show. Null means "could not read". */
data class OverlayState(
    val fps: Float? = null,
    val frameTimeMs: Float? = null,
    val low1: Float? = null,
    val jank: Int? = null,
    val cpuMaxMhz: Int? = null,
    val clusterMhz: List<Int> = emptyList(),
    val cpuLoad: Int? = null,
    val cpuTempC: Float? = null,
    val gpuLoad: Int? = null,
    val gpuMhz: Int? = null,
    val gpuTempC: Float? = null,
    val ramUsedKb: Long? = null,
    val ramTotalKb: Long? = null,
    val battTempC: Float? = null,
    val battLevel: Int? = null,
    val powerW: Float? = null,
    val charging: Boolean = false,
    /** PowerManager thermal status, 0 (none) to 6 (shutdown). */
    val thermalStatus: Int? = null,
    val netBytesPerSec: Long? = null,
    val pingMs: Int? = null,
    val clock: String = "",
    val sessionSec: Long = 0L,
    /** Refresh rate of the screen, used to color the FPS. */
    val refreshHz: Int = 60,
    /** Recent FPS readings for the graph, oldest first. */
    val fpsHistory: List<Float> = emptyList(),
) {
    companion object {
        /** Made-up values for the settings preview. */
        val preview = OverlayState(
            fps = 118f, frameTimeMs = 8.4f, low1 = 97f, jank = 0, cpuMaxMhz = 2850, clusterMhz = listOf(2000, 2600, 3100),
            cpuLoad = 42, cpuTempC = 61f, gpuLoad = 58, gpuMhz = 850, gpuTempC = 57f, ramUsedKb = 5_300_000, ramTotalKb = 11_800_000,
            battTempC = 38f, battLevel = 84, powerW = 4.2f, thermalStatus = 0, netBytesPerSec = 1_250_000, pingMs = 35,
            clock = "21:40", sessionSec = 1450, refreshHz = 120,
            fpsHistory = listOf(118f, 120f, 119f, 117f, 120f, 112f, 104f, 116f, 120f, 120f, 119f, 118f, 120f, 108f, 117f, 120f, 120f, 119f, 120f, 118f),
        )
    }
}

/** How a value reads on the panel, and whether it is good, a warning or bad. */
data class OverlayValue(val text: String, val level: Int = 0)

private fun f0(v: Float) = String.format(Locale.US, "%.0f", v)
private fun f1(v: Float) = String.format(Locale.US, "%.1f", v)
private fun ghz(mhz: Int) = if (mhz >= 1000) f1(mhz / 1000f) + "G" else "${mhz}M"

private fun tempLevel(c: Float, warn: Float, bad: Float) = when {
    c >= bad -> 2
    c >= warn -> 1
    else -> 0
}

fun OverlayMetric.format(s: OverlayState): OverlayValue = when (this) {
    OverlayMetric.FPS -> s.fps?.let {
        val target = s.refreshHz.coerceAtLeast(30)
        OverlayValue(f0(it), if (it >= target * 0.85f || it >= 58f) 0 else if (it >= 30f) 1 else 2)
    } ?: OverlayValue("--")
    OverlayMetric.FRAME_TIME -> s.frameTimeMs?.let { OverlayValue(f1(it) + "ms", if (it <= 1000f / s.refreshHz * 1.5f) 0 else if (it <= 33.4f) 1 else 2) } ?: OverlayValue("--")
    OverlayMetric.LOW1 -> s.low1?.let { OverlayValue(f0(it), if (it >= 50f) 0 else if (it >= 25f) 1 else 2) } ?: OverlayValue("--")
    OverlayMetric.JANK -> s.jank?.let { OverlayValue("$it", if (it == 0) 0 else if (it <= 2) 1 else 2) } ?: OverlayValue("--")
    OverlayMetric.CPU_FREQ -> s.cpuMaxMhz?.let { OverlayValue(ghz(it)) } ?: OverlayValue("--")
    OverlayMetric.CPU_CLUSTERS -> if (s.clusterMhz.isEmpty()) OverlayValue("--")
        else OverlayValue(s.clusterMhz.joinToString(" ") { if (it >= 1000) f1(it / 1000f) else "0.${it / 100}" })
    OverlayMetric.CPU_LOAD -> s.cpuLoad?.let { OverlayValue("$it%", if (it < 80) 0 else if (it < 95) 1 else 2) } ?: OverlayValue("--")
    OverlayMetric.CPU_TEMP -> s.cpuTempC?.let { OverlayValue(f0(it) + "°", tempLevel(it, 70f, 85f)) } ?: OverlayValue("--")
    OverlayMetric.GPU_LOAD -> s.gpuLoad?.let { OverlayValue("$it%", if (it < 85) 0 else if (it < 97) 1 else 2) } ?: OverlayValue("--")
    OverlayMetric.GPU_FREQ -> s.gpuMhz?.let { OverlayValue(ghz(it)) } ?: OverlayValue("--")
    OverlayMetric.GPU_TEMP -> s.gpuTempC?.let { OverlayValue(f0(it) + "°", tempLevel(it, 70f, 85f)) } ?: OverlayValue("--")
    OverlayMetric.RAM -> {
        val used = s.ramUsedKb
        val total = s.ramTotalKb
        if (used == null || total == null || total <= 0) OverlayValue("--")
        else OverlayValue(f1(used / 1048576f) + "G", if (used * 100 / total < 85) 0 else if (used * 100 / total < 93) 1 else 2)
    }
    OverlayMetric.BATT_TEMP -> s.battTempC?.let { OverlayValue(f0(it) + "°", tempLevel(it, 42f, 47f)) } ?: OverlayValue("--")
    OverlayMetric.BATT_LEVEL -> s.battLevel?.let { OverlayValue("$it%" + if (s.charging) "+" else "", if (it > 20) 0 else if (it > 10) 1 else 2) } ?: OverlayValue("--")
    OverlayMetric.POWER -> s.powerW?.let { OverlayValue((if (s.charging) "+" else "") + f1(it) + "W", if (s.charging || it < 6f) 0 else if (it < 9f) 1 else 2) } ?: OverlayValue("--")
    OverlayMetric.THERMAL -> s.thermalStatus?.let {
        OverlayValue(
            when (it) { 0 -> "OK"; 1 -> "LIGHT"; 2 -> "WARM"; 3 -> "HOT"; 4 -> "CRIT"; else -> "!!!" },
            when { it <= 1 -> 0; it == 2 -> 1; else -> 2 },
        )
    } ?: OverlayValue("--")
    OverlayMetric.NET -> s.netBytesPerSec?.let {
        OverlayValue(
            when {
                it >= 1_048_576 -> f1(it / 1048576f) + "M"
                it >= 1024 -> f0(it / 1024f) + "K"
                else -> "${it}B"
            }
        )
    } ?: OverlayValue("--")
    OverlayMetric.PING -> s.pingMs?.let { OverlayValue("${it}ms", if (it < 80) 0 else if (it < 150) 1 else 2) } ?: OverlayValue("--")
    OverlayMetric.CLOCK -> OverlayValue(s.clock.ifEmpty { "--:--" })
    OverlayMetric.SESSION -> {
        val t = s.sessionSec.coerceAtLeast(0)
        OverlayValue(
            if (t >= 3600) String.format(Locale.US, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
            else String.format(Locale.US, "%02d:%02d", t / 60, t % 60)
        )
    }
}
