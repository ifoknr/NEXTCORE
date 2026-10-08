if [ -f /data/adb/modules/nextcore/disable ]; then
  cat /data/adb/modules/nextcore/module.prop.orig > /data/adb/modules/nextcore/module.prop
  rm /data/adb/service.d/.nextcore_cleanup.sh
fi
