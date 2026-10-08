package zx.nextcore.ui.util

import java.util.Calendar

/** One 10-second sample written by PerfRecorder. */
data class PerfPoint(
    val epoch: Long,
    val tempC: Float?,
    val cpuMhz: Int,
    val fps: Float?,
    val pkg: String?,
)

/** One finished game session written by PerfRecorder. */
data class GameSession(
    val pkg: String,
    val start: Long,
    val end: Long,
    val avgTempC: Float?,
    val maxTempC: Float?,
    val avgMhz: Int,
    val avgFps: Float?,
) {
    val seconds: Long get() = (end - start).coerceAtLeast(0)
}

/** Totals for one game across all recorded sessions. */
data class GameTotals(
    val pkg: String,
    val sessions: Int,
    val seconds: Long,
    val avgFps: Float?,
    val maxTempC: Float?,
)

data class TodayStats(
    val playSeconds: Long,
    val avgMhz: Int?,
    val avgTempC: Float?,
    val maxTempC: Float?,
)

/** Reads the files PerfRecorder keeps under the module config (root). */
object PerfData {
    private const val HISTORY = "/data/adb/.config/NextCore/API/perf_history"
    private const val SESSIONS = "/data/adb/.config/NextCore/API/sessions"

    private fun num(s: String): Float? = s.takeIf { it != "-" }?.toFloatOrNull()

    /** Samples from the last [minutes], oldest first. Blocking; call off the main thread. */
    fun history(minutes: Int = 30): List<PerfPoint> {
        val since = System.currentTimeMillis() / 1000 - minutes * 60L
        return RootUtils.readRootFile(HISTORY).orEmpty().lineSequence().mapNotNull { line ->
            val p = line.trim().split(' ')
            if (p.size < 5) return@mapNotNull null
            val epoch = p[0].toLongOrNull() ?: return@mapNotNull null
            PerfPoint(epoch, num(p[1]), p[2].toIntOrNull() ?: 0, num(p[3]), p[4].takeIf { it != "-" })
        }.filter { it.epoch >= since }.toList()
    }

    /** Finished sessions, newest first. Blocking; call off the main thread. */
    fun sessions(): List<GameSession> =
        RootUtils.readRootFile(SESSIONS).orEmpty().lineSequence().mapNotNull { line ->
            val p = line.trim().split('|')
            if (p.size < 7) return@mapNotNull null
            GameSession(
                pkg = p[0],
                start = p[1].toLongOrNull() ?: return@mapNotNull null,
                end = p[2].toLongOrNull() ?: return@mapNotNull null,
                avgTempC = num(p[3]),
                maxTempC = num(p[4]),
                avgMhz = p[5].toIntOrNull() ?: 0,
                avgFps = num(p[6]),
            )
        }.toList().asReversed()

    fun totals(sessions: List<GameSession>): List<GameTotals> =
        sessions.groupBy { it.pkg }.map { (pkg, list) ->
            val fps = list.mapNotNull { it.avgFps }
            GameTotals(
                pkg = pkg,
                sessions = list.size,
                seconds = list.sumOf { it.seconds },
                avgFps = fps.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
                maxTempC = list.mapNotNull { it.maxTempC }.maxOrNull(),
            )
        }.sortedByDescending { it.seconds }

    /** Today's play time from sessions, and temperature/clock from today's samples. */
    fun today(sessions: List<GameSession>, history: List<PerfPoint>): TodayStats {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis / 1000
        val todays = sessions.filter { it.end >= midnight }
        val play = todays.sumOf { it.end - maxOf(it.start, midnight) }
        val temps = history.mapNotNull { it.tempC }
        val mhz = history.map { it.cpuMhz }.filter { it > 0 }
        return TodayStats(
            playSeconds = play,
            avgMhz = mhz.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            avgTempC = temps.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                ?: todays.mapNotNull { it.avgTempC }.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            maxTempC = (temps + todays.mapNotNull { it.maxTempC }).maxOrNull(),
        )
    }

    fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "%d:%02d h".format(h, m) else "%d min".format(m)
    }
}
