#!/system/bin/sh
MODDIR=${0%/*}
[ -z "$MODDIR" ] || [ "$MODDIR" = "." ] && MODDIR="/data/adb/modules/nextcore"
CONF_FILE="$MODDIR/config.json"

mkdir -p "$MODDIR"

case "$1" in
    set_global)
        GOV="$2"
        BYPASS="$3"
        THREAD_OPT="$4"

        # 1. تحديث التفضيلات عبر محرك Rust إن وجد
        if [ -f "$MODDIR/system/bin/binpreferenced" ]; then
            "$MODDIR/system/bin/binpreferenced" update-global --gov "$GOV" --bypass "$BYPASS" --threads "$THREAD_OPT" 2>/dev/null
        fi

        # 2. تطبيق الحاكم فورياً إذا كان يدوياً، أو تطبيق التلقائي
        if [ "$GOV" != "auto" ]; then
            for policy in /sys/devices/system/cpu/cpufreq/policy*; do
                [ -d "$policy" ] && echo "$GOV" > "$policy/scaling_governor" 2>/dev/null
            done
        else
            if [ -f "$MODDIR/system/bin/binprofiles" ]; then
                "$MODDIR/system/bin/binprofiles" set balanced 2>/dev/null
            fi
        fi

        # 3. معالجة عزل الشحن الفوري
        DAEMON_BIN="$MODDIR/system/bin/archdaemon"
        [ -f "$DAEMON_BIN" ] || DAEMON_BIN="$MODDIR/archdaemon"
        if [ -f "$DAEMON_BIN" ]; then
            if [ "$BYPASS" = "1" ]; then
                "$DAEMON_BIN" --enable-bypass 2>/dev/null
            elif [ "$BYPASS" = "0" ]; then
                "$DAEMON_BIN" --disable-bypass 2>/dev/null
            fi
        fi
        ;;

    get_status)
        SOC=$(getprop ro.board.platform)
        TEMP=$(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null | cut -c1-2)
        [ -z "$TEMP" ] && TEMP="--"
        echo "{\"soc\":\"$SOC\",\"temp\":\"$TEMP\"}"
        ;;
esac