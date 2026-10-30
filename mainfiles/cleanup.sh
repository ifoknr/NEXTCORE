#!/system/bin/sh

# Removed from the root manager: clear what the module left outside its folder.
cleanup_module() {
    resetprop | awk -F'[][]' '/persist\.sys\.nextcore/ {print $2}' | while read -r prop; do
        resetprop -p --delete "$prop"
    done

    for prop in persist.sys.ncthermal.learning_enabled persist.sys.ncthermal.thermalcore-bigdata.path; do
        resetprop -p --delete "$prop"
    done

    # Wait for PackageManager before uninstalling the app
    until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 2; done
    pm uninstall zx.nextcore >/dev/null 2>&1

    rm -rf \
        "/data/adb/.config/NextCore" \
        "/data/NextCore" \
        "/data/data/zx.nextcore"

    for dir in "/data/adb/ap/bin" "/data/adb/ksu/bin"; do
        rm -rf "$dir/zx"
        [ -d "$dir" ] && find "$dir" -name "sys.nextcore-*" -exec rm -f {} +
    done
}

if [ -f /data/adb/modules/nextcore/disable ]; then
    cat /data/adb/modules/nextcore/module.prop.orig > /data/adb/modules/nextcore/module.prop
    rm /data/adb/service.d/.nextcore_cleanup.sh
elif [ ! -d /data/adb/modules/nextcore ]; then
    cleanup_module
    rm /data/adb/service.d/.nextcore_cleanup.sh
fi
