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
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import zx.nextcore.MainActivity
import zx.nextcore.R
import zx.nextcore.ui.util.DeviceMonitor
import zx.nextcore.ui.util.DeviceProfile
import zx.nextcore.ui.util.RootUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt


/** Floating monitor preferences, kept in the app's "settings" SharedPreferences. */
object OverlayPrefs {
    const val ENABLED = "overlay_enabled"
    const val GAMES_ONLY = "overlay_games_only"
    const val SHOW_FPS = "overlay_show_fps"
    const val SHOW_CPU_TEMP = "overlay_show_cpu_temp"
    const val SHOW_GPU = "overlay_show_gpu"
    const val SHOW_RAM = "overlay_show_ram"
    const val SHOW_BATT_TEMP = "overlay_show_batt_temp"
    const val VERTICAL = "overlay_vertical"
    const val TEXT_SIZE = "overlay_text_size"
    const val OPACITY = "overlay_opacity"
    const val INTERVAL_MS = "overlay_interval_ms"
    const val POS_X = "overlay_x"
    const val POS_Y = "overlay_y"

    /** How often the Home and Monitor tabs refresh, in ms. Not an overlay setting, but set on the same page. */
    const val MONITOR_INTERVAL_MS = "monitor_interval_ms"
    const val DEFAULT_MONITOR_INTERVAL_MS = 1500L

    val textSizesSp = listOf(11f, 13f, 15f)
    val intervalsMs = listOf(500L, 1000L, 2000L)

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
}

/**
 * Draws a small always-on-top panel with FPS, temperatures and RAM over other
 * apps, like RvSystem Monitor's floating overlay. FPS needs root (it asks
 * SurfaceFlinger for its frame counter); the rest is read the same way the
 * Monitor tab reads it. Drag the panel to move it; the position is saved.
 */
class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "floating_monitor"
        private const val NOTIFICATION_ID = 7301
        private const val ACTION_STOP = "zx.nextcore.overlay.STOP"

        @Volatile
        var isRunning = false
            private set

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

        /**
         * Grants "display over other apps" through root when possible, which
         * saves a trip to system settings. Blocking; call from Dispatchers.IO.
         */
        fun grantWithRoot(context: Context): Boolean {
            if (canDraw(context)) return true
            Shell.cmd("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow").exec()
            return canDraw(context)
        }

        fun permissionIntent(context: Context): Intent =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun start(context: Context) {
            if (!canDraw(context)) return
            ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: SharedPreferences
    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var profile = DeviceProfile()
    private var gameRunning = false
    private var lastFrames: Long? = null
    private var lastFramesAt = 0L

    private val fpsView by lazy { metricView() }
    private val cpuView by lazy { metricView() }
    private val gpuView by lazy { metricView() }
    private val ramView by lazy { metricView() }
    private val battView by lazy { metricView() }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("overlay_") && key != OverlayPrefs.POS_X && key != OverlayPrefs.POS_Y) {
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
        addPanel()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        scope.launch(Dispatchers.IO) { profile = DeviceMonitor.loadProfile() }
        scope.launch {
            RootUtils.observeGameInfo().collect { info ->
                gameRunning = !info.pkg.isNullOrEmpty()
                updateVisibility()
            }
        }
        scope.launch { pollLoop() }
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
        super.onDestroy()
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

    private fun metricView() = TextView(this).apply {
        setTextColor(Color.WHITE)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        // Numbers and units read left to right in every language.
        textDirection = View.TEXT_DIRECTION_LTR
        setShadowLayer(dp(1.5f).toFloat(), 0f, 0f, Color.BLACK)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addPanel() {
        val layout = LinearLayout(this).apply {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            listOf(fpsView, cpuView, gpuView, ramView, battView).forEach { addView(it) }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Absolute LEFT so the saved position means the same thing in RTL languages.
            gravity = Gravity.TOP or Gravity.LEFT
            x = prefs.getInt(OverlayPrefs.POS_X, dp(16f))
            y = prefs.getInt(OverlayPrefs.POS_Y, dp(64f))
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        layout.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (startX + (e.rawX - downX)).roundToInt().coerceAtLeast(0)
                    lp.y = (startY + (e.rawY - downY)).roundToInt().coerceAtLeast(0)
                    runCatching { windowManager.updateViewLayout(v, lp) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    prefs.edit().putInt(OverlayPrefs.POS_X, lp.x).putInt(OverlayPrefs.POS_Y, lp.y).apply()
                    true
                }
                else -> false
            }
        }

        panel = layout
        params = lp
        applyStyle()
        windowManager.addView(layout, lp)
        updateVisibility()
    }

    /** Re-reads the look settings: orientation, text size, background opacity and which metrics show. */
    private fun applyStyle() {
        val layout = panel ?: return
        val vertical = prefs.getBoolean(OverlayPrefs.VERTICAL, false)
        layout.orientation = if (vertical) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        val alpha = (prefs.getInt(OverlayPrefs.OPACITY, 55).coerceIn(0, 100) * 255 / 100)
        layout.background = GradientDrawable().apply {
            cornerRadius = dp(12f).toFloat()
            setColor(Color.argb(alpha, 0, 0, 0))
        }
        layout.setPadding(dp(10f), dp(6f), dp(10f), dp(6f))

        val size = OverlayPrefs.textSizesSp[prefs.getInt(OverlayPrefs.TEXT_SIZE, 1).coerceIn(0, 2)]
        val shown = mapOf(
            fpsView to prefs.getBoolean(OverlayPrefs.SHOW_FPS, true),
            cpuView to prefs.getBoolean(OverlayPrefs.SHOW_CPU_TEMP, true),
            gpuView to prefs.getBoolean(OverlayPrefs.SHOW_GPU, false),
            ramView to prefs.getBoolean(OverlayPrefs.SHOW_RAM, true),
            battView to prefs.getBoolean(OverlayPrefs.SHOW_BATT_TEMP, true),
        )
        var first = true
        shown.forEach { (view, on) ->
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            view.visibility = if (on) View.VISIBLE else View.GONE
            val gap = if (first || !on) 0 else dp(10f)
            view.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (vertical) topMargin = gap / 3 else leftMargin = gap }
            if (on) first = false
        }
    }

    private fun updateVisibility() {
        val layout = panel ?: return
        val gamesOnly = prefs.getBoolean(OverlayPrefs.GAMES_ONLY, false)
        layout.visibility = if (!gamesOnly || gameRunning) View.VISIBLE else View.GONE
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            val layout = panel
            if (layout != null && layout.visibility == View.VISIBLE) {
                val wantFps = prefs.getBoolean(OverlayPrefs.SHOW_FPS, true)
                val sample = withContext(Dispatchers.IO) { DeviceMonitor.sampleOverlay(profile, wantFps) }
                render(sample)
            } else {
                // Restart FPS averaging when the panel comes back.
                lastFrames = null
            }
            delay(prefs.getLong(OverlayPrefs.INTERVAL_MS, 1000L).coerceIn(250L, 5000L))
        }
    }

    private fun render(s: DeviceMonitor.OverlaySample) {
        val now = SystemClock.elapsedRealtime()
        val fps = s.frameCount?.let { frames ->
            val prev = lastFrames
            val dt = (now - lastFramesAt) / 1000f
            lastFrames = frames
            lastFramesAt = now
            if (prev == null || dt <= 0f || frames < prev) null else ((frames - prev) / dt).roundToInt()
        }
        fpsView.text = metric("FPS", fps?.toString() ?: "--")
        cpuView.text = metric("CPU", s.cpuTempC?.let { String.format(Locale.US, "%.0f°C", it) } ?: "--")
        gpuView.text = metric("GPU", s.gpuLoad?.let { "$it%" } ?: "--")
        val total = s.memTotalKb
        val avail = s.memAvailKb
        ramView.text = metric(
            "RAM",
            if (total != null && avail != null && total > 0) "${((total - avail) * 100 / total)}%" else "--"
        )
        battView.text = metric("BAT", batteryTemp()?.let { String.format(Locale.US, "%.0f°C", it) } ?: "--")
    }

    private fun batteryTemp(): Float? {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val raw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return if (raw == Int.MIN_VALUE || abs(raw) > 2000) null else raw / 10f
    }

    /** "FPS 60" with the label in the accent color and the value in bold white. */
    private fun metric(label: String, value: String): CharSequence =
        SpannableStringBuilder().apply {
            append(label, ForegroundColorSpan(ACCENT), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(" ")
            append(value, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
}

/** Coral, matching the app's default accent. */
private const val ACCENT = 0xFFFF8A65.toInt()
