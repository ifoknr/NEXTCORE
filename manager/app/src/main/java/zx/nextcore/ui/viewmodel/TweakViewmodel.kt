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

package zx.nextcore.ui.viewmodel


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import com.topjohnwu.superuser.io.SuFileOutputStream
import zx.nextcore.ui.util.RootUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zx.nextcore.R
import zx.nextcore.RefreshRateReceiver
import zx.nextcore.ui.util.BackupManager
import zx.nextcore.ui.util.PropertyUtils


class TweakViewModel : ViewModel() {
    data class ValidationResult(
        val isValid: Boolean, 
        val message: String, 
        val hasTweaks: Boolean, 
        val hasApplist: Boolean,
        val socType: String?,
        val data: Map<String, String>?
    )
    
    var isUiLoaded by mutableStateOf(false)
        private set


    var liteState by mutableStateOf<Boolean?>(null)
    var availableGovernors by mutableStateOf<List<String>?>(null)
    var defaultGovIndex by mutableStateOf<Int?>(null)
    var powersaveGovIndex by mutableStateOf<Int?>(null)
    var performanceGovIndex by mutableStateOf<Int?>(null)
    var freqOffsetIndex by mutableStateOf<Float?>(null)
    val offsetLabels = listOf("Disabled", "90%", "80%", "70%", "60%", "50%", "40%") // These are used as values for PropertyUtils, so we should keep them as is or map them


    var availableIOSchedulers by mutableStateOf<List<String>?>(null)
    var balancedIOIndex by mutableStateOf<Int?>(null)
    var performanceIOIndex by mutableStateOf<Int?>(null)
    var powersaveIOIndex by mutableStateOf<Int?>(null)
    

    var isMaliGpuAvailable by mutableStateOf<Boolean?>(null)
    var availableMaliGovernors by mutableStateOf<List<String>?>(null)

    /**
     * These three lists are interpolated into `Shell.cmd` argument strings for the
     * module's own CLI, so an entry that is not a bare token would be able to
     * break out of the argument. The kernel writes them, but they are still file
     * contents: filter at the point of parsing rather than trusting the source.
     * Only [A-Za-z0-9._+-] is allowed, which covers every governor, I/O
     * scheduler and Mali governor name in use.
     */
    private val SAFE_NODE_TOKEN = Regex("[A-Za-z0-9._+-]+")

    private fun parseNodeTokens(raw: String): List<String> =
        raw.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() && SAFE_NODE_TOKEN.matches(it) }
    var balancedMaliGovIndex by mutableStateOf<Int?>(null)
    var performanceMaliGovIndex by mutableStateOf<Int?>(null)
    var powersaveMaliGovIndex by mutableStateOf<Int?>(null)
    


    var preloadState by mutableStateOf<Boolean?>(null)
    var memKillerState by mutableStateOf<Boolean?>(null)
    var appPriorState by mutableStateOf<Boolean?>(null)
    var dndState by mutableStateOf<Boolean?>(null)
    var perfMaxState by mutableStateOf<Boolean?>(null)
    var fstrimState by mutableStateOf<Boolean?>(null)


    var currentRenderer by mutableStateOf<String?>(null)
    var currentRefreshRate by mutableStateOf<Int?>(null)
    var thermalState by mutableStateOf<Boolean?>(null)
    
    var isRendererLoading by mutableStateOf(false)
        private set
    
    var isRefreshRateLoading by mutableStateOf(false)
        private set
    
    private val configKeysToBackup = listOf(
        "persist.sys.nextcore.soctype",
        "persist.sys.nextcoreconf.cpulimit",
        "persist.sys.nextcoreconf.freqoffset",
        "persist.sys.nextcoreconf.APreload",
        "persist.sys.nextcoreconf.clearbg",
        "persist.sys.nextcoreconf.iosched",
        "persist.sys.nextcoreconf.dnd",
        "persist.sys.nextcoreconf.perfmax",
        "persist.sys.nextcoreconf.fstrim",
        "persist.sys.nextcoreconf.thermalcore",
        "persist.sys.nextcoreconf.schedtunes",
        "persist.sys.nextcoreconf.SFL",
        "persist.sys.nextcoreconf.justintime",
        "persist.sys.nextcoreconf.fpsged",
        "persist.sys.nextcoreconf.malisched",
        "persist.sys.nextcoreconf.walttunes",
        "persist.sys.nextcoreconf.disabletrace",
        "persist.sys.nextcoreconf.logd",
        "persist.sys.nextcoreconf.schemeconfig",
        "persist.sys.nextcoreconf.usefpsgo",
        "persist.sys.nextcoreconf.bypasschgthreshold",
        "persist.sys.nextcoreconf.preloadbudget",
        "persist.sys.nextcore.custom_default_cpu_gov",
        "persist.sys.nextcore.custom_powersave_cpu_gov",
        "persist.sys.nextcore.custom_performance_cpu_gov",
        "persist.sys.nextcore.custom_default_balanced_IO",
        "persist.sys.nextcore.custom_performance_IO",
        "persist.sys.nextcore.custom_powersave_IO",
        "persist.sys.nextcore.custom_default_maligpu_gov",
        "persist.sys.nextcore.custom_performance_maligpu_gov",
        "persist.sys.nextcore.custom_powersave_maligpu_gov" 
    )
    
    
    private val APPLIST_BACKUP_KEY = "__NEXTCORE_APPLIST_DATA__"
    private val APPLIST_PATH = "/data/adb/.config/NextCore/gamelist/nextcoreApplist.json"
    
    suspend fun createConfigFileBackup(
        context: Context, 
        uri: Uri, 
        backupTweaks: Boolean, 
        backupApplist: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val propsMap = mutableMapOf<String, String>()
            

            propsMap["persist.sys.nextcore.soctype"] = PropertyUtils.get("persist.sys.nextcore.soctype")

            if (backupTweaks) {
                configKeysToBackup.forEach { key ->
                    if (key != "persist.sys.nextcore.soctype") {
                        propsMap[key] = PropertyUtils.get(key)
                    }
                }
            }

            if (backupApplist) {

                val applistContent = RootUtils.readRootFile(APPLIST_PATH).orEmpty()
                if (applistContent.isNotBlank()) {
                    propsMap[APPLIST_BACKUP_KEY] = applistContent
                }
            }

            val isSuccess = BackupManager.createBackup(context, uri, propsMap)
            
            delay(1500) 
            
            isSuccess 
        }
    }
    
    suspend fun validateAndRestoreFile(context: Context, uri: Uri): ValidationResult {
        return withContext(Dispatchers.IO) {
            val backupData = BackupManager.readBackup(context, uri)
            
            if (backupData == null) {
                return@withContext ValidationResult(false, context.getString(R.string.err_invalid_backup), false, false, null, null)
            }
            val backupSocType = backupData["persist.sys.nextcore.soctype"] 
                ?: backupData["persist.sys.nextcoredebug.soctype"]
                
            val hasApplist = backupData.containsKey(APPLIST_BACKUP_KEY)
            
            val hasTweaks = backupData.keys.any { 
                it.startsWith("persist.sys.nextcore") && 
                it != "persist.sys.nextcore.soctype" &&
                it != "persist.sys.nextcoredebug.soctype" 
            }
    
            ValidationResult(true, "", hasTweaks, hasApplist, backupSocType, backupData)
        }
    }

    suspend fun applyRestoreData(
        context: Context, 
        backupData: Map<String, String>, 
        restoreTweaks: Boolean, 
        restoreApplist: Boolean
    ) {
        withContext(Dispatchers.IO) {
            if (restoreTweaks) {
                backupData.forEach { (key, value) ->
                    if (key != "persist.sys.nextcore.soctype" && 
                        key != "persist.sys.nextcoredebug.soctype" && 
                        key != APPLIST_BACKUP_KEY && 
                        value.isNotEmpty()) {
                        
                        PropertyUtils.set(key, value)
                        
                        if (key == "persist.sys.nextcoreconf.freqoffset") {
                            RootUtils.writeRootFile("/data/adb/.config/NextCore/freqoffset", "$value\n")
                        }
                    }
                }
            }

            if (restoreApplist && backupData.containsKey(APPLIST_BACKUP_KEY)) {
                val applistContent = backupData[APPLIST_BACKUP_KEY]!!
                val file = SuFile(APPLIST_PATH)
                val parent = file.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                SuFileOutputStream.open(file).use { outputStream ->
                    outputStream.write(applistContent.toByteArray())
                }
            }
            
            RootUtils.touchRootFile("/data/adb/modules/nextcore/reboot")
            if (restoreTweaks) {
                loadAllConfiguration(context)
            }
            delay(1200) 
        }
    }
    
    fun loadAllConfiguration(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            
            launch {
                liteState = PropertyUtils.get("persist.sys.nextcoreconf.cpulimit") == "1"
                
                val savedOffset = PropertyUtils.get("persist.sys.nextcoreconf.freqoffset", "Disabled")
                freqOffsetIndex = when (savedOffset) {
                    "90" -> 1f; "80" -> 2f; "70" -> 3f; "60" -> 4f; "50" -> 5f; "40" -> 6f; else -> 0f
                }            
                
                preloadState = PropertyUtils.get("persist.sys.nextcoreconf.APreload") == "1"
                memKillerState = PropertyUtils.get("persist.sys.nextcoreconf.clearbg") == "1"
                appPriorState = PropertyUtils.get("persist.sys.nextcoreconf.iosched") == "1"
                dndState = PropertyUtils.get("persist.sys.nextcoreconf.dnd") == "1"
                perfMaxState = PropertyUtils.get("persist.sys.nextcoreconf.perfmax") == "1"
                fstrimState = PropertyUtils.get("persist.sys.nextcoreconf.fstrim") == "1"
                thermalState = PropertyUtils.get("persist.sys.nextcoreconf.thermalcore") == "1"

                val rawRenderer = PropertyUtils.get("debug.hwui.renderer")
                val nextcoreRenderer = PropertyUtils.get("persist.sys.nextcoreconf.renderer")
                
                currentRenderer = when {
                    rawRenderer.isEmpty() && (nextcoreRenderer.isEmpty() || nextcoreRenderer.equals("default", ignoreCase = true)) -> "Default"
                    
                    nextcoreRenderer.isEmpty() -> if (rawRenderer.equals("default", ignoreCase = true)) "Default" else rawRenderer
                    
                    rawRenderer.isEmpty() -> if (nextcoreRenderer.equals("default", ignoreCase = true)) "Default" else nextcoreRenderer
                    
                    else -> {
                        val isSameValue = rawRenderer.equals(nextcoreRenderer, ignoreCase = true)
                        val isNextcoreDefault = nextcoreRenderer.equals("default", ignoreCase = true)
                        
                        when {
                            isSameValue -> if (rawRenderer.equals("default", ignoreCase = true)) "Default" else rawRenderer
                            isNextcoreDefault -> "Default ($rawRenderer)"
                            else -> "$nextcoreRenderer ($rawRenderer)"
                        }
                    }
                }

                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                currentRefreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display.refreshRate.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.refreshRate.toInt()
                }
            }

            val govJob = async { loadGovernorsInternal() }
            val ioJob = async { loadIOSchedulersInternal() }
            val maliJob = async { loadMaliGovernorsInternal() }

            govJob.await()
            ioJob.await()
            maliJob.await()

            withContext(Dispatchers.Main) {
                isUiLoaded = true
            }
        }
    }

    private fun loadGovernorsInternal() {
        val govs = RootUtils.readRootFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
            ?.let { parseNodeTokens(it) } ?: emptyList()
        if (govs.isNotEmpty()) {
            val currentDefault = PropertyUtils.get("persist.sys.nextcore.custom_default_cpu_gov").ifEmpty {
                PropertyUtils.get("persist.sys.nextcore.default_cpu_gov")
            }
            val currentPowersave = PropertyUtils.get("persist.sys.nextcore.custom_powersave_cpu_gov")
            val currentPerformance = PropertyUtils.get("persist.sys.nextcore.custom_performance_cpu_gov")

            availableGovernors = govs
            defaultGovIndex = govs.indexOf(currentDefault).coerceAtLeast(0)
            powersaveGovIndex = govs.indexOf(currentPowersave).coerceAtLeast(0)
            performanceGovIndex = govs.indexOf(currentPerformance).coerceAtLeast(0)
        } else {
            availableGovernors = emptyList()
        }
    }

    private fun loadIOSchedulersInternal() {
        val candidates = listOf("mmcblk0", "mmcblk1", "sda", "sdb", "sdc")
        var validBlock = ""
        for (block in candidates) {
            if (RootUtils.rootFileExists("/sys/block/$block/queue/scheduler")) {
                validBlock = block
                break
            }
        }
        if (validBlock.isNotEmpty()) {
            val rawOut = RootUtils.readRootFile("/sys/block/$validBlock/queue/scheduler").orEmpty()
            if (rawOut.isNotEmpty()) {
                val schedulers = parseNodeTokens(rawOut.replace("[", "").replace("]", ""))

                val currentBal = PropertyUtils.get("persist.sys.nextcore.custom_default_balanced_IO").ifEmpty {
                    PropertyUtils.get("persist.sys.nextcore.default_balanced_IO")
                }
                val currentPerf = PropertyUtils.get("persist.sys.nextcore.custom_performance_IO")
                val currentEco = PropertyUtils.get("persist.sys.nextcore.custom_powersave_IO")

                availableIOSchedulers = schedulers
                balancedIOIndex = schedulers.indexOf(currentBal).coerceAtLeast(0)
                performanceIOIndex = schedulers.indexOf(currentPerf).coerceAtLeast(0)
                powersaveIOIndex = schedulers.indexOf(currentEco).coerceAtLeast(0)
            } else {
                availableIOSchedulers = emptyList()
            }
        } else {
            availableIOSchedulers = emptyList()
        }
    }

    private fun loadMaliGovernorsInternal() {

        val checkResult = Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf checkmalipath").exec()
        val hasMali = checkResult.out.joinToString("").trim() == "true"

        if (hasMali) {
            isMaliGpuAvailable = true

            // The node is behind a glob (`*.mali`). Was `cat /sys/class/devfreq/
            // *.mali/available_governors` through a shell for the expansion.
            // `SuFile.listFiles()` goes through the root shell, so the matching
            // no longer needs a process; the read goes through readRootFile
            // rather than File.readText, which is inherited from java.io.File
            // and would open an unprivileged FileInputStream.
            val maliGovs = try {
                SuFile("/sys/class/devfreq").listFiles()
                    ?.firstOrNull { it.name.endsWith(".mali") }
                    ?.let { RootUtils.readRootFile("${it.absolutePath}/available_governors") }
            } catch (e: Exception) {
                null
            }

            if (!maliGovs.isNullOrEmpty()) {
                val govs = parseNodeTokens(maliGovs)
                    .filterNot { it.startsWith("apu", ignoreCase = true) }
                
                val currentBal = PropertyUtils.get("persist.sys.nextcore.custom_default_maligpu_gov").ifEmpty {
                    PropertyUtils.get("persist.sys.nextcore.default_maligpu_gov")
                }
                val currentPerf = PropertyUtils.get("persist.sys.nextcore.custom_performance_maligpu_gov")
                val currentEco = PropertyUtils.get("persist.sys.nextcore.custom_powersave_maligpu_gov")

                availableMaliGovernors = govs
                balancedMaliGovIndex = govs.indexOf(currentBal).coerceAtLeast(0)
                performanceMaliGovIndex = govs.indexOf(currentPerf).coerceAtLeast(0)
                powersaveMaliGovIndex = govs.indexOf(currentEco).coerceAtLeast(0)
            } else {
                availableMaliGovernors = emptyList()
            }
        } else {
            isMaliGpuAvailable = false
        }
    }

    

    fun updateLiteMode(checked: Boolean) {
        liteState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.cpulimit", if (checked) "1" else "0")
        }
    }

    fun updateDefaultGovernor(index: Int) {
        defaultGovIndex = index
        val selectedGov = availableGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_default_cpu_gov", selectedGov)
            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "2") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsgov $selectedGov").exec()
            }
        }
    }

    fun updatePowersaveGovernor(index: Int) {
        powersaveGovIndex = index
        val selectedGov = availableGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_powersave_cpu_gov", selectedGov)
            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "3") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsgov $selectedGov").exec()
            }
        }
    }
    
    fun updatePerformanceGovernor(index: Int) {
        performanceGovIndex = index
        val selectedGov = availableGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_performance_cpu_gov", selectedGov)
            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "3") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsgov $selectedGov").exec()
            }
        }
    }

    fun saveFreqOffset(value: Float) {
        freqOffsetIndex = value
        val index = value.roundToInt()
        val propValue = if (index == 0) "Disabled" else offsetLabels[index].replace("%", "")
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.freqoffset", propValue)
            RootUtils.writeRootFile("/data/adb/.config/NextCore/freqoffset", "$propValue\n")
        }
    }

    fun updateBalancedIO(index: Int) {
        balancedIOIndex = index
        val selectedIO = availableIOSchedulers?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_default_balanced_IO", selectedIO)
            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "2") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsIO $selectedIO").exec()
            }
        }
    }

    fun updatePerformanceIO(index: Int) {
        performanceIOIndex = index
        val selectedIO = availableIOSchedulers?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_performance_IO", selectedIO)
            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "1") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsIO $selectedIO").exec()
            }
        }
    }

    fun updatePowersaveIO(index: Int) {
        powersaveIOIndex = index
        val selectedIO = availableIOSchedulers?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_powersave_IO", selectedIO)
            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "3") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsIO $selectedIO").exec()
            }
        }
    }
    
    fun updateBalancedMaliGov(index: Int) {
        balancedMaliGovIndex = index
        val selectedGov = availableMaliGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_default_maligpu_gov", selectedGov)

            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "2") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsMaliGov $selectedGov").exec()
            }
        }
    }

    fun updatePerformanceMaliGov(index: Int) {
        performanceMaliGovIndex = index
        val selectedGov = availableMaliGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_performance_maligpu_gov", selectedGov)
            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "1") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsMaliGov $selectedGov").exec()
            }
        }
    }

    fun updatePowersaveMaliGov(index: Int) {
        powersaveMaliGovIndex = index
        val selectedGov = availableMaliGovernors?.getOrNull(index) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcore.custom_powersave_maligpu_gov", selectedGov)
            val currentProfile = RootUtils.readRootFile("/data/adb/.config/NextCore/API/current_profile")
            if (currentProfile == "3") {
                Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setsMaliGov $selectedGov").exec()
            }
        }
    }

    fun updatePreloadMode(checked: Boolean) {
        preloadState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.APreload", if (checked) "1" else "0")
        }
    }

    fun updateMemoryKiller(checked: Boolean) {
        memKillerState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.clearbg", if (checked) "1" else "0")
        }
    }

    fun updateAppPriority(checked: Boolean) {
        appPriorState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.iosched", if (checked) "1" else "0")
        }
    }

    /** Max performance mode; takes effect the next time the performance profile is applied. */
    fun updatePerfMax(checked: Boolean) {
        perfMaxState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.perfmax", if (checked) "1" else "0")
        }
    }

    fun updateDndMode(checked: Boolean) {
        dndState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.dnd", if (checked) "1" else "0")
        }
    }

    fun updateFstrim(checked: Boolean) {
        fstrimState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.fstrim", if (checked) "1" else "0")
        }
    }

    fun updateThermalCore(checked: Boolean) {
        thermalState = checked
        viewModelScope.launch(Dispatchers.IO) {
            PropertyUtils.set("persist.sys.nextcoreconf.thermalcore", if (checked) "1" else "0")
            Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setthermalcore ${if (checked) "1" else "0"}").exec()
        }
    }

    fun executeSetRenderer(reason: String, context: Context) {
        // The value ends up in a root shell command; only allow plain renderer names.
        if (!reason.matches(Regex("^[A-Za-z0-9_.-]+$"))) return
        isRendererLoading = true
        Shell.cmd("/data/adb/modules/nextcore/system/bin/sys.nextcore-utilityconf setrender $reason && setprop persist.sys.nextcoreconf.renderer $reason").submit {
            viewModelScope.launch {
                delay(1000)
                loadAllConfiguration(context)
                isRendererLoading = false
            }
        }
    }
    
    fun executeSetRefreshRates(reason: String, context: Context) {
        isRefreshRateLoading = true
    
        val fps = reason.toIntOrNull() ?: 60
        val intent = Intent(context, RefreshRateReceiver::class.java).apply {
            action = "zx.nextcore.SET_FPS"
            putExtra("fps", fps)
        }
        context.sendBroadcast(intent)
    
        viewModelScope.launch {
            delay(1000)
            loadAllConfiguration(context)
            isRefreshRateLoading = false
        }
    }

}
