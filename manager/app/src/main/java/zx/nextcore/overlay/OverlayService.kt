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

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import zx.nextcore.MainActivity
import zx.nextcore.R
import zx.nextcore.ui.util.RootUtils
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Draws the floating monitor over other apps: FPS, frame times, clocks,
 * temperatures, battery and network, in the order and layout picked in the
 * monitor settings. Drag the panel to move it (portrait and landscape keep
 * their own spot); long-press it to switch between compact, minimal and
 * expanded without leaving the game.
 */
class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_monitor"
        private const val NOTIFICATION_ID = 7301
        private const val ACTION_STOP = "zx.nextcore.overlay.STOP"

        @Volatile
        var isRunning = false
            private set

        fun canDraw(context: android.content.Context): Boolean = Settings.canDrawOverlays(context)

        /**
         * Grants "display over other apps" through root when possible, which
         * saves a trip to system settings. Blocking; call from Dispatchers.IO.
         */
        fun grantWithRoot(context: android.content.Context): Boolean {
            if (canDraw(context)) return true
            Shell.cmd("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow").exec()
            return canDraw(context)
        }

        fun permissionIntent(context: android.content.Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun start(context: android.content.Context) {
            if (!canDraw(context)) return
            ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: SharedPreferences
    private lateinit var windowManager: WindowManager
    private var panel: OverlayPanelView? = null
    private var params: WindowManager.LayoutParams? = null
    private var sampler: OverlaySampler? = null
    private var gamePkg: String? = null
    private var visible: List<OverlayMetric> = emptyList()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("overlay_") && !key.startsWith("overlay_x") && !key.startsWith("overlay_y")) {
            applyStyle()
            updateVisibility()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = OverlayPrefs.prefs(this)
        windowManager = getSystemService(WindowManager::class.java)
        startAsForeground()
        if (!canDraw(this)) {
            prefs.edit().putBoolean(OverlayPrefs.ENABLED, false).apply()
            stopSelf()
            return
        }
        isRunning = true
        sampler = OverlaySampler(applicationContext)
        addPanel()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        scope.launch {
            RootUtils.observeGameInfo().collect { info ->
                gamePkg = info.pkg?.takeIf { it.isNotEmpty() }
                updateVisibility()
            }
        }
        scope.launch { pollLoop() }
        scope.launch { pingLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.edit().putBoolean(OverlayPrefs.ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        // Closing disables SurfaceFlinger time stats if the fallback turned them on.
        sampler?.let { s -> CoroutineScope(Dispatchers.IO).launch { s.close() } }
        sampler = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Portrait and landscape each remember where the panel was.
        val lp = params ?: return
        val (x, y) = savedPosition()
        lp.x = x
        lp.y = y
        panel?.post { clampAndUpdate() }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.nc_overlay_title), NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.avatar_transparent)
            .setContentTitle(getString(R.string.nc_overlay_title))
            .setContentText(getString(R.string.nc_overlay_notification))
            .setContentIntent(open)
            .addAction(0, getString(R.string.nc_overlay_stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).roundToInt()

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun savedPosition(): Pair<Int, Int> =
        if (isLandscape()) prefs.getInt(OverlayPrefs.POS_X_LAND, dp(24f)) to prefs.getInt(OverlayPrefs.POS_Y_LAND, dp(16f))
        else prefs.getInt(OverlayPrefs.POS_X, dp(16f)) to prefs.getInt(OverlayPrefs.POS_Y, dp(64f))

    private fun savePosition(x: Int, y: Int) {
        val (kx, ky) = if (isLandscape()) OverlayPrefs.POS_X_LAND to OverlayPrefs.POS_Y_LAND else OverlayPrefs.POS_X to OverlayPrefs.POS_Y
        prefs.edit().putInt(kx, x).putInt(ky, y).apply()
    }

    /** Keeps the panel fully on screen after a drag, a rotation or a size change. */
    private fun clampAndUpdate() {
        val view = panel ?: return
        val lp = params ?: return
        val (sw, sh) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        lp.x = lp.x.coerceIn(0, (sw - view.width).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(0, (sh - view.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addPanel() {
        val view = OverlayPanelView(this)
        val (x0, y0) = savedPosition()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Absolute LEFT so the saved position means the same thing in RTL languages.
            gravity = Gravity.TOP or Gravity.LEFT
            x = x0
            y = y0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val touchSlop = dp(6f)
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (dragging) return
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                prefs.edit().putString(OverlayPrefs.MODE, OverlayPrefs.mode(prefs).next().key).apply()
            }
        })
        view.setOnTouchListener { v, e ->
            gestures.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        lp.x = (startX + dx).roundToInt().coerceAtLeast(0)
                        lp.y = (startY + dy).roundToInt().coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(v, lp) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragging) {
                    clampAndUpdate()
                    savePosition(lp.x, lp.y)
                }
            }
            true
        }
        view.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) clampAndUpdate()
        }

        panel = view
        params = lp
        applyStyle()
        windowManager.addView(view, lp)
        updateVisibility()
    }

    /** Re-reads the look settings and which metrics show. */
    private fun applyStyle() {
        val view = panel ?: return
        visible = OverlayPrefs.visible(prefs)
        view.configure(
            metrics = visible,
            mode = OverlayPrefs.mode(prefs),
            scale = OverlayPrefs.scale(prefs),
            opacity = prefs.getInt(OverlayPrefs.OPACITY, 60),
            accent = OverlayPrefs.accent(prefs),
            colorCode = prefs.getBoolean(OverlayPrefs.COLOR_CODE, true),
            border = prefs.getBoolean(OverlayPrefs.BORDER, false),
            graph = prefs.getBoolean(OverlayPrefs.GRAPH, false),
        )
    }

    private fun updateVisibility() {
        val view = panel ?: return
        val gamesOnly = prefs.getBoolean(OverlayPrefs.GAMES_ONLY, false)
        view.visibility = if (!gamesOnly || gamePkg != null) View.VISIBLE else View.GONE
    }

    private fun wanted(): Set<OverlayMetric> = buildSet {
        addAll(visible)
        // The graph plots FPS even when the FPS number itself is hidden.
        if (prefs.getBoolean(OverlayPrefs.GRAPH, false)) add(OverlayMetric.FPS)
    }

    private suspend fun pollLoop() {
        val power = getSystemService(PowerManager::class.java)
        while (currentCoroutineContext().isActive) {
            val view = panel
            val s = sampler
            if (view != null && s != null && view.visibility == View.VISIBLE && power?.isInteractive != false) {
                val want = wanted()
                val pkg = gamePkg
                val state = withContext(Dispatchers.IO) { runCatching { s.sample(want, pkg) }.getOrNull() }
                if (state != null) view.state = state
            }
            delay(OverlayPrefs.interval(prefs))
        }
    }

    private suspend fun pingLoop() {
        while (currentCoroutineContext().isActive) {
            val s = sampler
            if (s != null && OverlayMetric.PING in visible && panel?.visibility == View.VISIBLE) {
                s.pingMs = withContext(Dispatchers.IO) { s.ping() }
            }
            delay(2000)
        }
    }
}
