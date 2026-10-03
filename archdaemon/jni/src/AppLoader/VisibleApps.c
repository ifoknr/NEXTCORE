#include "AZenith.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <sys/types.h>
#include <sys/stat.h>

#define TOP_APP_CGROUP "/dev/cpuset/top-app/cgroup.procs"
#define TOP_APP_CGROUP_V2 "/sys/fs/cgroup/top-app/cgroup.procs"

static char s_last_package[256] = "";
static char s_visible_pkg[256] = "";
static int s_last_pid = -1;

/* مطابقة لتعريف AZenith.h: int uidof(pid_t pid) */
int uidof(pid_t pid) {
    if (pid <= 0) return -1;
    char status_path[64];
    snprintf(status_path, sizeof(status_path), "/proc/%d/status", pid);
    FILE *status_fp = fopen(status_path, "r");
    if (!status_fp) return -1;

    char s_line[128];
    int uid = -1;
    while (fgets(s_line, sizeof(s_line), status_fp)) {
        if (strncmp(s_line, "Uid:", 4) == 0) {
            if (sscanf(s_line, "Uid:\t%d", &uid) == 1) {
                break;
            }
        }
    }
    fclose(status_fp);
    return uid;
}

int get_foreground_pid(void) {
    const char *target_cgroup = TOP_APP_CGROUP;
    if (access(target_cgroup, R_OK) != 0) {
        target_cgroup = TOP_APP_CGROUP_V2;
        if (access(target_cgroup, R_OK) != 0) {
            return -1;
        }
    }

    FILE *fp = fopen(target_cgroup, "r");
    if (!fp) return -1;

    int pid = -1;
    char line[32];
    
    while (fgets(line, sizeof(line), fp)) {
        int temp_pid = atoi(line);
        if (temp_pid <= 1000) continue;

        int uid = uidof(temp_pid);
        if (uid >= 10000) {
            pid = temp_pid;
            break;
        }
    }
    fclose(fp);
    return pid;
}

int get_package_name_by_pid(int pid, char *out_package, size_t max_len) {
    if (pid <= 0) return 0;

    char cmdline_path[64];
    snprintf(cmdline_path, sizeof(cmdline_path), "/proc/%d/cmdline", pid);

    int fd = open(cmdline_path, O_RDONLY);
    if (fd < 0) return 0;

    ssize_t bytes_read = read(fd, out_package, max_len - 1);
    close(fd);

    if (bytes_read > 0) {
        out_package[bytes_read] = '\0';
        char *colon = strchr(out_package, ':');
        if (colon) *colon = '\0';
        return 1;
    }
    return 0;
}

int check_foreground_app_changed(char *current_package, int *current_pid) {
    int pid = get_foreground_pid();
    if (pid <= 0 || pid == s_last_pid) {
        return 0;
    }

    char pkg[256] = {0};
    if (get_package_name_by_pid(pid, pkg, sizeof(pkg))) {
        if (strcmp(pkg, s_last_package) != 0) {
            strncpy(s_last_package, pkg, sizeof(s_last_package) - 1);
            s_last_package[sizeof(s_last_package) - 1] = '\0';
            strncpy(current_package, pkg, 255);
            current_package[255] = '\0';
            s_last_pid = pid;
            *current_pid = pid;
            return 1;
        }
    }
    return 0;
}

/* مطابقة لتعريف AZenith.h: char* get_visible_package(SystemStateCache* cache) */
char* get_visible_package(SystemStateCache* cache) {
    (void)cache;
    int pid = get_foreground_pid();
    if (pid <= 0) {
        return NULL;
    }
    if (get_package_name_by_pid(pid, s_visible_pkg, sizeof(s_visible_pkg))) {
        return s_visible_pkg;
    }
    return NULL;
}
