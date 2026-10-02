use std::fs;
use std::path::Path;

pub struct DimensityTuner;

impl DimensityTuner {
    fn write_node(path: &str, value: &str) {
        if Path::new(path).exists() {
            let _ = fs::write(path, value.trim());
        }
    }

    pub fn apply_cpu_governor(governor: &str) {
        if let Ok(entries) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*") {
            for entry in entries.flatten() {
                let policy_path = entry.to_string_lossy();
                let avail_govs_path = format!("{}/scaling_available_governors", policy_path);
                let target_gov_path = format!("{}/scaling_governor", policy_path);

                if let Ok(avail) = fs::read_to_string(&avail_govs_path) {
                    if avail.contains(governor) {
                        Self::write_node(&target_gov_path, governor);
                    } else if avail.contains("schedutil") {
                        Self::write_node(&target_gov_path, "schedutil");
                    }
                }
            }
        }
    }

    pub fn apply_performance_profile() {
        Self::apply_cpu_governor("sugov_ext");

        // 1. محرك MediaTek FPSGO
        Self::write_node("/sys/module/mtk_fpsgo/parameters/fbt_enable", "1");
        Self::write_node("/sys/module/mtk_fpsgo/parameters/fstb_soft_level", "0");
        Self::write_node("/sys/module/mtk_fpsgo/parameters/bhr_opp", "15");

        // 2. محرك الرسوميات GED
        Self::write_node("/sys/module/ged/parameters/boost_gpu_enable", "1");
        Self::write_node("/sys/module/ged/parameters/gx_game_mode", "1");
        Self::write_node("/sys/module/ged/parameters/gx_boost_on", "1");
        Self::write_node("/sys/module/ged/parameters/boost_extra_sub", "1");

        // 3. ضبط uclamp لمعمارية All-Big-Core
        Self::write_node("/proc/sys/kernel/sched_util_clamp_min_rt_default", "500");
        Self::write_node("/dev/cpuset/top-app/uclamp.min", "40");
        Self::write_node("/dev/cpuset/foreground/uclamp.min", "20");
        Self::write_node("/dev/cpuset/background/uclamp.max", "50");

        // 4. توقيت استجابة sugov_ext
        if let Ok(policies) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*/sugov_ext") {
            for policy in policies.flatten() {
                let p = policy.to_string_lossy();
                Self::write_node(&format!("{}/up_rate_limit_us", p), "500");
                Self::write_node(&format!("{}/down_rate_limit_us", p), "10000");
            }
        }
    }

    pub fn apply_balanced_profile() {
        Self::apply_cpu_governor("sugov_ext");

        Self::write_node("/sys/module/mtk_fpsgo/parameters/fbt_enable", "1");
        Self::write_node("/sys/module/mtk_fpsgo/parameters/fstb_soft_level", "1");
        Self::write_node("/sys/module/ged/parameters/gx_game_mode", "0");
        Self::write_node("/sys/module/ged/parameters/boost_gpu_enable", "0");

        Self::write_node("/dev/cpuset/top-app/uclamp.min", "10");
        Self::write_node("/dev/cpuset/background/uclamp.max", "30");

        if let Ok(policies) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*/sugov_ext") {
            for policy in policies.flatten() {
                let p = policy.to_string_lossy();
                Self::write_node(&format!("{}/up_rate_limit_us", p), "2000");
                Self::write_node(&format!("{}/down_rate_limit_us", p), "5000");
            }
        }
    }

    pub fn apply_powersave_profile() {
        Self::apply_cpu_governor("schedutil");

        Self::write_node("/sys/module/mtk_fpsgo/parameters/fbt_enable", "0");
        Self::write_node("/sys/module/ged/parameters/boost_gpu_enable", "0");
        Self::write_node("/sys/module/ged/parameters/gx_game_mode", "0");

        Self::write_node("/dev/cpuset/top-app/uclamp.max", "60");
        Self::write_node("/dev/cpuset/foreground/uclamp.max", "40");
        Self::write_node("/dev/cpuset/background/uclamp.max", "20");
    }
}