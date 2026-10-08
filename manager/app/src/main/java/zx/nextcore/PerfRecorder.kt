package zx.nextcore

import android.os.IBinder
import android.os.Parcel
import java.io.File

/**
 * Records performance history and game sessions for the app's dashboard.
 *
 * Runs on a daemon thread inside the root AppMonitor process. Every
 * [SAMPLE_MS] it writes one line to [HISTORY] (`epoch tempC cpuMhz fps pkg`)
 * and, when a game from the daemon's gameinfo ends, one line to [SESSIONS]
 * (`pkg|start|end|avgTemp|maxTemp|avgMhz|avgFps`). Both files are trimmed so
 * they stay small, and lines are appended rather than rewritten to spare flash.
 */
object PerfRecorder {
    private const val API = "/data/adb/.config/NextCore/API"
    const val HISTORY = "$API/perf_history"
    const val SESSIONS = "$API/sessions"
    private const val GAME_INFO = "$API/gameinfo"
    private const val DEVICE_PROFILE = "/data/adb/.config/NextCore/device_profile"

    private const val SAMPLE_MS = 10_000L
    /** 30 minutes of samples; the file is trimmed back to this when it doubles. */
    private const val HISTORY_KEEP = 180
    private const val SESSIONS_KEEP = 300
    /** Sessions shorter than this are app switches, not play. */
    private const val MIN_SESSION_S = 60L

    private var sfBinder: IBinder? = null
    private var lastFrames: Long? = null
    private var lastFramesAt = 0L

    private class Session(val pkg: String, val start: Long) {
        var temps = 0.0
        var maxTemp = 0.0
        var mhz = 0.0
        var fps = 0.0
        var n = 0
        var fpsN = 0
    }

    private var session: Session? = null

    fun start() {
        Thread({ loop() }, "perf-recorder").apply { isDaemon = true }.start()
    }

    private fun loop() {
        val tempPath = readKey(DEVICE_PROFILE, "cpu_temp_path")
        while (!Thread.currentThread().isInterrupted) {
            try {
                sample(tempPath)
                Thread.sleep(SAMPLE_MS)
            } catch (_: InterruptedException) {
                break
            } catch (t: Throwable) {
                t.printStackTrace()
                try { Thread.sleep(SAMPLE_MS) } catch (_: InterruptedException) { break }
            }
        }
        endSession(System.currentTimeMillis() / 1000)
    }

    private fun sample(tempPath: String) {
        val now = System.currentTimeMillis() / 1000
        val temp = readTemp(tempPath)
        val mhz = avgCpuMhz()
        val fps = readFps()
        val pkg = currentGame()

        File(API).mkdirs()
        appendTrimmed(
            File(HISTORY),
            "$now ${fmt(temp)} $mhz ${fps?.let { fmt(it) } ?: "-"} ${pkg ?: "-"}",
            HISTORY_KEEP
        )

        val s = session
        if (s != null && s.pkg != pkg) endSession(now)
        if (pkg != null && session == null) session = Session(pkg, now)
        session?.let {
            if (temp != null) {
                it.temps += temp
                it.maxTemp = maxOf(it.maxTemp, temp)
            }
            it.mhz += mhz
            if (fps != null && fps > 0) {
                it.fps += fps
                it.fpsN++
            }
            it.n++
        }
    }

    private fun endSession(now: Long) {
        val s = session ?: return
        session = null
        if (now - s.start < MIN_SESSION_S || s.n == 0) return
        val line = listOf(
            s.pkg, s.start, now,
            fmt(s.temps / s.n), fmt(s.maxTemp),
            (s.mhz / s.n).toLong(),
            if (s.fpsN > 0) fmt(s.fps / s.fpsN) else "-"
        ).joinToString("|")
        appendTrimmed(File(SESSIONS), line, SESSIONS_KEEP)
    }

    /** Appends a line; when the file holds twice [keep] lines, keeps the newest [keep]. */
    private fun appendTrimmed(file: File, line: String, keep: Int) {
        file.appendText(line + "\n")
        val lines = file.readLines()
        if (lines.size > keep * 2) {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(lines.takeLast(keep).joinToString("\n", postfix = "\n"))
            tmp.renameTo(file)
        }
    }

    private fun currentGame(): String? {
        val first = runCatching { File(GAME_INFO).readLines().firstOrNull() }.getOrNull() ?: return null
        val pkg = first.substringBefore(' ').trim()
        // The daemon writes "(null)" while no game is in the foreground.
        return pkg.takeIf { it.isNotEmpty() && it.lowercase() !in setOf("null", "(null)", "none") }
    }

    private fun readTemp(path: String): Double? {
        if (path.isEmpty()) return null
        val raw = runCatching { File(path).readText().trim().toDouble() }.getOrNull() ?: return null
        val c = if (raw > 1000) raw / 1000 else raw
        return c.takeIf { it in 1.0..150.0 }
    }

    private fun avgCpuMhz(): Long {
        val freqs = (0 until 16).mapNotNull { cpu ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_cur_freq").readText().trim().toLong()
            }.getOrNull()
        }
        return if (freqs.isEmpty()) 0 else freqs.average().toLong() / 1000
    }

    /**
     * Composited frames per second from SurfaceFlinger's page-flip counter
     * (transaction 1013, the same one `service call SurfaceFlinger 1013` reads).
     * Null until two readings exist or when the call is refused.
     */
    private fun readFps(): Double? {
        val binder = sfBinder ?: runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "SurfaceFlinger") as? IBinder
        }.getOrNull()?.also { sfBinder = it } ?: return null

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        val frames = try {
            data.writeInterfaceToken("android.ui.ISurfaceComposer")
            if (!binder.transact(1013, data, reply, 0)) return null
            reply.readInt().toLong() and 0xffffffffL
        } catch (_: Throwable) {
            sfBinder = null
            return null
        } finally {
            data.recycle()
            reply.recycle()
        }

        val at = System.nanoTime()
        val prev = lastFrames
        val prevAt = lastFramesAt
        lastFrames = frames
        lastFramesAt = at
        if (prev == null || frames < prev) return null
        val seconds = (at - prevAt) / 1e9
        return if (seconds > 0) (frames - prev) / seconds else null
    }

    private fun readKey(file: String, key: String): String =
        runCatching {
            File(file).readLines().firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.trim()
        }.getOrNull().orEmpty()

    private fun fmt(v: Double?): String = v?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "-"
}
