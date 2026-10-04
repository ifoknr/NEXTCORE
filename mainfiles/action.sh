#!/system/bin/sh

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

MODDIR="${0%/*}"
{ [ -z "$MODDIR" ] || [ "$MODDIR" = "." ] || [ ! -f "$MODDIR/module.prop" ]; } && MODDIR="/data/adb/modules/nextcore"
readonly MODDIR
readonly BIN_SVC="$MODDIR/system/bin/sys.azenith-service"
readonly APK_COMP="$MODDIR/AZenith.apk"
readonly TMP_DIR="/data/local/tmp"
readonly TMP_APK="$TMP_DIR/AZenith_install.apk"

readonly BIN_UTIL="$MODDIR/system/bin/sys.azenith-utilityconf"
readonly MODULE_CONFIG="/data/adb/.config/AZenith"
readonly CPUFREQ="/sys/devices/system/cpu/cpu0/cpufreq"

# ---------------------------------------------------------------------------
# WebUI / CLI sub-commands
#   action.sh get_status
#   action.sh set_global <governor|auto|keep_gov> <bypass 0|1|keep>
#   action.sh set_profile <1|2|3>
#   action.sh set_auto <0|1>
#   action.sh doctor          (diagnostics: boot.log, daemon log, state)
#   action.sh restart         (restart the NextCore service)
# Without arguments (module "Action" button) the manager app is installed/opened.
# ---------------------------------------------------------------------------

_json_escape() {
	printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

_is_valid_gov() {
	[ -n "$1" ] || return 1
	case "$1" in *[!a-zA-Z0-9_-]*) return 1 ;; esac
	for g in $(cat "$CPUFREQ/scaling_available_governors" 2>/dev/null); do
		[ "$g" = "$1" ] && return 0
	done
	return 1
}

_get_status() {
	soc_model="$(getprop ro.soc.model)"
	[ -z "$soc_model" ] && soc_model="$(getprop ro.board.platform)"
	device="$(getprop ro.product.model)"
	temp_raw="$(cat /sys/class/power_supply/battery/temp 2>/dev/null)"
	temp="--"
	case "$temp_raw" in "" | *[!0-9-]*) ;; *) temp=$((temp_raw / 10)) ;; esac
	profile="$(cat "$MODULE_CONFIG/API/current_profile" 2>/dev/null)"
	gov="$(cat "$CPUFREQ/scaling_governor" 2>/dev/null)"
	govs="$(cat "$CPUFREQ/scaling_available_governors" 2>/dev/null)"
	custom_gov="$(getprop persist.sys.azenith.custom_default_cpu_gov)"
	bypass="$(getprop persist.sys.azenithconf.bypasschg)"
	bypass_path="$(getprop persist.sys.azenithconf.bypasspath)"
	auto="$(getprop persist.sys.azenithconf.AIenabled)"
	running=0
	[ -n "$(/system/bin/toybox pidof sys.azenith-service)" ] && running=1
	support="$(grep '^support=' "$MODULE_CONFIG/device_profile" 2>/dev/null | cut -d= -f2-)"
	soc_vendor="$(grep '^soc_vendor=' "$MODULE_CONFIG/device_profile" 2>/dev/null | cut -d= -f2-)"
	printf '{"support":"%s","soc_vendor":"%s","device":"%s","soc":"%s","temp":"%s","profile":"%s","governor":"%s","governors":"%s","custom_gov":"%s","bypass":"%s","bypass_path":"%s","auto":"%s","daemon":%s,"version":"%s"}\n' \
		"$(_json_escape "$support")" "$(_json_escape "$soc_vendor")" \
		"$(_json_escape "$device")" "$(_json_escape "$soc_model")" "$temp" "$(_json_escape "$profile")" \
		"$(_json_escape "$gov")" "$(_json_escape "$govs")" "$(_json_escape "$custom_gov")" \
		"$(_json_escape "$bypass")" "$(_json_escape "$bypass_path")" "$(_json_escape "$auto")" "$running" \
		"$(_json_escape "$(grep '^version=' "$MODDIR/module.prop" | cut -d= -f2-)")"
}

_set_bypass() {
	case "$1" in
	0 | 1)
		setprop persist.sys.azenithconf.bypasschg "$1"
		mkdir -p "$MODULE_CONFIG/bypasschgconfig"
		echo "$1" >"$MODULE_CONFIG/bypasschgconfig/bypasschg"
		;;
	esac
}

_set_global() {
	gov="$1"
	bypass="$2"
	if [ "$gov" = "keep_gov" ]; then
		_set_bypass "$bypass"
		echo "OK"
		return 0
	elif [ "$gov" = "auto" ]; then
		setprop persist.sys.azenith.custom_default_cpu_gov ""
		gov="$(getprop persist.sys.azenith.default_cpu_gov)"
	elif _is_valid_gov "$gov"; then
		setprop persist.sys.azenith.custom_default_cpu_gov "$gov"
	else
		echo "ERROR: unsupported governor: $gov" >&2
		return 1
	fi
	# Apply immediately only while the balanced profile is active; other
	# profiles pick their own governor and will use this one when they return.
	if [ "$(cat "$MODULE_CONFIG/API/current_profile" 2>/dev/null)" = "2" ] && _is_valid_gov "$gov"; then
		"$BIN_UTIL" setsgov "$gov" >/dev/null 2>&1
	fi
	_set_bypass "$bypass"
	echo "OK"
}

_doctor() {
	echo "== state: $(getprop persist.sys.azenith.state)"
	echo "== service pid: $(/system/bin/toybox pidof sys.azenith-service)"
	echo "== appmonitor pid: $(/system/bin/toybox pidof sys.azenith-appmonitoring)"
	echo "== soc: $(getprop ro.soc.manufacturer) $(getprop ro.soc.model) (soctype=$(getprop persist.sys.azenith.soctype))"
	echo "== profile: $(cat "$MODULE_CONFIG/API/current_profile" 2>/dev/null)"
	[ -d /data/adb/modules/AZenith ] && echo "== WARNING: AZenith module folder still exists"
	echo "== device_profile"
	cat "$MODULE_CONFIG/device_profile" 2>/dev/null || echo "(missing)"
	echo "== boot.log"
	cat "$MODULE_CONFIG/debug/boot.log" 2>/dev/null
	echo "== AZenith.log (last 30)"
	tail -n 30 "$MODULE_CONFIG/debug/AZenith.log" 2>/dev/null
}

case "$1" in
doctor)
	_doctor
	exit 0
	;;
restart)
	"$BIN_SVC" --rerun >/dev/null 2>&1
	echo "OK"
	exit 0
	;;
get_status)
	_get_status
	exit 0
	;;
set_global)
	_set_global "$2" "$3"
	exit $?
	;;
set_profile)
	case "$2" in
	1 | 2 | 3) exec "$BIN_SVC" -p "$2" ;;
	*) echo "ERROR: profile must be 1, 2 or 3" >&2; exit 1 ;;
	esac
	;;
set_auto)
	case "$2" in
	0 | 1)
		setprop persist.sys.azenithconf.AIenabled "$2"
		echo "$2" >"$MODULE_CONFIG/API/current_modes"
		echo "OK"
		exit 0
		;;
	*) echo "ERROR: value must be 0 or 1" >&2; exit 1 ;;
	esac
	;;
esac

# Check if app is installed
_app_installed() {
	pm path zx.azenith >/dev/null 2>&1
}

# Check if service binary exists
_service_exists() {
	[ -f "$BIN_SVC" ]
}

# Prepare app files safely
_prepare_apk() {
	if [ ! -f "$APK_COMP" ]; then
		echo "[!] APK not found at $APK_COMP" >&2
		return 1
	fi
	cp "$APK_COMP" "$TMP_APK" || return 1
	chmod 644 "$TMP_APK" || return 1
	return 0
}

# Cleanup temporary APK
_cleanup_apk() {
	[ -f "$TMP_APK" ] && rm -f "$TMP_APK"
}

# Install APK with timeout protection
install_manager() {
	local tmp_log="$TMP_DIR/action_install_log.txt"
	local MAX_TIMEOUT=100

	(
		local install_output
		install_output=$(pm install -r -d --user 0 "$TMP_APK" 2>&1)
		if ! echo "$install_output" | grep -iq "Success"; then
			install_output=$(cmd package install -r -d --user 0 "$TMP_APK" 2>&1)
		fi
		echo "$install_output" >"$tmp_log"
	) &
	local pid=$!

	local i=0
	while kill -0 $pid 2>/dev/null; do
		clear
		case $((i % 4)) in
		0) echo "[-] Installing NextCore Manager..." ;;
		1) echo "[/] Installing NextCore Manager..." ;;
		2) echo "[|] Installing NextCore Manager..." ;;
		3) echo "[\] Installing NextCore Manager..." ;;
		esac
		sleep 0.1
		i=$((i + 1))
		
		# Pengecekan batas waktu
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
		printf "[✓] NextCore Manager installed successfully\n"

		pm enable --user 0 zx.azenith/.Launcher >/dev/null 2>&1
		pm grant zx.azenith android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1
		pm grant zx.azenith android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
		pm grant zx.azenith android.permission.READ_MEDIA_IMAGES >/dev/null 2>&1

		echo "- Launching manager..."
		sleep 1
		return 0
	else
		echo "[!] Failed to install NextCore Manager"
		if echo "$result" | grep -iq "Timeout"; then
			echo "  Error: Installation failed due to Timeout"
            echo "  Possible reason: Your Rom blocking background install."
		else
			echo "  Log: $result"
		fi
		echo "  Please install AZenith.apk manually."
		sleep 3
		return 1
	fi
}

clear

if ! _app_installed; then
	echo "[*] App not detected. Preparing installation..."
	sleep 1

	if _prepare_apk; then
		if install_manager; then
			_cleanup_apk
			if _service_exists; then
				exec "$BIN_SVC" --appactivity >/dev/null 2>&1
			else
				echo "[!] Service binary not found at $BIN_SVC" >&2
				exit 1
			fi
		else
			_cleanup_apk
			exit 1
		fi
	else
		exit 1
	fi
else
	if _service_exists; then
		echo "[*] Launching NextCore Manager..."
		exec "$BIN_SVC" --appactivity >/dev/null 2>&1
	else
		echo "[!] Service binary not found at $BIN_SVC" >&2
		exit 1
	fi
fi
