package zx.nextcore.ui.hud

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.nextcore.R
import zx.nextcore.ui.component.ConfirmDialogHost
import zx.nextcore.ui.component.rememberConfirmDialog
import zx.nextcore.ui.util.dumpDiagnosticLogs
import zx.nextcore.ui.util.getShareLogIntent
import zx.nextcore.ui.viewmodel.HomeViewModel
import zx.nextcore.ui.viewmodel.SettingsViewModel
import zx.nextcore.ui.viewmodel.TweakViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HudSettingsScreen(
    navController: NavController,
    vm: SettingsViewModel = viewModel(),
    homeVm: HomeViewModel = viewModel(),
    tweakVm: TweakViewModel = viewModel(),
) {
    val context = LocalContext.current
    val ui by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { if (message != null) { delay(3000); message = null } }

    val uninstallDialog = rememberConfirmDialog(onConfirm = {
        Shell.cmd("sh /data/adb/modules/nextcore/uninstall.sh").submit()
        message = context.getString(R.string.hud_uninstall_done)
    })

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            val ok = tweakVm.createConfigFileBackup(context, uri, true, true)
            message = context.getString(if (ok) R.string.dialog_backup_success else R.string.dialog_backup_fail)
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = tweakVm.validateAndRestoreFile(context, uri)
            val data = result.data
            if (result.isValid && data != null) {
                tweakVm.applyRestoreData(context, data, result.hasTweaks, result.hasApplist)
                tweakVm.loadAllConfiguration(context)
                message = context.getString(R.string.dialog_backup_success)
            } else {
                message = result.message
            }
        }
    }

    HudPage(title = stringResource(R.string.hud_nav_settings)) {
        if (message != null) item(key = "msg") { HudNote(message!!) }

        item(key = "theme") {
            HudGroup(rows = listOf({
                HudRow(
                    stringResource(R.string.hud_theme), icon = Icons.Rounded.Palette,
                    subtitle = stringResource(R.string.hud_theme_sub),
                    onClick = { navController.navigate("color_palette") },
                )
            }))
        }

        item(key = "h_features") { HudSectionTitle(stringResource(R.string.hud_features)) }
        item(key = "features") {
            // SettingsUiState.autoMode is true when auto mode is OFF (AIenabled == 0).
            HudGroup(rows = listOf(
                { HudRow(stringResource(R.string.hud_auto), icon = Icons.Rounded.AutoMode, subtitle = stringResource(R.string.hud_auto_sub), trailing = { HudSwitch(!ui.autoMode, { vm.setAutoMode(!it); homeVm.refreshAiMode() }) }) },
                { HudRow(stringResource(R.string.hud_grace), icon = Icons.Rounded.Timer, subtitle = stringResource(R.string.hud_grace_sub), trailing = { HudSwitch(ui.profileTimeout, vm::setProfileTimeout) }) },
                { HudRow(stringResource(R.string.hud_toast), icon = Icons.Rounded.Mail, trailing = { HudSwitch(ui.stateToast, vm::setShowToast) }) },
                { HudRow(stringResource(R.string.hud_notif), icon = Icons.Rounded.Notifications, trailing = { HudSwitch(ui.profileNotifications, vm::setProfileNotifications) }) },
                { HudRow(stringResource(R.string.hud_disable_tweaks), icon = Icons.Rounded.Block, subtitle = stringResource(R.string.hud_disable_tweaks_sub), trailing = { HudSwitch(ui.disableTweak, vm::setDisableTweak) }) },
            ))
        }

        item(key = "h_tools") { HudSectionTitle(stringResource(R.string.hud_tools)) }
        item(key = "tools") {
            HudGroup(rows = listOf(
                {
                    HudRow(stringResource(R.string.hud_restart), icon = Icons.Rounded.RestartAlt, onClick = {
                        message = context.getString(R.string.hud_restarting)
                        homeVm.restartService {}
                    })
                },
                {
                    HudRow(stringResource(R.string.hud_save_log), icon = Icons.Rounded.Download, subtitle = stringResource(R.string.hud_save_log_sub), onClick = {
                        scope.launch { shareLogs(context) { message = it } }
                    })
                },
                {
                    HudRow(stringResource(R.string.hud_backup), icon = Icons.Rounded.SwapVert, onClick = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        backupLauncher.launch("NextCore_Backup_$stamp.ncbak")
                    }, trailing = {
                        HudValueChip(stringResource(R.string.hud_restore), active = true) { restoreLauncher.launch(arrayOf("*/*")) }
                    })
                },
                { HudRow(stringResource(R.string.hud_verbose), icon = Icons.Rounded.BugReport, trailing = { HudSwitch(ui.debugMode, vm::setDebugMode) }) },
            ))
        }

        item(key = "h_about") { HudSectionTitle(stringResource(R.string.hud_about)) }
        item(key = "about") {
            HudCard(Modifier.fillMaxWidth(), onClick = { navController.navigate("aboutscreen") }) {
                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                    NcLogo(40.dp)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                    androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                        Text("NextCore", color = Hud.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(appVersion(context), color = Hud.muted, fontSize = 12.sp)
                    }
                    Text("›", color = Hud.muted, fontSize = 18.sp)
                }
            }
        }
        item(key = "uninstall") {
            Box(
                Modifier.fillMaxWidth().clip(Hud.cardShape).background(Hud.card)
                    .clickable {
                        uninstallDialog.showConfirm(
                            title = context.getString(R.string.uninstall),
                            content = context.getString(R.string.uninstall_confirm_content),
                        )
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.hud_uninstall), color = Hud.red, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
    ConfirmDialogHost(handle = uninstallDialog)
}

private suspend fun shareLogs(context: Context, onError: (String) -> Unit) {
    val file = dumpDiagnosticLogs(context, saveToDownloads = false)
    if (file == null) {
        onError(context.getString(R.string.toast_log_gather_fail))
        return
    }
    withContext(Dispatchers.Main) {
        runCatching { context.startActivity(getShareLogIntent(context, file).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun appVersion(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "v${info.versionName} (${info.longVersionCode})"
}.getOrDefault("")
