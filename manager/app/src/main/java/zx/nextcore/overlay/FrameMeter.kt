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

import com.topjohnwu.superuser.Shell
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Live frame rate of the app on screen, read from SurfaceFlinger.
 *
 * The main method asks SurfaceFlinger for the present timestamps of the
 * focused app's layer (`dumpsys SurfaceFlinger --latency <layer>`). Those are
 * the real times each frame reached the screen, so besides FPS they give the
 * frame time, the 1% low and stutters. When a ROM hides the layer, it falls
 * back to SurfaceFlinger's time stats (the method FrameX uses), and last to the
 * global frame counter.
 *
 * Every call runs on a dedicated root shell, so the floating monitor never
 * waits behind the app's other root commands. Not thread safe: use one
 * instance per poll loop and call [sample] from Dispatchers.IO.
 */
class FrameMeter : AutoCloseable {

    enum class Method { LAYER, TIMESTATS, COUNTER, NONE }

    data class Stats(
        val fps: Float? = null,
        /** Duration of the last frame, in ms. */
        val frameTimeMs: Float? = null,
        /** FPS of the slowest 1% of frames over the last few seconds. */
        val low1: Float? = null,
        /** Frames in the last second that took at least twice the usual time. */
        val jank: Int = 0,
        /** Package whose frames are counted. */
        val pkg: String? = null,
        val method: Method = Method.NONE,
    )

    private var shell: Shell? = null

    // Focused app and the layer that carries its frames.
    private var focusPkg: String? = null
    private var focusCheckedAt = 0L
    private var layer: String? = null
    private var layerCheckedAt = 0L

    // Present timestamps (ns, CLOCK_MONOTONIC) seen so far, oldest first.
    private val presents = ArrayDeque<Long>()
    private var lastPresent = 0L
    private var refreshNs = 16_666_667L
    private var layerMisses = 0

    // Fallbacks.
    private var timestatsOn = false
    private var lastTimestatsFps = 0f
    private var lastCounter: Long? = null
    private var lastCounterAt = 0L

    private fun sh(): Shell? {
        shell?.let { if (it.isAlive) return it }
        shell = runCatching { Shell.Builder.create().build() }.getOrNull()?.takeIf { it.isRoot }
        return shell
    }

    private fun run(cmd: String): List<String> {
        val s = sh() ?: return emptyList()
        val out = ArrayList<String>()
        runCatching { s.newJob().add(cmd).to(out).exec() }
        return out
    }

    /** Runs a shell command on the meter's root shell. Used by the sampler for its sysfs reads. */
    fun exec(cmd: String): List<String> = run(cmd)

    override fun close() {
        if (timestatsOn) runCatching { run("dumpsys SurfaceFlinger --timestats -disable") }
        runCatching { shell?.close() }
        shell = null
    }

    /** Blocking. [hintPkg] is the running game, if the daemon knows it. */
    fun sample(hintPkg: String? = null): Stats {
        val now = System.nanoTime()
        val pkg = hintPkg ?: focusedPackage(now)
        if (pkg != focusPkg) {
            focusPkg = pkg
            layer = null
            resetLayerHistory()
        }

        if (pkg != null) {
            if (layer == null && now - layerCheckedAt > 2_000_000_000L) {
                layerCheckedAt = now
                layer = findLayer(pkg)
            }
            layer?.let { name ->
                readLayer(name, now)?.let { return it.copy(pkg = pkg) }
                // The layer went away (activity changed); look again soon.
                if (++layerMisses >= 3) {
                    layer = null
                    layerMisses = 0
                    resetLayerHistory()
                }
            }
        }

        timestats()?.let { return Stats(fps = it, pkg = pkg, method = Method.TIMESTATS) }
        counter(now)?.let { return Stats(fps = it, pkg = pkg, method = Method.COUNTER) }
        return Stats(pkg = pkg)
    }

    private fun resetLayerHistory() {
        presents.clear()
        lastPresent = 0L
        layerMisses = 0
    }

    /* ---------- Focused app ---------- */

    // "mCurrentFocus=Window{1a2b u0 com.game.app/com.game.app.Main}"
    private val focusRegex = Regex("""u\d+\s+([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)[/}\s]""")

    private fun focusedPackage(now: Long): String? {
        if (now - focusCheckedAt < 2_000_000_000L) return focusPkg
        focusCheckedAt = now
        // The display section is small and fast; some ROMs only print the focus in the full dump.
        val grep = "grep -m 2 -E 'mCurrentFocus|mFocusedApp'"
        val out = run("dumpsys window displays 2>/dev/null | $grep").ifEmpty { run("dumpsys window 2>/dev/null | $grep") }
        return out.firstNotNullOfOrNull { focusRegex.find(it)?.groupValues?.get(1) }
    }

    /* ---------- Layer frames ---------- */

    private val safeLayer = Regex("""^[A-Za-z0-9._/$#()\[\]\- :@,]+$""")
    private val skipLayer = listOf("Background for", "Bounds for", "animation-leash", "Letterbox", "ActivityRecord", "WindowToken", "Task=", "Splash Screen")

    /**
     * Picks the layer of [pkg] that is actually drawing. Games draw into a
     * SurfaceView; normal apps into their window layer. Each candidate is
     * probed and the one with the newest frame wins.
     */
    private fun findLayer(pkg: String): String? {
        val candidates = run("dumpsys SurfaceFlinger --list 2>/dev/null")
            .map { it.trim() }
            .filter { it.contains(pkg) && safeLayer.matches(it) && skipLayer.none { s -> it.contains(s) } }
            .distinct()
            .sortedBy {
                when {
                    it.startsWith("SurfaceView") && it.contains("BLAST") -> 0
                    it.startsWith("SurfaceView") -> 1
                    it.contains("$pkg/") -> 2
                    else -> 3
                }
            }
            .take(4)
        var best: String? = null
        var bestPresent = 0L
        for (name in candidates) {
            val frames = parseLatency(run("dumpsys SurfaceFlinger --latency '$name' 2>/dev/null"))
            val newest = frames.maxOrNull() ?: continue
            if (frames.size >= 2 && newest > bestPresent) {
                best = name
                bestPresent = newest
            }
        }
        return best
    }

    /** Present timestamps from a `--latency` dump; also updates the refresh period. */
    private fun parseLatency(lines: List<String>): List<Long> {
        if (lines.isEmpty()) return emptyList()
        lines.first().trim().toLongOrNull()?.takeIf { it in 2_000_000L..100_000_000L }?.let { refreshNs = it }
        return lines.drop(1).mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            // Columns: desired present, actual present, frame ready. 0 and
            // INT64_MAX mark frames whose fence has not signalled yet.
            val actual = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            actual.takeIf { it > 0L && it < Long.MAX_VALUE / 2 }
        }.sorted()
    }

    private fun readLayer(name: String, now: Long): Stats? {
        val frames = parseLatency(run("dumpsys SurfaceFlinger --latency '$name' 2>/dev/null"))
        if (frames.isEmpty()) return null
        layerMisses = 0

        val fresh = frames.filter { it > lastPresent }
        // SurfaceFlinger keeps the last 127 frames. If every one of them is new
        // since the last poll, frames were dropped from our history in between.
        val gap = lastPresent != 0L && fresh.size == frames.size
        if (gap) presents.clear()
        fresh.forEach { presents.addLast(it) }
        lastPresent = presents.lastOrNull() ?: lastPresent
        // Keep five seconds for the 1% low.
        val keepFrom = lastPresent - 5_000_000_000L
        while (presents.isNotEmpty() && presents.first() < keepFrom) presents.removeFirst()
        if (presents.size < 2) return Stats(fps = 0f, method = Method.LAYER)

        // Present times share CLOCK_MONOTONIC with System.nanoTime(); if a ROM
        // uses another clock, measure against the newest frame instead.
        val end = if (abs(now - lastPresent) < 5_000_000_000L) now else lastPresent
        val since = end - 1_000_000_000L
        val window = presents.filter { it > since }
        val fps = when {
            // Nothing drawn for a second: the app is idle.
            window.isEmpty() -> 0f
            // History started inside this second (fresh layer or a gap).
            presents.first() > since && window.size >= 2 ->
                (window.size - 1) * 1e9f / (window.last() - window.first()).coerceAtLeast(1L)
            else -> window.size.toFloat()
        }

        val times = presents.zipWithNext { a, b -> (b - a) / 1e6f }.filter { it > 0f && it < 1000f }
        val lastTime = times.lastOrNull()
        val low1 = if (times.size >= 20) {
            val sorted = times.sorted()
            val p99 = sorted[((sorted.size - 1) * 0.99f).roundToInt()]
            1000f / p99
        } else null
        val periodMs = refreshNs / 1e6f
        val recent = window.zipWithNext { a, b -> (b - a) / 1e6f }
        val median = recent.sorted().getOrNull(recent.size / 2) ?: periodMs
        val jank = recent.count { it >= maxOf(median * 2f, periodMs * 2f) }

        return Stats(
            fps = fps.coerceIn(0f, 1000f / periodMs.coerceAtLeast(1f) + 5f),
            frameTimeMs = lastTime,
            low1 = low1?.coerceAtMost(fps.coerceAtLeast(1f)),
            jank = jank,
            method = Method.LAYER,
        )
    }

    /* ---------- Fallbacks ---------- */

    private val avgFps = Regex("""averageFPS\s*=\s*([0-9.]+)""")

    /**
     * SurfaceFlinger time stats: average FPS since the last clear. Cleared in
     * the first second of every three, so each reading covers at least two
     * seconds and never reads an empty window.
     */
    private fun timestats(): Float? {
        if (!timestatsOn) {
            if (run("dumpsys SurfaceFlinger --timestats -clear -enable 2>/dev/null; echo ok").none { it.contains("ok") }) return null
            timestatsOn = true
            return null
        }
        val fps = run("dumpsys SurfaceFlinger --timestats -dump 2>/dev/null")
            .firstNotNullOfOrNull { avgFps.find(it)?.groupValues?.get(1)?.toFloatOrNull() }
        if (fps != null && fps > 0f) lastTimestatsFps = fps
        if (System.currentTimeMillis() % 3000L < 1000L) run("dumpsys SurfaceFlinger --timestats -clear -enable 2>/dev/null")
        return lastTimestatsFps.takeIf { it > 0f }
    }

    private val parcelWord = Regex("""Parcel\(\s*([0-9a-fA-F]{8})""")

    /** SurfaceFlinger's composited frame counter (transaction 1013); only old ROMs answer it. */
    private fun counter(now: Long): Float? {
        val frames = run("service call SurfaceFlinger 1013 2>/dev/null")
            .firstNotNullOfOrNull { parcelWord.find(it)?.groupValues?.get(1)?.toLongOrNull(16) } ?: return null
        val prev = lastCounter
        val dt = (now - lastCounterAt) / 1e9f
        lastCounter = frames
        lastCounterAt = now
        return if (prev == null || dt <= 0f || frames < prev) null else (frames - prev) / dt
    }
}
