#include "AZenith.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

/* تعريف العقد بـ 4 حقول مطابقة لـ AZenith.h: (device, path, on_val, off_val) */
BypassNode bypass_list[] = {
    // 1. كوالكوم وشاومي (HyperOS / MIUI)
    {"Xiaomi / Qualcomm", "/sys/class/power_supply/battery/input_suspend", "1", "0"},
    {"Qualcomm Secondary", "/sys/class/qcom-battery/input_suspend", "1", "0"},
    
    // 2. ميديا تيك وأجهزة Transsion (Infinix / Tecno Gaming)
    {"MediaTek / Transsion", "/sys/devices/platform/charger/bypass_charge", "1", "0"},
    {"Generic Battery", "/sys/class/power_supply/battery/charging_enabled", "0", "1"},
    {"Generic Battery Alt", "/sys/class/power_supply/battery/battery_charging_enabled", "0", "1"},
    
    // 3. Asus ROG / BlackShark
    {"Asus ROG / BlackShark", "/sys/class/power_supply/battery/charge_control_limit_max", "1", "0"},
    {"Motorola", "/sys/class/power_supply/battery/mmi_charging_enable", "0", "1"},
    
    // 4. Google Pixel و AOSP القياسي
    {"Google Pixel", "/sys/class/power_supply/battery/charge_control_limit", "0", "1"},
    {"Google Pixel Platform", "/sys/devices/platform/google,battery/power_supply/battery/charge_control_limit", "0", "1"},

    // 5. سامسونج
    {"Samsung", "/sys/class/power_supply/battery/batt_slate_mode", "1", "0"}
};

const int bypass_list_size = (int)(sizeof(bypass_list) / sizeof(bypass_list[0]));

static int g_active_node_index = -1;

static int is_node_writable(const char *path) {
    if (!path || access(path, F_OK) != 0) return 0;
    if (access(path, W_OK) == 0) return 1;

    chmod(path, 0666);
    return (access(path, W_OK) == 0);
}

int detect_bypass_node(void) {
    if (g_active_node_index != -1) {
        return g_active_node_index;
    }

    for (int i = 0; i < bypass_list_size; i++) {
        if (is_node_writable(bypass_list[i].path)) {
            g_active_node_index = i;
            return g_active_node_index;
        }
    }
    return -1;
}

int enable_bypass_charging(void) {
    int idx = detect_bypass_node();
    if (idx < 0) return 0;

    FILE *fp = fopen(bypass_list[idx].path, "w");
    if (!fp) return 0;

    fputs(bypass_list[idx].on_val, fp);
    fclose(fp);
    return 1;
}

int disable_bypass_charging(void) {
    int idx = detect_bypass_node();
    if (idx < 0) return 0;

    FILE *fp = fopen(bypass_list[idx].path, "w");
    if (!fp) return 0;

    fputs(bypass_list[idx].off_val, fp);
    fclose(fp);
    return 1;
}

const char* get_current_bypass_path(void) {
    int idx = detect_bypass_node();
    if (idx >= 0) {
        return bypass_list[idx].path;
    }
    return "Unsupported / Not Found";
}
