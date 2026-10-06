#!/system/bin/sh
#
# NextCore device probe.
#
# Detects the SoC, CPU clusters and the sysfs nodes the manager app reads for
# live monitoring, then writes them as key=value lines to
#   /data/adb/.config/AZenith/device_profile
#
# Sourced by customize.sh at install time and run by service.sh at boot, so the
# profile follows kernel or ROM changes. Only reads sysfs; writes nothing else.
#
# Usage: devprobe_run [quiet]
#

DEVPROBE_OUT="/data/adb/.config/AZenith/device_profile"

_dp_say() {
	[ "$_DP_QUIET" = "1" ] && return 0
	if command -v ui_print >/dev/null 2>&1; then ui_print "$1"; else echo "$1"; fi
}

# First integer in a file, or empty
_dp_int() {
	[ -r "$1" ] || return 1
	tr -c '0-9\n-' ' ' <"$1" 2>/dev/null | awk '{for(i=1;i<=NF;i++) if ($i ~ /^-?[0-9]+$/) {print $i; exit}}'
}

# SoC vendor -> sets DP_SOC_VENDOR, DP_SOC_TYPE, DP_SOC_MODEL
_dp_soc() {
	DP_SOC_MODEL="$(getprop ro.soc.model)"
	DP_SOC_SOURCE="ro.soc"
	_raw=""
	case "$(getprop ro.soc.manufacturer | tr '[:upper:]' '[:lower:]')" in
	*mediatek*) _raw="mt $DP_SOC_MODEL" ;;
	*qti* | *qualcomm*) _raw="qcom $DP_SOC_MODEL" ;;
	*samsung*) _raw="exynos $DP_SOC_MODEL" ;;
	*google*) _raw="tensor $DP_SOC_MODEL" ;;
	*unisoc* | *spreadtrum*) _raw="unisoc $DP_SOC_MODEL" ;;
	esac
	[ -z "$_raw" ] && DP_SOC_SOURCE="cpuinfo" && _raw=$(grep -i 'hardware' /proc/cpuinfo | uniq | cut -d ':' -f2 | sed 's/^[ \t]*//')
	[ -z "$_raw" ] && DP_SOC_SOURCE="platform" && _raw="$(getprop ro.board.platform) $(getprop ro.hardware)"
	[ -z "$DP_SOC_MODEL" ] && DP_SOC_MODEL="$(getprop ro.board.platform)"
	DP_SOC_RAW="$_raw"

	case "$(echo "$_raw" | tr '[:upper:]' '[:lower:]')" in
	*mt*) DP_SOC_VENDOR="MediaTek"; DP_SOC_TYPE=1 ;;
	*sm* | *qcom* | *qualcomm* | *sdm* | *snapdragon*) DP_SOC_VENDOR="Snapdragon"; DP_SOC_TYPE=2 ;;
	*exynos* | *universal* | *samsung* | *erd* | *s5e*) DP_SOC_VENDOR="Exynos"; DP_SOC_TYPE=3 ;;
	*unisoc* | *ums*) DP_SOC_VENDOR="Unisoc"; DP_SOC_TYPE=4 ;;
	*gs* | *tensor*) DP_SOC_VENDOR="Tensor"; DP_SOC_TYPE=5 ;;
	*) DP_SOC_VENDOR="Unknown"; DP_SOC_TYPE=0 ;;
	esac

	[ "$DP_SOC_TYPE" -eq 0 ] && _dp_soc_by_nodes
}

# The name gave nothing: recognise the SoC family by the kernel drivers it
# exposes, so a renamed or unlisted chip still gets its family's tweaks.
_dp_soc_by_nodes() {
	if [ -d /proc/gpufreqv2 ] || [ -d /proc/gpufreq ] || [ -d /sys/kernel/fpsgo ] || [ -d /proc/ppm ]; then
		DP_SOC_VENDOR="MediaTek"; DP_SOC_TYPE=1
	elif [ -d /sys/class/kgsl/kgsl-3d0 ]; then
		DP_SOC_VENDOR="Snapdragon"; DP_SOC_TYPE=2
	elif [ -d /sys/kernel/gpu ] && [ -n "$(ls -d /sys/devices/platform/*.mali 2>/dev/null)" ]; then
		DP_SOC_VENDOR="Exynos"; DP_SOC_TYPE=3
	elif ls /sys/class/devfreq 2>/dev/null | grep -qi 'sprd\|unisoc'; then
		DP_SOC_VENDOR="Unisoc"; DP_SOC_TYPE=4
	else
		return 0
	fi
	DP_SOC_SOURCE="kernel nodes"
}

# CPU clusters as "first-last:maxkhz" separated by spaces
_dp_clusters() {
	DP_CLUSTERS=""
	DP_CPU_COUNT=$(grep -c '^processor' /proc/cpuinfo 2>/dev/null)
	for p in /sys/devices/system/cpu/cpufreq/policy*; do
		[ -d "$p" ] || continue
		_cpus=$(cat "$p/related_cpus" 2>/dev/null)
		[ -z "$_cpus" ] && continue
		_first=$(echo $_cpus | awk '{print $1}')
		_last=$(echo $_cpus | awk '{print $NF}')
		_max=$(_dp_int "$p/cpuinfo_max_freq")
		DP_CLUSTERS="$DP_CLUSTERS ${_first}-${_last}:${_max:-0}"
	done
	DP_CLUSTERS="${DP_CLUSTERS# }"
}

# Best thermal zone for CPU temperature. Patterns are tried in order, so the
# SoC-wide sensors win over a single core sensor.
_dp_cpu_temp() {
	DP_CPU_TEMP_PATH=""
	DP_CPU_TEMP_LABEL=""
	for pat in 'soc_max' 'cpu-big' 'cpu_big' 'mtktscpu' 'cpu-1-' 'cpuss-' 'cpu-0-' 'cpu_therm' 'cpu' 'tsens_tz_sensor' 'soc'; do
		for z in /sys/class/thermal/thermal_zone*; do
			[ -r "$z/type" ] || continue
			_t=$(cat "$z/type" 2>/dev/null)
			case "$_t" in
			*"$pat"*) ;;
			*) continue ;;
			esac
			_v=$(_dp_int "$z/temp")
			[ -z "$_v" ] && continue
			# Accept millidegrees (10000..125000) or degrees (10..125)
			if { [ "$_v" -ge 10000 ] && [ "$_v" -le 125000 ]; } || { [ "$_v" -ge 10 ] && [ "$_v" -le 125 ]; }; then
				DP_CPU_TEMP_PATH="$z/temp"
				DP_CPU_TEMP_LABEL="$(basename "$z") · $_t"
				return 0
			fi
		done
	done
}

_dp_gpu() {
	DP_GPU_FREQ_PATH=""
	DP_GPU_LOAD_PATH=""
	DP_GPU_NAME=""
	if [ -r /sys/class/kgsl/kgsl-3d0/gpuclk ]; then
		DP_GPU_FREQ_PATH=/sys/class/kgsl/kgsl-3d0/gpuclk
		[ -r /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage ] && DP_GPU_LOAD_PATH=/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
		DP_GPU_NAME=$(cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null)
	fi
	if [ -z "$DP_GPU_FREQ_PATH" ]; then
		for d in /sys/class/devfreq/*; do
			case "$(basename "$d")" in
			*mali* | *gpu* | *kgsl* | *g3d* | *pvr*)
				[ -r "$d/cur_freq" ] || continue
				DP_GPU_FREQ_PATH="$d/cur_freq"
				[ -z "$DP_GPU_LOAD_PATH" ] && [ -r "$d/load" ] && DP_GPU_LOAD_PATH="$d/load"
				break
				;;
			esac
		done
	fi
	if [ -z "$DP_GPU_FREQ_PATH" ] && [ -r /sys/kernel/ged/hal/current_freqency ]; then
		DP_GPU_FREQ_PATH=/sys/kernel/ged/hal/current_freqency
	fi
	if [ -z "$DP_GPU_LOAD_PATH" ]; then
		for f in /sys/kernel/ged/hal/gpu_utilization /sys/module/ged/parameters/gpu_loading /sys/kernel/gpu/gpu_busy; do
			[ -r "$f" ] && { DP_GPU_LOAD_PATH="$f"; break; }
		done
	fi
	if [ -z "$DP_GPU_NAME" ]; then
		DP_GPU_NAME=$(getprop ro.hardware.egl)
		case "$DP_GPU_NAME" in
		*mali*) DP_GPU_NAME="Mali" ;;
		*adreno*) DP_GPU_NAME="Adreno" ;;
		*powervr* | *pvr*) DP_GPU_NAME="PowerVR" ;;
		*)
			# Some ROMs put arbitrary text in ro.hardware.egl; use the GPU family from the node instead
			case "$DP_GPU_FREQ_PATH" in
			*mali*) DP_GPU_NAME="Mali" ;;
			*kgsl*) DP_GPU_NAME="Adreno" ;;
			*pvr*) DP_GPU_NAME="PowerVR" ;;
			esac
			;;
		esac
	fi
}

_dp_battery() {
	DP_BATT_DIR=""
	for b in /sys/class/power_supply/battery /sys/class/power_supply/Battery /sys/class/power_supply/bms; do
		[ -r "$b/capacity" ] && { DP_BATT_DIR="$b"; break; }
	done
	DP_BATT_CURRENT_PATH=""
	[ -n "$DP_BATT_DIR" ] && [ -r "$DP_BATT_DIR/current_now" ] && DP_BATT_CURRENT_PATH="$DP_BATT_DIR/current_now"
}

devprobe_run() {
	_DP_QUIET="$1"
	[ "$_DP_QUIET" = "quiet" ] && _DP_QUIET=1

	_dp_soc
	_dp_clusters
	_dp_cpu_temp
	_dp_gpu
	_dp_battery

	if [ "$DP_SOC_TYPE" -ne 0 ]; then
		DP_SUPPORT="FULL"
	else
		DP_SUPPORT="PARTIAL"
	fi

	mkdir -p "$(dirname "$DEVPROBE_OUT")"
	cat >"$DEVPROBE_OUT.tmp" <<EOF
device_model=$(getprop ro.product.model)
device_brand=$(getprop ro.product.brand)
device_codename=$(getprop ro.product.device)
android=$(getprop ro.build.version.release)
sdk=$(getprop ro.build.version.sdk)
kernel=$(uname -r)
soc_vendor=$DP_SOC_VENDOR
soc_type=$DP_SOC_TYPE
soc_model=$DP_SOC_MODEL
soc_source=$DP_SOC_SOURCE
soc_raw=$DP_SOC_RAW
support=$DP_SUPPORT
cpu_count=$DP_CPU_COUNT
cpu_clusters=$DP_CLUSTERS
cpu_temp_path=$DP_CPU_TEMP_PATH
cpu_temp_label=$DP_CPU_TEMP_LABEL
gpu_name=$DP_GPU_NAME
gpu_freq_path=$DP_GPU_FREQ_PATH
gpu_load_path=$DP_GPU_LOAD_PATH
battery_dir=$DP_BATT_DIR
battery_current_path=$DP_BATT_CURRENT_PATH
probed_at=$(date +%s)
EOF
	mv -f "$DEVPROBE_OUT.tmp" "$DEVPROBE_OUT"
	chmod 0644 "$DEVPROBE_OUT"

	_dp_say "- Device : $(getprop ro.product.model) ($(getprop ro.product.device))"
	_dp_say "- SoC    : $DP_SOC_VENDOR ${DP_SOC_MODEL:-?}"
	_dp_say "- CPU    : ${DP_CPU_COUNT:-?} cores, clusters ${DP_CLUSTERS:-?}"
	if [ -n "$DP_CPU_TEMP_PATH" ]; then
		_dp_say "- CPU temperature sensor: $DP_CPU_TEMP_LABEL"
	else
		_dp_say "- CPU temperature sensor: not found"
	fi
	_dp_say "- GPU    : ${DP_GPU_FREQ_PATH:-not found}"
	_dp_say "- Battery current: ${DP_BATT_CURRENT_PATH:-not found}"
	if [ "$DP_SUPPORT" = "FULL" ]; then
		_dp_say "✅ Support level: FULL — general + $DP_SOC_VENDOR tweaks"
	else
		_dp_say "⚠️ Support level: PARTIAL — unknown SoC, only general tweaks"
	fi
}
