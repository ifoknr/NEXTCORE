package zx.nextcore.ui.hud

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import zx.nextcore.ui.component.AppIconCache
import zx.nextcore.ui.util.GameSession
import zx.nextcore.ui.util.PerfData
import zx.nextcore.ui.util.PerfPoint
import zx.nextcore.ui.viewmodel.HomeViewModel
import java.util.Locale

const val CHANNEL_URL = "https://t.me/IFOKNR1"
const val SOURCE_URL = "https://github.com/ifoknr/NEXTCORE"

private const val LIVE_POLL_MS = 1500L

/** Polls live stats while [isVisible] and the app is in the foreground. */
@Composable
fun LiveStatsPoller(viewModel: HomeViewModel, isVisible: Boolean, profileLoaded: Boolean) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isVisible, profileLoaded) {
        if (!isVisible || !profileLoaded) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.pollLiveStats()
                delay(LIVE_POLL_MS)
            }
        }
    }
}

/** History and sessions recorded by the module, reloaded every 30 s while shown. */
data class PerfSnapshot(
    val history: List<PerfPoint> = emptyList(),
    val sessions: List<GameSession> = emptyList(),
    val loaded: Boolean = false,
)

@Composable
fun rememberPerfSnapshot(isVisible: Boolean = true): PerfSnapshot {
    var snap by remember { mutableStateOf(PerfSnapshot()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                snap = withContext(Dispatchers.IO) {
                    PerfSnapshot(PerfData.history(), PerfData.sessions(), loaded = true)
                }
                delay(30_000)
            }
        }
    }
    return snap
}

/** App icon by package name; a letter tile when the app is not installed. */
@Composable
fun HudAppIcon(pkg: String, size: Dp = 44.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bmp by remember(pkg) { mutableStateOf<ImageBitmap?>(AppIconCache.get(pkg)) }
    LaunchedEffect(pkg, px) {
        if (bmp == null) {
            bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    AppIconCache.loadIcon(pm, pm.getApplicationInfo(pkg, 0), px)
                }.getOrNull()
            }
        }
    }
    val b = bmp
    if (b != null) {
        Image(b, null, Modifier.size(size).clip(Hud.tileShape))
    } else {
        Box(Modifier.size(size).clip(Hud.tileShape).background(Hud.cardHi), contentAlignment = Alignment.Center) {
            Text(pkg.substringAfterLast('.').take(1).uppercase(Locale.ROOT), color = hudAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg.substringAfterLast('.'))

fun isInstalled(pm: PackageManager, pkg: String): Boolean =
    runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun launchApp(context: Context, pkg: String) {
    context.packageManager.getLaunchIntentForPackage(pkg)?.let {
        runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun ghz(khz: Long): String = String.format(Locale.US, "%.2f", khz / 1_000_000.0)
fun ghzShort(khz: Long): String = String.format(Locale.US, "%.1f", khz / 1_000_000.0)
fun one(v: Float): String = String.format(Locale.US, "%.0f", v)
fun gb(kb: Long): String = String.format(Locale.US, "%.1f GB", kb / 1_048_576.0)

/** "1:24 h" or "52 min" from seconds. */
fun duration(seconds: Long): String = PerfData.formatDuration(seconds)
