use std::fs;
use std::path::Path;

pub struct SnapdragonTuner;

impl SnapdragonTuner {
    fn write_node(path: &str, value: &str) {
        if Path::new(path).exists() {
            let _ = fs::write(path, value.trim());
        }
    }

    pub fn apply_cpu_governor(governor: &str) {
        if let Ok(entries) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*") {
            for entry in entries.flatten() {
                let p = entry.to_string_lossy();
                let avail_path = format!("{}/scaling_available_governors", p);
                let target_path = format!("{}/scaling_governor", p);

                if let Ok(avail) = fs::read_to_string(&avail_path) {
                    if avail.contains(governor) {
                        Self::write_node(&target_path, governor);
                    } else if avail.contains("schedutil") {
                        Self::write_node(&target_path, "schedutil");
                    }
                }
            }
        }
    }

    fn tune_adreno_gpu(performance: bool) {
        let kgsl_path = "/sys/class/kgsl/kgsl-3d0";
        if !Path::new(kgsl_path).exists() {
            return;
        }

        if performance {
            Self::write_node(&format!("{}/devfreq/governor", kgsl_path), "msm-adreno-tz");
            Self::write_node(&format!("{}/throttling", kgsl_path), "0");
            Self::write_node(&format!("{}/force_bus_on", kgsl_path), "1");
            Self::write_node(&format!("{}/force_clk_on", kgsl_path), "1");
            Self::write_node(&format!("{}/idle_timer", kgsl_path), "64");
        } else {
            Self::write_node(&format!("{}/throttling", kgsl_path), "1");
            Self::write_node(&format!("{}/force_bus_on", kgsl_path), "0");
            Self::write_node(&format!("{}/force_clk_on", kgsl_path), "0");
            Self::write_node(&format!("{}/idle_timer", kgsl_path), "24");
        }
    }

    pub fn apply_performance_profile() {
        Self::apply_cpu_governor("sugov_ext");
        Self::tune_adreno_gpu(true);

        if let Ok(entries) = glob::glob("/sys/devices/system/cpu/qcom_core_ctl/cpu*") {
            for entry in entries.flatten() {
                let p = entry.to_string_lossy();
                Self::write_node(&format!("{}/enable", p), "0");
            }
        }

        Self::write_node("/dev/cpuset/top-app/uclamp.min", "60");
        Self::write_node("/dev/cpuset/top-app/uclamp.latency_sensitive", "1");
        Self::write_node("/dev/cpuset/foreground/uclamp.min", "20");
        Self::write_node("/dev/cpuset/background/uclamp.max", "30");

        if let Ok(policies) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*/sugov_ext") {
            for policy in policies.flatten() {
                let p = policy.to_string_lossy();
                Self::write_node(&format!("{}/up_rate_limit_us", p), "500");
                Self::write_node(&format!("{}/down_rate_limit_us", p), "15000");
            }
        }
    }

    pub fn apply_balanced_profile() {
        Self::apply_cpu_governor("sugov_ext");
        Self::tune_adreno_gpu(false);

        if let Ok(entries) = glob::glob("/sys/devices/system/cpu/qcom_core_ctl/cpu*") {
            for entry in entries.flatten() {
                let p = entry.to_string_lossy();
                Self::write_node(&format!("{}/enable", p), "1");
            }
        }

        Self::write_node("/dev/cpuset/top-app/uclamp.min", "10");
        Self::write_node("/dev/cpuset/top-app/uclamp.latency_sensitive", "0");
        Self::write_node("/dev/cpuset/background/uclamp.max", "20");

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
        Self::tune_adreno_gpu(false);

        Self::write_node("/dev/cpuset/top-app/uclamp.max", "50");
        Self::write_node("/dev/cpuset/foreground/uclamp.max", "35");
        Self::write_node("/dev/cpuset/background/uclamp.max", "15");
    }
}