/*
 * Copyright (C) 2024-2025 Rem01Gaming x Zexshia
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

#include <AZenith.h>
#include <sys/system_properties.h>
#include <time.h>

/**
 * @brief Trims a newline character at the end of a string if present.
 * @param string The string to trim.
 * @return Pointer to the modified string, or NULL if input was NULL.
 */
[[gnu::always_inline]] char* trim_newline(char* string) {
    if (string == NULL)
        return NULL;

    char* end;
    if ((end = strchr(string, '\n')) != NULL)
        *end = '\0';

    return string;
}

/**
 * @brief Escape single quotes in a string to make it safe for shell execution.
 * @param dest Destination buffer.
 * @param src Source string.
 * @param max_size Maximum size of destination buffer.
 */
void escape_shell_string(char* dest, const char* src, size_t max_size) {
    size_t j = 0;
    while (*src && j < max_size - 5) {
        if (*src == '\'') {
            dest[j++] = '\'';
            dest[j++] = '\\';
            dest[j++] = '\'';
            dest[j++] = '\'';
        } else {
            dest[j++] = *src;
        }
        src++;
    }
    dest[j] = '\0';
}

/**
 * @brief Checks that a value is safe to place unquoted in a shell command.
 * @param value String to check (package name, renderer, scale factor, ...).
 * @return true if it only contains [A-Za-z0-9._-] and is non-empty.
 */
bool is_shell_safe(const char* value) {
    if (!value || !value[0])
        return false;

    for (const char* c = value; *c; c++) {
        if (!isalnum((unsigned char)*c) && *c != '.' && *c != '_' && *c != '-')
            return false;
    }
    return true;
}

/**
 * @brief Push an Android broadcast notification.
 * @param title Notification title.
 * @param fmt Format string for the message.
 * @param chrono Chronometer flag as bool.
 * @param timeout_ms Timeout in milliseconds (0 for no timeout).
 */
void notify(const char* title, const char* fmt, bool chrono, int timeout_ms, ...) {
    char message[512];
    va_list args;
    va_start(args, timeout_ms);
    vsnprintf(message, sizeof(message), fmt, args);
    va_end(args);

    /*
     * Run the broadcast without a shell: the title/message may contain an app
     * label chosen by a third-party app, so it must never be parsed by sh.
     */
    const char* action = "zx.azenith.ACTION_MANAGE";
    const char* component = "zx.azenith/zx.azenith.receiver.ZenithReceiver";
    const char* chrono_str = chrono ? "true" : "false";
    char* out;

    if (timeout_ms > 0) {
        char timeout_str[16];
        snprintf(timeout_str, sizeof(timeout_str), "%d", timeout_ms);
        out = execute_direct("/system/bin/cmd", "cmd", "activity", "broadcast", "-a", action, "-n", component,
                             "--es", "notifytitle", title, "--es", "notifytext", message, "--ez", "chrono_bool",
                             chrono_str, "--es", "timeout", timeout_str, NULL);
    } else {
        out = execute_direct("/system/bin/cmd", "cmd", "activity", "broadcast", "-a", action, "-n", component,
                             "--es", "notifytitle", title, "--es", "notifytext", message, "--ez", "chrono_bool",
                             chrono_str, NULL);
    }
    free(out);
}

/**
 * @brief Generates a timestamp with the format [YYYY-MM-DD HH:MM:SS.milliseconds].
 * @return Pointer to a statically allocated string containing the timestamp.
 */
char* timern(void) {
    static char timestamp[64];
    struct timeval tv;
    time_t current_time;
    struct tm* local_time;

    gettimeofday(&tv, NULL);
    current_time = tv.tv_sec;
    local_time = localtime(&current_time);

    if (local_time == NULL) [[clang::unlikely]] {
        strcpy(timestamp, "[TimeError]");
        return timestamp;
    }

    size_t format_result = strftime(timestamp, sizeof(timestamp), "%Y-%m-%d %H:%M:%S", local_time);
    if (format_result == 0) [[clang::unlikely]] {
        strcpy(timestamp, "[TimeFormatError]");
        return timestamp;
    }

    snprintf(timestamp + strlen(timestamp), sizeof(timestamp) - strlen(timestamp), ".%03ld", tv.tv_usec / 1000);

    return timestamp;
}

/**
 * @brief Handles termination signals and exits cleanly.
 * @param signal The received exit signal.
 */
[[noreturn]] void sighandler(const int signal) {
    switch (signal) {
    case SIGTERM:
        log_zenith(LOG_INFO, "Received SIGTERM, exiting.");
        break;
    case SIGINT:
        log_zenith(LOG_INFO, "Received SIGINT, exiting.");
        break;
    }

    _exit(EXIT_SUCCESS);
}

/**
 * @brief Display a toast notification using Zenith receiver.
 * @param message Message to display.
 */
void toast(const char* message) {
    char val[PROP_VALUE_MAX] = {0};

    if (__system_property_get("persist.sys.azenithconf.showtoast", val) > 0 && val[0] == '1') {
        char* out = execute_direct("/system/bin/cmd", "cmd", "activity", "broadcast", "-a",
                                   "zx.azenith.ACTION_MANAGE", "-n", "zx.azenith/.receiver.ZenithReceiver",
                                   "--es", "toasttext", message, NULL);
        int exit = out ? 0 : 1;
        free(out);

        if (exit != 0) [[clang::unlikely]] {
            log_zenith(LOG_WARN, "Unable to send toast broadcast: %s", message);
        }
    }
}

/**
 * @brief Exits the program if the module state is set to "stopped" or is empty.
 */
void checkstate(void) {
    char state[64] = {0};
    FILE* fp = popen("getprop persist.sys.azenith.state", "r");
    if (fp) {
        fgets(state, sizeof(state), fp);
        pclose(fp);
    }
    state[strcspn(state, "\n")] = 0;
    if (state[0] == '\0' || strcmp(state, "stopped") == 0) [[clang::unlikely]] {
        goto killsvc;
    }
    return;
killsvc:
    log_zenith(LOG_FATAL, "Service killed by checkstate().");
    __system_property_set("persist.sys.azenith.service", "");
    __system_property_set("persist.sys.azenith.state", "stopped");
    exit(EXIT_FAILURE);
}

/**
 * @brief Error fallback function that always returns true.
 * @note Never call this function directly.
 * @return Always true.
 */
bool return_true(void) {
    return true;
}

/**
 * @brief Error fallback function that always returns false.
 * @note Never call this function directly.
 * @return Always false.
 */
bool return_false(void) {
    return false;
}

/**
 * @brief Runs the thermalcore service in the background if enabled in properties.
 */
void runthermalcore(void) {
    char thermalcore[PROP_VALUE_MAX] = {0};
    __system_property_get("persist.sys.azenithconf.thermalcore", thermalcore);
    if (strcmp(thermalcore, "1") == 0) {
        __system_property_set("persist.sys.rianixia.thermalcore-bigdata.path", "/data/adb/.config/AZenith/debug");
        systemv("sys.azenith-rianixiathermalcore &");
        FILE* fp = popen("pidof sys.azenith-rianixiathermalcore", "r");
        if (fp == NULL) {
            perror("pidof failed");
            log_zenith(LOG_INFO, "Failed to run Thermalcore service");
            return;
        }
        char pid_str[32] = {0};
        if (fgets(pid_str, sizeof(pid_str), fp) != NULL) {
            int pid = atoi(pid_str);
            log_zenith(LOG_INFO, "Starting Thermalcore Service with pid %d", pid);
        } else {
            log_zenith(LOG_INFO, "Thermalcore Service started but PID not found");
        }

        pclose(fp);
    }
}

/**
 * @brief Extracts value from a "key: value" string format.
 * @param dest Destination buffer for the extracted value.
 * @param key_pos Pointer to the start of the key/string.
 * @param max_len Maximum length to copy into dest.
 */
void extract_string_value(char* dest, const char* key_pos, size_t max_len) {
    if (!key_pos) {
        strncpy(dest, "default", max_len - 1);
        dest[max_len - 1] = '\0';
        return;
    }

    const char* colon = strchr(key_pos, ':');
    if (!colon) {
        strncpy(dest, "default", max_len - 1);
        dest[max_len - 1] = '\0';
        return;
    }

    const char* start = colon + 1;
    while (*start == ' ' || *start == '\t')
        start++;

    if (*start == '\"')
        start++;

    const char* end = strchr(start, '\"');
    if (!end) {
        strncpy(dest, "default", max_len - 1);
        dest[max_len - 1] = '\0';
        return;
    }

    size_t len = end - start;
    if (len >= max_len)
        len = max_len - 1;
    strncpy(dest, start, len);
    dest[len] = '\0';
}

/**
 * @brief Force-restarts the target app so pending Game Mode / renderer
 *        interventions take effect. Shared by renderer and resolution
 *        handlers to avoid double restarts.
 * @param pkg Target package name to restart.
 */
void restart_target_app(const char* pkg) {
    if (!is_shell_safe(pkg)) {
        log_zenith(LOG_WARN, "RestartHandler: Refusing to restart invalid package name");
        return;
    }
    log_zenith(LOG_INFO, "RestartHandler: Restarting %s to apply pending changes...", pkg);
    is_restarting_renderer = true; // reuse existing flag, guards inotify/pid logic during respawn
    systemv("am force-stop %s && am start -n $(cmd package resolve-activity --brief %s | tail -n 1)",
            pkg, pkg);
    usleep(500000);
    is_restarting_renderer = false;
}

/**
 * @brief Rewrites the `description=` field in module.prop to show a live
 *        status banner with the daemon's PID. Called once the daemon has
 *        finished initialization so the module manager UI reflects that
 *        AZenith is actually running.
 * @param pid PID of the running daemon process, shown in the banner.
 */
void update_module_description(pid_t pid) {
    FILE *fp = fopen(MODULE_PROP, "r");
    if (!fp) {
        log_zenith(LOG_ERROR, "Failed to open module.prop for description update");
        return;
    }

    char lines[128][512];
    int line_count = 0;
    while (line_count < 128 && fgets(lines[line_count], sizeof(lines[line_count]), fp)) {
        line_count++;
    }
    fclose(fp);

    char new_desc[256];
    snprintf(new_desc, sizeof(new_desc),
             "description=✅ Running (PID %d) · Advanced performance engine\n",
             pid);

    fp = fopen(MODULE_PROP, "w");
    if (!fp) {
        log_zenith(LOG_ERROR, "Failed to write module.prop for description");
        return;
    }

    bool replaced = false;
    for (int i = 0; i < line_count; i++) {
        if (strncmp(lines[i], "description=", 12) == 0) {
            fputs(new_desc, fp);
            replaced = true;
        } else {
            fputs(lines[i], fp);
        }
    }
    if (!replaced) fputs(new_desc, fp);
    fclose(fp);
}
