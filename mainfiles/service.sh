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

readonly MODDIR="${0%/*}"
readonly MODULE_CONFIG="/data/adb/.config/AZenith"
readonly BIN_SVC="$MODDIR/system/bin/sys.azenith-service"
readonly APK_COMP="$MODDIR/AZenith.apk"
readonly BOOT_LOG="$MODULE_CONFIG/debug/boot.log"

# Boot diagnostics: every step and any early daemon error lands in boot.log
# (the daemon's own AZenith.log stays empty if it dies before starting)
mkdir -p "$MODULE_CONFIG/API" "$MODULE_CONFIG/debug" "$MODULE_CONFIG/gamelist" \
    "$MODULE_CONFIG/bypasschgconfig" "$MODULE_CONFIG/preload"
: >"$BOOT_LOG"
blog() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >>"$BOOT_LOG"
}
blog "NextCore service.sh started (MODDIR=$MODDIR)"

# Failsafe: touch /data/local/tmp/nextcore_abort to skip starting NextCore
if [ -f "/data/local/tmp/nextcore_abort" ]; then
    blog "nextcore_abort found, not starting"
    exit 0
fi

# Wait boot to complete
until [ "$(getprop sys.boot_completed)" = "1" ]; do 
    sleep 1
done

blog "Boot completed"

# Reset anti bootloop
echo "BOOTCOUNT=0" > "$MODDIR/count.sh"

# The old AZenith module shares binaries, config and app with NextCore; running both breaks startup
if [ -d /data/adb/modules/AZenith ] && [ ! -f /data/adb/modules/AZenith/disable ]; then
    blog "WARNING: AZenith module is installed and enabled; it conflicts with NextCore. Disabling it."
    touch /data/adb/modules/AZenith/disable
    for p in $(/system/bin/toybox pidof sys.azenith-service sys.azenith-appmonitoring); do
        kill -TERM "$p" 2>/dev/null
    done
    sleep 1
fi

# Make sure binaries are executable and required files exist
chmod 0755 "$MODDIR"/system/bin/* 2>/dev/null
for bin in sys.azenith-service sys.azenith-profilesettings sys.azenith-utilityconf \
    sys.azenith-preferencedtweaks sys.azenith-rianixiathermalcore sys.azenith-preloadbin; do
    [ -x "$MODDIR/system/bin/$bin" ] || blog "ERROR: missing or not executable: $bin"
done
[ -f "$APK_COMP" ] || blog "ERROR: AZenith.apk missing from module (AppMonitor cannot start)"
if [ ! -f "$MODULE_CONFIG/gamelist/azenithApplist.json" ] && [ -f "$MODDIR/azenithApplist.json" ]; then
    blog "Gamelist missing, restoring default"
    cp "$MODDIR/azenithApplist.json" "$MODULE_CONFIG/gamelist/azenithApplist.json"
fi

# Clear Old Logs
"$BIN_SVC" --clearlogs

# Remove reboot flag
if [ -f "$MODDIR/reboot" ]; then
    rm -f "$MODDIR/reboot"
fi

# Create Cleanup Files
if [ ! -f /data/adb/service.d/.azenith_cleanup.sh ]; then
  mkdir -p /data/adb/service.d
  cat "$MODDIR/cleanup.sh" > /data/adb/service.d/.azenith_cleanup.sh
  chmod +x /data/adb/service.d/.azenith_cleanup.sh
fi

# Refresh AZenith daemon state
STATE=$(getprop persist.sys.azenith.state)
{ [ -z "$STATE" ] || { [ "$STATE" = "running" ] && [ -z "$(/system/bin/toybox pidof sys.azenith-service)" ]; }; } && {
    setprop persist.sys.azenith.state stopped
    setprop persist.sys.azenith.service ""
}

# Exec Java Companion Daemon
nohup app_process -Djava.class.path="$APK_COMP" / \
    --nice-name=sys.azenith-appmonitoring zx.azenith.AppMonitor \
    "$MODULE_CONFIG/app_status" \
    "$MODULE_CONFIG/background_apps" \
    "$MODULE_CONFIG/java.lock" >"$MODULE_CONFIG/sysmon.log" 2>&1 &

sleep 3
if [ -n "$(/system/bin/toybox pidof sys.azenith-appmonitoring)" ]; then
    blog "AppMonitor running"
else
    blog "ERROR: AppMonitor failed to start. sysmon.log:"
    tail -n 20 "$MODULE_CONFIG/sysmon.log" >>"$BOOT_LOG" 2>/dev/null
fi

# Run NextCore service (it daemonizes itself; early errors go to boot.log)
blog "Starting sys.azenith-service"
"$BIN_SVC" --run >>"$BOOT_LOG" 2>&1
sleep 2
if [ -n "$(/system/bin/toybox pidof sys.azenith-service)" ]; then
    blog "Service running (PID $(/system/bin/toybox pidof sys.azenith-service))"
else
    blog "ERROR: service is not running. Last daemon log lines:"
    tail -n 20 "$MODULE_CONFIG/debug/AZenith.log" >>"$BOOT_LOG" 2>/dev/null
fi
