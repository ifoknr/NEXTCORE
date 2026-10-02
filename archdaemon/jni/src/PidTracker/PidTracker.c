#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/resource.h>
#include <sched.h>

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

    // 1. رفع أولوية العملية الرئيسية إلى Nice -10
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
                    // خيط الرندرة والصوت يأخذ أولوية مرتفعة
                    setpriority(PRIO_PROCESS, tid, -15);

                    // تحرير قيود الأنوية والسماح بالعمل على جميع الأنوية
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