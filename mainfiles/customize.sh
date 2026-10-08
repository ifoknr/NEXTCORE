#
# Copyright (C) 2026-2027 Zexshia
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

SKIPUNZIP=1

# Paths
MODULE_CONFIG="/data/adb/.config/NextCore"
device_codename=$(getprop ro.product.board)
chip=$(getprop ro.hardware)
HM_DIR="/data/adb/hybrid-mount"
HM_CONFIG="$HM_DIR/config.toml"
API_LEVEL=$(getprop ro.build.version.sdk)
readonly APK_COMP="$MODPATH/NextCore.apk"
readonly TMP_DIR="/data/local/tmp"
readonly TMP_APK="$TMP_DIR/NextCore_install.apk"

# Create File
make_node() {
	[ ! -f "$2" ] && echo "$1" >"$2"
}

# Prepare app files
_prepare_apk() {
    if [ ! -f "$APK_COMP" ]; then
        echo "[!] APK not found at $APK_COMP" >&2
        return 1
    fi    
    echo "- Preparing APK for installation..."
    cp "$APK_COMP" "$TMP_APK" || return 1
    chmod 644 "$TMP_APK" || return 1  
    return 0
}

# Cleanup temporary APK
_cleanup_apk() {
    [ -f "$TMP_APK" ] && rm -f "$TMP_APK"
}

# Install APK
install_manager() {
    local apk_path="$1"
    local app_name="$2"
    local tmp_log="$TMP_DIR/install_log.txt"

    local MAX_TIMEOUT=100

    (
        local install_output
        install_output=$(pm install -r -d --user 0 "$apk_path" 2>&1)    
        if ! echo "$install_output" | grep -iq "Success"; then
            install_output=$(cmd package install -r -d --user 0 "$apk_path" 2>&1)
        fi
        echo "$install_output" > "$tmp_log"
    ) &
    local pid=$!

    local i=0
    while kill -0 $pid 2>/dev/null; do
        clear
        case $((i % 4)) in
            0) echo "[-] Installing $app_name..." ;;
            1) echo "[/] Installing $app_name..." ;;
            2) echo "[|] Installing $app_name..." ;;
            3) echo "[\] Installing $app_name..." ;;
        esac
        sleep 0.1
        i=$((i + 1))

        if [ $i -ge $MAX_TIMEOUT ]; then
            echo "[!] Installation took too long. Stopping..."
            kill -9 $pid 2>/dev/null
            echo "Timeout: Installation exceeded 10 seconds" > "$tmp_log"
            break
        fi
    done

    wait $pid 2>/dev/null
    clear

    local result
    [ -f "$tmp_log" ] && result=$(cat "$tmp_log")
    rm -f "$tmp_log"

    if echo "$result" | grep -iq "Success"; then
        echo "[✓] $app_name installed successfully"
		echo ""
        return 0
    else
        echo "[!] Failed to install $app_name"
        if echo "$result" | grep -iq "Timeout"; then
            echo "  Error: Installation failed due to Timeout"
            echo "  Possible reason: Your Rom blocking background install."
        else
            echo "  Error log: $(echo "$result" | head -n 2)"
        fi
        echo "! Run module Action"
        echo "! Or unzip the module file, and"
        echo "! Install it manually"
        echo "- Continuing module installation..."
        sleep 3
        return 1
    fi
}


abort_api() {
  echo ""
  echo "! Installation Aborted"
  echo "! Unsupported Android Version Detected"
  echo "! NextCore requires Android 10 (API 29) or newer."
  abort "! Your device is currently running API $API_LEVEL."
}

abort_corrupted() {
  clear
  echo ""
  echo "! Installation Aborted"
  echo "! The NextCore package appears to be corrupted or incomplete."
  echo "! Required installation files were not found."
  echo ""
  abort "! Please re-download the module and try again."
}

abort_arch() {
  clear
  echo "! Installation Aborted"
  echo "! Unsupported CPU Architecture Detected"
  echo "! Your device architecture is not compatible with this build of NextCore."
  echo "! Supported architectures:"
  echo "  • arm64-v8a"
  abort "  • armeabi-v7a"
}

installation_complete() {
  echo "- NextCore has been successfully installed"
  echo "- Support level: $DP_SUPPORT ($soc)"
  echo "- Please reboot your device."
  echo "- Open Manager from Action"
  echo "- Don't forget to grant root access."
}

# Displaybanner
echo ""
echo "              NextCore              "
echo ""
echo "- Installing NextCore..."

# API Level Check (Require API 29+)
[ "$API_LEVEL" -lt 29 ] && abort_api

# Extract Module Directiories
mkdir -p "$MODULE_CONFIG"
mkdir -p "$MODULE_CONFIG/debug"
mkdir -p "$MODULE_CONFIG/API"
mkdir -p "$MODULE_CONFIG/preload"
mkdir -p "$MODULE_CONFIG/bypasschgconfig"
mkdir -p "$MODULE_CONFIG/gamelist"
mkdir -p "$MODPATH/system/bin"
echo "- Create module config"

# Flashable integrity checkup
echo "- Extracting verify.sh"
unzip -o "$ZIPFILE" 'verify.sh' -d "$TMPDIR" >&2
[ ! -f "$TMPDIR/verify.sh" ] && abort_corrupted
source "$TMPDIR/verify.sh"

# Target architecture detection
case $ARCH in
"arm64") ARCH_TMP="arm64-v8a" ;;
"arm") ARCH_TMP="armeabi-v7a" ;;
*) abort_arch ;;
esac

echo "- Extracting binaries for $ARCH_TMP..."
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.nextcore-service" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.nextcore-profilesettings" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.nextcore-thermalcore" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.nextcore-utilityconf" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.nextcore-preferencedtweaks" "$TMPDIR"
extract "$ZIPFILE" "libs/$ARCH_TMP/sys.nextcore-preloadbin" "$TMPDIR"
cp "$TMPDIR/libs/$ARCH_TMP/"* "$MODPATH/system/bin/"
rm -rf "$TMPDIR/libs"
echo "- All binaries installed successfully"

# Extract Module standard files
echo "- Extracting service.sh..."
extract "$ZIPFILE" service.sh "$MODPATH"
echo "- Extracting post-fs-data.sh..."
extract "$ZIPFILE" post-fs-data.sh "$MODPATH"
echo "- Extracting action.sh..."
extract "$ZIPFILE" action.sh "$MODPATH"
echo "- Extracting cleanup.sh..."
extract "$ZIPFILE" cleanup.sh "$MODPATH"
echo "- Extracting module.prop..."
extract "$ZIPFILE" module.prop "$MODPATH"
cp "$MODPATH/module.prop" "$MODPATH/module.prop.orig"
echo "- Extracting uninstall.sh..."
extract "$ZIPFILE" uninstall.sh "$MODPATH"
# Builds before the rename kept their config in .config/AZenith: carry the game list over
OLD_CONFIG="/data/adb/.config/AZenith"
if [ ! -f "$MODULE_CONFIG/gamelist/nextcoreApplist.json" ] && [ -f "$OLD_CONFIG/gamelist/azenithApplist.json" ]; then
    echo "- Migrating game list from the previous version"
    mkdir -p "$MODULE_CONFIG/gamelist"
    cp "$OLD_CONFIG/gamelist/azenithApplist.json" "$MODULE_CONFIG/gamelist/nextcoreApplist.json"
fi
# Keep a pristine copy in the module so service.sh can restore a missing gamelist
extract "$ZIPFILE" nextcoreApplist.json "$MODPATH"
if [ ! -f "$MODULE_CONFIG/gamelist/nextcoreApplist.json" ]; then
    echo "- Extracting Applist.json..."
    extract "$ZIPFILE" nextcoreApplist.json "$MODULE_CONFIG/gamelist"
fi
echo "- Extracting module banner..."
extract "$ZIPFILE" module.banner.jpg "$MODPATH"
echo "- Extracting WebUI..."
extract "$ZIPFILE" webui/index.html "$MODPATH"

# Skip mountify
touch "$MODPATH/skip_mountify"

# Skip hybrid mount
if [ -f "$HM_CONFIG" ]; then
    echo "- Hybrid Mount detected, configuring rules..."

    HM_BIN=""
    if [ -x "/data/adb/modules/hybrid_mount/hybrid-mount" ]; then
        HM_BIN="/data/adb/modules/hybrid_mount/hybrid-mount"
    elif command -v hybrid-mount >/dev/null 2>&1; then
        HM_BIN="hybrid-mount"
    fi

    if [ -n "$HM_BIN" ]; then
        echo "- Found Hybrid Mount CLI at: $HM_BIN"
        $HM_BIN api config-patch --patch '{"rules":{"nextcore":{"default_mode":"ignore"}}}' --apply-runtime >/dev/null 2>&1
        echo "- Runtime policy for NextCore updated to 'ignore'."
    else
        echo "- Warning: CLI binary not found in standard paths. Skipping live patch."
    fi

    if ! grep -q "\[rules\.nextcore\]" "$HM_CONFIG"; then
        echo "" >> "$HM_CONFIG"
        echo "[rules.nextcore]" >> "$HM_CONFIG"
        echo 'default_mode = "ignore"' >> "$HM_CONFIG"
        echo "- Permanent rule added to config.toml."
    fi
else
    echo "- Hybrid Mount is not installed. Skipping configuration."
fi

# Use Symlink for APatch / KernelSU
if [ "$KSU" = "true" ] || [ "$APATCH" = "true" ]; then
	# skip mount on APatch / KernelSU
	touch "$MODPATH/skip_mount"
	echo "- KSU/AP Detected, skipping module mount (skip_mount)"
	# symlink ourselves on $PATH
	manager_paths="/data/adb/ap/bin /data/adb/ksu/bin"
	BIN_PATH="/data/adb/modules/nextcore/system/bin"
	for dir in $manager_paths; do
		[ -d "$dir" ] && {
			echo "- Creating symlink in $dir"
			ln -sf "$BIN_PATH/sys.nextcore-service" "$dir/sys.nextcore-service"
			ln -sf "$BIN_PATH/sys.nextcore-service" "$dir/zx" # Binary calls for CLI
			ln -sf "$BIN_PATH/sys.nextcore-profilesettings" "$dir/sys.nextcore-profilesettings"
			ln -sf "$BIN_PATH/sys.nextcore-utilityconf" "$dir/sys.nextcore-utilityconf"
			ln -sf "$BIN_PATH/sys.nextcore-preferencedtweaks" "$dir/sys.nextcore-preferencedtweaks"
			ln -sf "$BIN_PATH/sys.nextcore-preloadbin" "$dir/sys.nextcore-preloadbin"
            ln -sf "$BIN_PATH/sys.nextcore-thermalcore" "$dir/sys.nextcore-thermalcore"
		}
	done
fi

# Detect the device, pick monitoring sensors and the support level
ui_print ""
ui_print "- Scanning device..."
extract "$ZIPFILE" devprobe.sh "$MODPATH"
. "$MODPATH/devprobe.sh"
devprobe_run
soc="$DP_SOC_VENDOR"
setprop persist.sys.nextcore.soctype "$DP_SOC_TYPE"
ui_print ""

# Soc Type
# 1) MediaTek
# 2) Snapdragon
# 3) Exynos
# 4) Unisoc
# 5) Tensor
# 0) Unknown

# Set default freqoffset
if [ -z "$(getprop persist.sys.nextcoreconf.freqoffset)" ]; then
	setprop persist.sys.nextcoreconf.freqoffset "Disabled"
	touch "$MODULE_CONFIG/freqoffset"
	echo "Disabled" > "$MODULE_CONFIG/freqoffset"
fi

# Set default color scheme if not set
if [ -z "$(getprop persist.sys.nextcoreconf.schemeconfig)" ]; then
	setprop persist.sys.nextcoreconf.schemeconfig "1000 1000 1000 1000"
fi

# Initiate bypasspath default value
if [ -z "$(getprop persist.sys.nextcoreconf.bypasspath)" ]; then
	setprop persist.sys.nextcoreconf.bypasspath "NEED_SETUP"
	touch "$MODULE_CONFIG/bypasschgconfig/bypasspath"
	echo "NEED_SETUP" > "$MODULE_CONFIG/bypasschgconfig/bypasspath"
fi

# Initiate bypasspath default value
if [ -z "$(getprop persist.sys.nextcoreconf.bypasschgthreshold)" ]; then
	setprop persist.sys.nextcoreconf.bypasschgthreshold "20"
	touch "$MODULE_CONFIG/bypasschgconfig/bypasschgthreshold"
	echo "20" > "$MODULE_CONFIG/bypasschgconfig/bypasschgthreshold"
fi

# Initiate bypasscharging state
if [ -z "$(getprop persist.sys.nextcoreconf.bypasschg)" ]; then
	setprop persist.sys.nextcoreconf.bypasschg "0"
	touch "$MODULE_CONFIG/bypasschgconfig/bypasschg"
	echo "0" > "$MODULE_CONFIG/bypasschgconfig/bypasschg"
fi

# Daemon Configurations
if [ -z "$(getprop persist.sys.nextcoreconf.showtoast)" ]; then
	setprop persist.sys.nextcoreconf.showtoast 0
fi

if [ -z "$(getprop persist.sys.nextcore.profilenotifications)" ]; then
	setprop persist.sys.nextcore.profilenotifications 1
fi

if [ -z "$(getprop persist.sys.nextcore.dropforeground)" ]; then
	setprop persist.sys.nextcore.dropforeground 0
fi

if [ -z "$(getprop persist.sys.nextcore.disabletweak)" ]; then
	setprop persist.sys.nextcore.disabletweak 0
fi

if [ -z "$(getprop persist.sys.nextcoreconf.iosched)" ]; then
	setprop persist.sys.nextcoreconf.iosched 1
fi

if [ -z "$(getprop persist.sys.nextcoreconf.renderer)" ]; then
	setprop persist.sys.nextcoreconf.renderer default
fi

if [ -z "$(getprop persist.sys.nextcoreconf.preloadbudget)" ]; then
    setprop persist.sys.nextcoreconf.preloadbudget 500M
fi

if [ -z "$(getprop persist.sys.nextcoreconf.AIenabled)" ]; then
    echo "- Enabling Auto Mode"
    setprop persist.sys.nextcoreconf.AIenabled 1
    echo 1 > "$MODULE_CONFIG/API/current_modes"
fi

echo "- Disable Debugmode"
setprop persist.sys.nextcore.debugmode "false"

# Set config properties to use
echo "- Setting config properties..."
props="
persist.sys.nextcoreconf.logd
persist.sys.nextcoreconf.SFL
persist.sys.nextcoreconf.malisched
persist.sys.nextcoreconf.fpsged
persist.sys.nextcoreconf.schedtunes
persist.sys.nextcoreconf.clearbg
persist.sys.nextcoreconf.APreload
persist.sys.nextcoreconf.cpulimit
persist.sys.nextcoreconf.dnd
persist.sys.nextcoreconf.justintime
persist.sys.nextcoreconf.disabletrace
persist.sys.nextcoreconf.thermalcore
persist.sys.nextcoreconf.walttunes
persist.sys.nextcoreconf.fstrim
persist.sys.nextcoreconf.usefpsgo
"
for prop in $props; do
	curval=$(getprop "$prop")
	if [ -z "$curval" ]; then
		setprop "$prop" 0
	fi
done

extract "$ZIPFILE" NextCore.apk "$MODPATH"

# Install Apps
APP_INSTALLED=false

if ! _prepare_apk; then
    echo "[!] Failed to prepare APK. Continuing without installing manager..." >&2
else
    if install_manager "$TMP_APK" "NextCore Manager"; then
        APP_INSTALLED=true
    fi
    _cleanup_apk
fi

# Enable Launcher and grant permissions ONLY if app installed successfully
if [ "$APP_INSTALLED" = "true" ]; then
    echo "- Enabling Launcher..."
    pm enable --user 0 zx.nextcore/.Launcher > /dev/null 2>&1
    
    echo "- Setting Permissions..."
    pm grant zx.nextcore android.permission.READ_EXTERNAL_STORAGE
    pm grant zx.nextcore android.permission.POST_NOTIFICATIONS
    pm grant zx.nextcore android.permission.READ_MEDIA_IMAGES
fi

# Remove old module files if available
echo "- Cleaning old files..."
[ -f "/data/local/tmp/module.avatar.webp" ] && rm -f "/data/local/tmp/module.avatar.webp"
if pm list packages | grep -q "azenith.toast"; then
    echo "- Uninstalling old components"
    pm uninstall --user 0 azenith.toast > /dev/null 2>&1
fi
# NextCore 2.0 builds before the rename shipped the app as zx.azenith
if pm list packages | grep -q "^package:zx.azenith$"; then
    echo "- Removing the old NextCore app (zx.azenith)"
    pm uninstall --user 0 zx.azenith > /dev/null 2>&1
fi

set_perm_recursive "$MODPATH/system/bin" 0 0 0755 0755

installation_complete
