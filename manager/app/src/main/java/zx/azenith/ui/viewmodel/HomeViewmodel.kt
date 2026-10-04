/*
 * Copyright (C) 2026-2027 Zexshia
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

package zx.azenith.ui.viewmodel


import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zx.azenith.R
import zx.azenith.ui.util.DeviceMonitor
import zx.azenith.ui.util.DeviceProfile
import zx.azenith.ui.util.LiveStats
import zx.azenith.ui.util.RootUtils
import zx.azenith.ui.util.isBannerImageEnabled
import zx.azenith.ui.util.PropertyUtils


data class HomeUiState(
    val isBannerEnabled: Boolean = false,
    val moduleInstalled: Boolean = false,
    val autoMode: String? = null,
    val rootStatus: Boolean = false,
    val serviceStatusRes: Int = R.string.status_suspended,
    val servicePid: String = "",
    val currentProfileRes: Int = R.string.status_initializing,
    val currentProfileValue: String = "",
    val runningGamePkg: String? = null,
    val runningGameStartTime: String? = null,
    val moduleVersion: String = "",
    val deviceProfile: DeviceProfile = DeviceProfile(),
    val profileLoaded: Boolean = false,
    val live: LiveStats = LiveStats(),
    /** Recent battery current samples in mA, oldest first, for the Monitor chart. */
    val currentHistory: List<Int> = emptyList()
)


class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enable_banner_image") {
            _uiState.value = _uiState.value.copy(isBannerEnabled = context.isBannerImageEnabled())
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        _uiState.value = _uiState.value.copy(isBannerEnabled = context.isBannerImageEnabled())
        
        observeRootUtils()
        fetchInitialSystemData()
    }

    private fun observeRootUtils() {
        viewModelScope.launch(Dispatchers.IO) {
            RootUtils.observeServiceStatusRes().collect { (statusRes, pid) ->
                _uiState.value = _uiState.value.copy(serviceStatusRes = statusRes, servicePid = pid)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            RootUtils.observeProfileRes().collect { profileRes ->
                _uiState.value = _uiState.value.copy(currentProfileRes = profileRes)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            RootUtils.observeProfileValue().collect { value ->
                _uiState.value = _uiState.value.copy(currentProfileValue = value)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            RootUtils.observeGameInfo().collect { info ->
                _uiState.value = _uiState.value.copy(
                    runningGamePkg = info.pkg,
                    runningGameStartTime = info.startTime
                )
            }
        }
    }


    private fun fetchInitialSystemData() {
        viewModelScope.launch(Dispatchers.IO) {
            val isRooted = RootUtils.requestRootAccess()
            val isModuleInstalled = RootUtils.isModuleInstalled()
            val mode = PropertyUtils.get("persist.sys.azenithconf.AIenabled")

            _uiState.value = _uiState.value.copy(
                rootStatus = isRooted,
                moduleInstalled = isModuleInstalled,
                autoMode = mode
            )

            val version = RootUtils.readRootFile("/data/adb/modules/nextcore/module.prop")
                ?.lineSequence()?.firstOrNull { it.startsWith("version=") }
                ?.substringAfter('=')?.trim().orEmpty()
            val profile = DeviceMonitor.loadProfile()
            _uiState.value = _uiState.value.copy(
                moduleVersion = version,
                deviceProfile = profile,
                profileLoaded = true
            )
        }
    }

    /** One live-monitoring sample. The Home screen calls this in a loop while visible. */
    suspend fun pollLiveStats() {
        val state = _uiState.value
        if (!state.profileLoaded) return
        val stats = kotlinx.coroutines.withContext(Dispatchers.IO) {
            DeviceMonitor.sample(context, state.deviceProfile)
        }
        kotlinx.coroutines.withContext(Dispatchers.IO) { refreshProfile() }
        val history = stats.batteryCurrentMa?.let { (_uiState.value.currentHistory + it).takeLast(48) }
            ?: _uiState.value.currentHistory
        _uiState.value = _uiState.value.copy(live = stats, currentHistory = history)
    }

    fun setAutoMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val v = if (enabled) "1" else "0"
            Shell.cmd(
                "setprop persist.sys.azenithconf.AIenabled $v",
                "echo $v > /data/adb/.config/AZenith/API/current_modes"
            ).exec()
            _uiState.value = _uiState.value.copy(autoMode = v)
        }
    }

    fun restartService(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.azenith-service --rerun").exec()
            viewModelScope.launch(Dispatchers.Main) { onDone() }
        }
    }

    fun applyProfile(profileReason: String, onSuccess: () -> Unit) {
        if (profileReason !in setOf("1", "2", "3")) return
        // Show the choice right away; the daemon's file is re-read below to confirm it.
        _uiState.value = _uiState.value.copy(currentProfileValue = profileReason)
        viewModelScope.launch(Dispatchers.IO) {
            Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.azenith-service -p $profileReason").exec()
            viewModelScope.launch(Dispatchers.Main) { onSuccess() }
            kotlinx.coroutines.delay(1500)
            refreshProfile()
        }
    }

    /** Re-read the active profile from the daemon (the file observer can miss writes). */
    private fun refreshProfile() {
        // The daemon's own file is the source of truth; the app mirror can lag behind it.
        val value = RootUtils.readRootFile("/data/adb/.config/AZenith/API/current_profile")?.trim().orEmpty()
        if (value.isNotEmpty() && value != _uiState.value.currentProfileValue) {
            _uiState.value = _uiState.value.copy(
                currentProfileValue = value,
                currentProfileRes = RootUtils.profileResFor(value)
            )
        }
    }

    fun rebootDevice(reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cmd = when (reason) {
                "" -> "svc power reboot"
                "soft_reboot" -> "killall system_server"
                "recovery" -> "/system/bin/input keyevent 26 && svc power reboot $reason || reboot $reason"
                else -> "svc power reboot $reason || reboot $reason"
            }
            Shell.cmd(cmd).submit()
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }
    


    fun refreshAiMode() {
        viewModelScope.launch(Dispatchers.IO) {
            val mode = PropertyUtils.get("persist.sys.azenithconf.AIenabled")
            _uiState.value = _uiState.value.copy(autoMode = mode)
        }
    }

}

