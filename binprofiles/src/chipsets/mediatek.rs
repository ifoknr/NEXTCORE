use crate::utils::*; use std::fs; use std::path::Path;

/// DVFSRC (memory/vcore) governor nodes. The vendor value is saved at boot
/// (see initialize) and restored outside max mode.
pub const DVFSRC_GOVERNORS: [&str; 2] = [
    "/sys/class/devfreq/mtk-dvfsrc-devfreq/governor",
    "/sys/devices/platform/soc/*.dvfsrc/mtk-dvfsrc-devfreq/devfreq/mtk-dvfsrc-devfreq/governor",
];

/// `Some(gov)` sets the DVFSRC governor, `None` restores the vendor one.
fn dvfsrc_governor(gov: Option<&str>) {
    for pattern in DVFSRC_GOVERNORS {
        if let Ok(paths) = glob::glob(pattern) {
            for path in paths.flatten() {
                let p = path.to_string_lossy();
                match gov {
                    Some(g) => write_unlock(g, &p),
                    None => restore_node(&p),
                }
            }
        }
    }
}

fn dvfsrc_ddr_opp(opp: &str) {
    write_unlock(opp, "/sys/kernel/helio-dvfsrc/dvfsrc_force_vcore_dvfs_opp");
    if let Ok(paths) = glob::glob("/sys/devices/platform/*.dvfsrc") {
        for path in paths.flatten() {
            write_unlock(opp, &format!("{}/helio-dvfsrc/dvfsrc_req_ddr_opp", path.display()));
        }
    }
}

pub fn mediatek_balance() {
    if Path::new("/proc/ppm/policy_status").exists() {
        let content = fs::read_to_string("/proc/ppm/policy_status").unwrap_or_default();
        for line in content.lines() {
            let is_target_1 = line.contains("FORCE_LIMIT") || line.contains("PWR_THRO") || line.contains("THERMAL") || line.contains("USER_LIMIT");
            let is_target_2 = line.contains("SYS_BOOST");
            if is_target_1 || is_target_2 {
                if let Some(idx_str) = line.split('[').nth(1).and_then(|s| s.split(']').next()) {
                    if is_target_1 {
                        write_lock(&format!("{} 1", idx_str), "/proc/ppm/policy_status");
                    }
                    if is_target_2 {
                        write_lock(&format!("{} 0", idx_str), "/proc/ppm/policy_status");
                    }
                }
            }
        }
    }
    
    // ppm_fix_freq("-1"); 
    
    write_lock("2", "/sys/kernel/fpsgo/common/force_onoff");
    write_lock("1", "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled");
    
    write_lock("0", "/sys/devices/platform/boot_dramboost/dramboost/dramboost");

    write_lock("0", "/proc/cpufreq/cpufreq_cci_mode");
    write_lock("1", "/proc/cpufreq/cpufreq_power_mode");

    if Path::new("/proc/gpufreq").exists() {
        write_lock("0", "/proc/gpufreq/gpufreq_opp_freq");
    } else if Path::new("/proc/gpufreqv2").exists() {
        write_lock("-1", "/proc/gpufreqv2/fix_target_opp_index");
    }

    write_lock("1", "/sys/devices/system/cpu/eas/enable");

    if Path::new("/proc/gpufreq/gpufreq_power_limited").exists() {
        let settings = [
            "ignore_batt_oc", "ignore_batt_percent", "ignore_low_batt",
            "ignore_thermal_protect", "ignore_pbm_limited"
        ];
        for setting in settings {
            write_lock(&format!("{} 0", setting), "/proc/gpufreq/gpufreq_power_limited");
        }
    }
    

    write_lock("0", "/proc/perfmgr/syslimiter/syslimiter_force_disable");
    write_lock("stop 0", "/proc/mtk_batoc_throttling/battery_oc_protect_stop");
    write_lock("1", "/sys/kernel/eara_thermal/enable");

    dvfsrc_ddr_opp("-1");
    dvfsrc_governor(None);

    if let Ok(mut paths) = glob::glob("/sys/devices/platform/*.mali") {
        if let Some(Ok(path)) = paths.next() {
            write_lock("coarse_demand", &format!("{}/power_policy", path.display()));
        }
    }
}

/// MediaTek part of the performance profile. `max` is the opt-in max mode:
/// GPU pinned at its top OPP and the PPM, thermal and battery limits relaxed.
/// Sustained mode keeps those protections and lets FPSGO/GED pace the GPU.
pub fn mediatek_performance(max: bool) {
    if Path::new("/proc/ppm/policy_status").exists() {
        let content = fs::read_to_string("/proc/ppm/policy_status").unwrap_or_default();
        for line in content.lines() {
            let is_target_1 = line.contains("FORCE_LIMIT") || line.contains("PWR_THRO") || line.contains("THERMAL") || line.contains("USER_LIMIT");
            let is_target_2 = line.contains("SYS_BOOST");
            if is_target_1 || is_target_2 {
                if let Some(idx_str) = line.split('[').nth(1).and_then(|s| s.split(']').next()) {
                    if is_target_1 {
                        let state = if max { "0" } else { "1" };
                        write_lock(&format!("{} {}", idx_str, state), "/proc/ppm/policy_status");
                    }
                    if is_target_2 {
                        write_lock(&format!("{} 1", idx_str), "/proc/ppm/policy_status");
                    }
                }
            }
        }
    }

    
    // ppm_fix_freq("0"); 

    let use_fpsgo = getprop("persist.sys.nextcoreconf.usefpsgo");
    if use_fpsgo == "0" {
        write_lock("0", "/sys/kernel/fpsgo/common/force_onoff");
    }
    
    // FPSGO reads frame timing from GED KPI, so sustained mode keeps it on.
    write_lock(if max { "0" } else { "1" }, "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled");
    
    write_lock("1", "/sys/devices/platform/boot_dramboost/dramboost/dramboost");

    write_lock("1", "/proc/cpufreq/cpufreq_cci_mode");
    write_lock("3", "/proc/cpufreq/cpufreq_power_mode");

    if max {
        if Path::new("/proc/gpufreq").exists() {
            if let Some(freq) = get_mtk_gpu_max_freq() {
                write_lock(&freq.to_string(), "/proc/gpufreq/gpufreq_opp_freq");
            }
        } else if Path::new("/proc/gpufreqv2").exists() {
            write_lock("0", "/proc/gpufreqv2/fix_target_opp_index");
        }
    } else if Path::new("/proc/gpufreq").exists() {
        write_lock("0", "/proc/gpufreq/gpufreq_opp_freq");
    } else if Path::new("/proc/gpufreqv2").exists() {
        write_lock("-1", "/proc/gpufreqv2/fix_target_opp_index");
    }

    write_lock(if max { "0" } else { "1" }, "/sys/devices/system/cpu/eas/enable");

    if Path::new("/proc/gpufreq/gpufreq_power_limited").exists() {
        let settings = [
            "ignore_batt_oc", "ignore_batt_percent", "ignore_low_batt",
            "ignore_thermal_protect", "ignore_pbm_limited"
        ];
        let state = if max { "1" } else { "0" };
        for setting in settings {
            write_lock(&format!("{} {}", setting, state), "/proc/gpufreq/gpufreq_power_limited");
        }
    }

    write_lock("0", "/proc/perfmgr/syslimiter/syslimiter_force_disable");
    write_lock(if max { "stop 1" } else { "stop 0" }, "/proc/mtk_batoc_throttling/battery_oc_protect_stop");

    // EARA keeps the frame rate steady as the chip warms up; only max mode turns it off.
    write_lock(if max { "0" } else { "1" }, "/sys/kernel/eara_thermal/enable");

    // Memory clock: pinned at its top OPP only in max mode; sustained mode
    // leaves it to the vendor governor, which already ramps for games.
    if max {
        dvfsrc_ddr_opp("0");
        dvfsrc_governor(Some("performance"));
    } else {
        dvfsrc_ddr_opp("-1");
        dvfsrc_governor(None);
    }

    if let Ok(mut paths) = glob::glob("/sys/devices/platform/*.mali") {
        if let Some(Ok(path)) = paths.next() {
            // always_on keeps every shader core powered: only worth it in max mode.
            write_unlock(if max { "always_on" } else { "coarse_demand" }, &format!("{}/power_policy", path.display()));
        }
    }
}

pub fn mediatek_powersave() {
    if Path::new("/proc/ppm/policy_status").exists() {
        let content = fs::read_to_string("/proc/ppm/policy_status").unwrap_or_default();
        for line in content.lines() {
            let is_target_1 = line.contains("FORCE_LIMIT") || line.contains("PWR_THRO") || line.contains("THERMAL") || line.contains("USER_LIMIT");
            let is_target_2 = line.contains("SYS_BOOST");
            if is_target_1 || is_target_2 {
                if let Some(idx_str) = line.split('[').nth(1).and_then(|s| s.split(']').next()) {
                    if is_target_1 {
                        write_lock(&format!("{} 1", idx_str), "/proc/ppm/policy_status");
                    }
                    if is_target_2 {
                        write_lock(&format!("{} 0", idx_str), "/proc/ppm/policy_status");
                    }
                }
            }
        }
    }
    
    // ppm_fix_freq("-1"); 
    write_lock("0", "/sys/devices/platform/boot_dramboost/dramboost/dramboost");
    write_lock("2", "/sys/kernel/fpsgo/common/force_onoff");
    write_lock("1", "/sys/module/sspm_v3/holders/ged/parameters/is_GED_KPI_enabled");
    
    write_lock("1", "/sys/kernel/eara_thermal/enable");

    dvfsrc_ddr_opp("-1");
    dvfsrc_governor(None);

    if Path::new("/proc/gpufreq/gpufreq_power_limited").exists() {
        let settings = [
            "ignore_batt_oc", "ignore_batt_percent", "ignore_low_batt",
            "ignore_thermal_protect", "ignore_pbm_limited"
        ];
        for setting in settings {
            write_lock(&format!("{} 0", setting), "/proc/gpufreq/gpufreq_power_limited");
        }
    }

    write_lock("0", "/proc/perfmgr/syslimiter/syslimiter_force_disable");
    write_lock("stop 0", "/proc/mtk_batoc_throttling/battery_oc_protect_stop");

    if let Ok(mut paths) = glob::glob("/sys/devices/platform/*.mali") {
        if let Some(Ok(path)) = paths.next() {
            write_lock("coarse_demand", &format!("{}/power_policy", path.display()));
        }
    }
}
