/*
 * Copyright (C) 2024-2025 Zexshia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "NextCore.h"

const char* VALID_NEXTCORE_PROPS[] = {
    "persist.sys.nextcore.custom_default_balanced_IO",
    "persist.sys.nextcore.custom_default_cpu_gov",
    "persist.sys.nextcore.custom_default_maligpu_gov",
    "persist.sys.nextcore.custom_performance_IO",
    "persist.sys.nextcore.custom_performance_cpu_gov",
    "persist.sys.nextcore.custom_performance_maligpu_gov",
    "persist.sys.nextcore.custom_powersave_IO",
    "persist.sys.nextcore.custom_powersave_cpu_gov",
    "persist.sys.nextcore.custom_powersave_maligpu_gov",
    "persist.sys.nextcore.debugmode",
    "persist.sys.nextcore.default_balanced_IO",
    "persist.sys.nextcore.default_cpu_gov",
    "persist.sys.nextcore.default_maligpu_gov",
    "persist.sys.nextcore.disabletweak",
    "persist.sys.nextcore.service",
    "persist.sys.nextcore.soctype",
    "persist.sys.nextcore.state",
    "persist.sys.nextcore.profilenotifications",
    "persist.sys.nextcore.dropforeground",
    "persist.sys.nextcoreconf.AIenabled",
    "persist.sys.nextcoreconf.APreload",
    "persist.sys.nextcoreconf.SFL",
    "persist.sys.nextcoreconf.bypasschg",
    "persist.sys.nextcoreconf.bypasschgthreshold",
    "persist.sys.nextcoreconf.renderer",
    "persist.sys.nextcoreconf.bypasspath",
    "persist.sys.nextcoreconf.clearbg",
    "persist.sys.nextcoreconf.cpulimit",
    "persist.sys.nextcoreconf.disabletrace",
    "persist.sys.nextcoreconf.dnd",
    "persist.sys.nextcoreconf.fpsged",
    "persist.sys.nextcoreconf.freqoffset",
    "persist.sys.nextcoreconf.fstrim",
    "persist.sys.nextcoreconf.iosched",
    "persist.sys.nextcoreconf.justintime",
    "persist.sys.nextcoreconf.litemode",
    "persist.sys.nextcoreconf.logd",
    "persist.sys.nextcoreconf.perfmax",
    "persist.sys.nextcoreconf.malisched",
    "persist.sys.nextcoreconf.preloadbudget",
    "persist.sys.nextcoreconf.renderer",
    "persist.sys.nextcoreconf.schedtunes",
    "persist.sys.nextcoreconf.schemeconfig",
    "persist.sys.nextcoreconf.showtoast",
    "persist.sys.nextcoreconf.thermalcore",
    "persist.sys.nextcoreconf.usefpsgo",
    "persist.sys.nextcoreconf.walttunes",
};
const size_t VALID_NEXTCORE_PROPS_COUNT = sizeof(VALID_NEXTCORE_PROPS) / sizeof(VALID_NEXTCORE_PROPS[0]);

/**
 * @brief Checks whether a given property name belongs to the known/whitelisted
 *        set of NextCore properties currently in use.
 *
 * @param name Null-terminated property name to check.
 * @return true if the property is recognized as valid, false otherwise.
 */
static bool is_known_nextcore_prop(const char *name) {
    for (size_t i = 0; i < VALID_NEXTCORE_PROPS_COUNT; i++) {
        if (strcmp(name, VALID_NEXTCORE_PROPS[i]) == 0)
            return true;
    }
    return false;
}

typedef struct {
    char names[MAX_PENDING_DELETE][MAX_PROP_NAME_BUF];
    int  count;
} StalePropList;

/**
 * @brief Callback invoked by __system_property_read_callback for each property.
 *        Receives the original, untruncated name and value directly from the
 *        property area.
 *
 * @param cookie  Pointer to a StalePropList used to accumulate results.
 * @param name    Full-length original property name.
 * @param value   Property value (unused here).
 * @param serial  Property serial number (unused here).
 */
static void read_prop_cb(void *cookie, const char *name, const char *value, uint32_t serial) {
    (void)value;
    (void)serial;
    StalePropList *pending = (StalePropList *)cookie;

    if (strncmp(name, NEXTCORE_PROPERTIES, NEXTCORE_PROPERTIES_LEN) != 0)
        return;

    if (is_known_nextcore_prop(name))
        return;

    if (pending->count < MAX_PENDING_DELETE) {
        strlcpy(pending->names[pending->count], name, MAX_PROP_NAME_BUF);
        pending->count++;
        log_zenith(LOG_WARN, "PropValidator: flagged stale prop -> %s", name);
    } else {
        log_zenith(LOG_WARN, "PropValidator: buffer full, skip %s", name);
    }
}

/**
 * @brief Foreach-level callback invoked per prop_info by __system_property_foreach.
 *        Forwards to __system_property_read_callback to obtain the full,
 *        untruncated property name.
 *
 * @param pi     Property handle provided by foreach.
 * @param cookie Pointer to a StalePropList, forwarded to the next callback.
 */
static void foreach_prop_cb(const prop_info *pi, void *cookie) {
    __system_property_read_callback(pi, read_prop_cb, cookie);
}

/**
 * @brief Validates crucial system files and module integrity before startup.
 */
void validateprop(void) {
    StalePropList pending = { .count = 0 };

    __system_property_foreach(foreach_prop_cb, &pending);

    if (pending.count == 0) {
        log_zenith(LOG_INFO, "PropValidator: no unused prop found, continue...");
        return;
    }

    log_zenith(LOG_INFO, "PropValidator: Found %d unused prop cleaning...", pending.count);

    for (int i = 0; i < pending.count; i++) {
        char cmd[MAX_PROP_NAME_BUF + 32];
        snprintf(cmd, sizeof(cmd), "resetprop -p --delete %s", pending.names[i]);
        log_zenith(LOG_WARN, "PropValidator: delete -> %s", pending.names[i]);
        systemv("%s", cmd);
    }
}
