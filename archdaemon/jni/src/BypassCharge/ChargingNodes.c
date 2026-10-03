#include "AZenith.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>

/* تعريف مسارات العزل للملفات القديمة (BypassCompatibility.c) */
const char *bypass_list[] = {
    "/sys/class/power_supply/battery/input_suspend",
    "/sys/class/qcom-battery/input_suspend",
    "/sys/devices/platform/charger/bypass_charge",
    "/sys/class/power_supply/battery/charging_enabled",
    "/sys/class/power_supply/battery/battery_charging_enabled",
    "/sys/class/power_supply/battery/charge_control_limit_max",
    "/sys/class/power_supply/battery/mmi_charging_enable",
    "/sys/class/power_supply/battery/charge_control_limit",
    "/sys/devices/platform/google,battery/power_supply/battery/charge_control_limit",
    "/sys/class/power_supply/battery/batt_slate_mode"
};

const size_t bypass_list_size = sizeof(bypass_list) / sizeof(bypass_list[0]);

typedef struct {
    const char *node_path;
    const char *disable_value;
    const char *enable_value;
} ChargingNodeInfo;

static const ChargingNodeInfo SUPPORTED_NODES[] = {
    {"/sys/class/power_supply/battery/input_suspend", "1", "0"},
    {"/sys/class/qcom-battery/input_suspend", "1", "0"},
    {"/sys/devices/platform/charger/bypass_charge", "1", "0"},
    {"/sys/class/power_supply/battery/charging_enabled", "0", "1"},
    {"/sys/class/power_supply/battery/battery_charging_enabled", "0", "1"},
    {"/sys/class/power_supply/battery/charge_control_limit_max", "1", "0"},
    {"/sys/class/power_supply/battery/mmi_charging_enable", "0", "1"},
    {"/sys/class/power_supply/battery/charge_control_limit", "0", "1"},
    {"/sys/devices/platform/google,battery/power_supply/battery/charge_control_limit", "0", "1"},
    {"/sys/class/power_supply/battery/batt_slate_mode", "1", "0"}
};

#define TOTAL_NODES (sizeof(SUPPORTED_NODES) / sizeof(SUPPORTED_NODES[0]))

static int g_active_node_index = -1;

static int is_node_writable(const char *path) {
    if (access(path, F_OK) != 0) return 0;
    if (access(path, W_OK) == 0) return 1;

    chmod(path, 0666);
    return (access(path, W_OK) == 0);
}

int detect_bypass_node(void) {
    if (g_active_node_index != -1) {
        return g_active_node_index;
    }

    for (size_t i = 0; i < TOTAL_NODES; i++) {
        if (is_node_writable(SUPPORTED_NODES[i].node_path)) {
            g_active_node_index = (int)i;
            return g_active_node_index;
        }
    }
    return -1;
}

int enable_bypass_charging(void) {
    int idx = detect_bypass_node();
    if (idx < 0) return 0;

    const ChargingNodeInfo *node = &SUPPORTED_NODES[idx];
    FILE *fp = fopen(node->node_path, "w");
    if (!fp) return 0;

    fputs(node->disable_value, fp);
    fclose(fp);
    return 1;
}

int disable_bypass_charging(void) {
    int idx = detect_bypass_node();
    if (idx < 0) return 0;

    const ChargingNodeInfo *node = &SUPPORTED_NODES[idx];
    FILE *fp = fopen(node->node_path, "w");
    if (!fp) return 0;

    fputs(node->enable_value, fp);
    fclose(fp);
    return 1;
}

const char* get_current_bypass_path(void) {
    int idx = detect_bypass_node();
    if (idx >= 0) {
        return SUPPORTED_NODES[idx].node_path;
    }
    return "Unsupported / Not Found";
}
