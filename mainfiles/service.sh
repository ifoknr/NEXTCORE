#!/system/bin/sh
MODDIR=${0%/*}
[ -z "$MODDIR" ] || [ "$MODDIR" = "." ] && MODDIR="/data/adb/modules/nextcore"

# 1. صمام الأمان (Failsafe)
[ -f "/data/local/tmp/nextcore_abort" ] && exit 0

# 2. حلقة انتظار اكتمال الإقلاع مع Timeout آمن (حد أقصى 90 ثانية)
BOOT_TIMEOUT=90
COUNTER=0
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    sleep 2
    COUNTER=$((COUNTER + 2))
    if [ $COUNTER -ge $BOOT_TIMEOUT ]; then
        break
    fi
done

# تأخير إضافي لضمان استقرار السطح الرسومي وخدمات النظام
sleep 5

# 3. تفعيل أذونات الملفات الثنائية
chmod 0755 "$MODDIR/system/bin/"* 2>/dev/null
chmod 0755 "$MODDIR/archdaemon" 2>/dev/null
chmod 0755 "$MODDIR/binprofiles" 2>/dev/null
chmod 0755 "$MODDIR/binpreferenced" 2>/dev/null
chmod 0755 "$MODDIR/action.sh" 2>/dev/null

# 4. تطبيق حاكم المعالج مع التحقق التلقائي والبديل الآمن
for policy in /sys/devices/system/cpu/cpufreq/policy*; do
    [ -d "$policy" ] || continue
    if grep -q "sugov_ext" "$policy/scaling_available_governors" 2>/dev/null; then
        echo "sugov_ext" > "$policy/scaling_governor" 2>/dev/null
    elif grep -q "schedutil" "$policy/scaling_available_governors" 2>/dev/null; then
        echo "schedutil" > "$policy/scaling_governor" 2>/dev/null
    fi
done

# 5. تشغيل خادم الرصد الدائم (archdaemon) في الخلفية
if [ -f "$MODDIR/system/bin/archdaemon" ]; then
    nohup "$MODDIR/system/bin/archdaemon" >/dev/null 2>&1 &
elif [ -f "$MODDIR/archdaemon" ]; then
    nohup "$MODDIR/archdaemon" >/dev/null 2>&1 &
fi

# 6. تطبيق ملف التوازن التلقائي الافتراضي عند الإقلاع
if [ -f "$MODDIR/system/bin/binprofiles" ]; then
    "$MODDIR/system/bin/binprofiles" set balanced >/dev/null 2>&1
elif [ -f "$MODDIR/binprofiles" ]; then
    "$MODDIR/binprofiles" set balanced >/dev/null 2>&1
fi