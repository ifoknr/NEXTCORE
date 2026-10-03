#define _GNU_SOURCE
#include "AZenith.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/resource.h>
#include <sched.h>

/* الدالة المطلوبة لربط InotifyWatcher.c و System.c */
pid_t get_pids_of(const char *process_name) {
    if (!process_name || process_name[0] == '\0') {
        return -1;
    }

    DIR *dir = opendir("/proc");
    if (!dir) return -1;

    struct dirent *entry;
    pid_t found_pid = -1;

    while ((entry = readdir(dir)) != NULL) {
        if (entry->d_name[0] < '0' || entry->d_name[0] > '9') continue;

        int pid = atoi(entry->d_name);
        if (pid <= 0) continue;

        char cmdline_path[64];
        snprintf(cmdline_path, sizeof(cmdline_path), "/proc/%d/cmdline", pid);
        int fd = open(cmdline_path, O_RDONLY);
        if (fd >= 0) {
            char cmdline[256] = {0};
            ssize_t bytes = read(fd, cmdline, sizeof(cmdline) - 1);
            close(fd);
            if (bytes > 0) {
                char *colon = strchr(cmdline, ':');
                if (colon) *colon = '\0';
                if (strcmp(cmdline, process_name) == 0) {
                    found_pid = pid;
                    break;
                }
            }
        }

        char comm_path[64];
        snprintf(comm_path, sizeof(comm_path), "/proc/%d/comm", pid);
        FILE *cfp = fopen(comm_path, "r");
        if (cfp) {
            char comm[64] = {0};
            if (fgets(comm, sizeof(comm), cfp)) {
                comm[strcspn(comm, "\r\n")] = '\0';
                if (strcmp(comm, process_name) == 0) {
                    found_pid = pid;
                    fclose(cfp);
                    break;
                }
            }
            fclose(cfp);
        }
    }

    closedir(dir);
    return found_pid;
}

/* الدالة المطلوبة لربط System.c عند إقلاع الـ Daemon */
static pid_t s_spid = -1;

void setspid(void) {
    pid_t pid = get_pids_of("system_server");
    if (pid > 0) {
        s_spid = pid;
    } else {
        s_spid = getpid();
    }
}

static int is_render_thread(const char *thread_name) {
    const char *critical_threads[] = {
        "RenderThread",
        "UnityMain",
        "GLThread",
        "vkQueue",
        "AudioTrack",
        "MainThread"
    };
    for (size_t i = 0; i < sizeof(critical_threads)/sizeof(critical_threads[0]); i++) {
        if (strstr(thread_name, critical_threads[i]) != NULL) {
            return 1;
        }
    }
    return 0;
}

void optimize_game_threads(int game_pid) {
    if (game_pid <= 0) return;

    setpriority(PRIO_PROCESS, game_pid, -10);

    char task_dir_path[64];
    snprintf(task_dir_path, sizeof(task_dir_path), "/proc/%d/task", game_pid);
    DIR *task_dir = opendir(task_dir_path);
    if (!task_dir) return;

    struct dirent *entry;
    while ((entry = readdir(task_dir)) != NULL) {
        if (entry->d_name[0] == '.') continue;

        int tid = atoi(entry->d_name);
        if (tid <= 0) continue;

        char comm_path[128];
        snprintf(comm_path, sizeof(comm_path), "/proc/%d/task/%d/comm", game_pid, tid);
        FILE *fp = fopen(comm_path, "r");
        if (fp) {
            char thread_name[64] = {0};
            if (fgets(thread_name, sizeof(thread_name), fp)) {
                thread_name[strcspn(thread_name, "\n")] = 0;

                if (is_render_thread(thread_name)) {
                    setpriority(PRIO_PROCESS, tid, -15);

                    cpu_set_t cpuset;
                    CPU_ZERO(&cpuset);
                    for (int cpu = 0; cpu < 8; cpu++) {
                        CPU_SET(cpu, &cpuset);
                    }
                    sched_setaffinity(tid, sizeof(cpu_set_t), &cpuset);
                } else {
                    setpriority(PRIO_PROCESS, tid, -5);
                }
            }
            fclose(fp);
        }
    }
    closedir(task_dir);
}

void reset_process_priority(int pid) {
    if (pid <= 0) return;
    setpriority(PRIO_PROCESS, pid, 0);
}
