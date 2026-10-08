use crate::utils::*;
use std::path::Path;

// Exynos: Mali GPU through /sys/kernel/gpu and the MIF (memory) devfreq.
// Profiles move the floor or the ceiling only; nothing is pinned outside max mode.

const GPU: &str = "/sys/kernel/gpu";

fn set_gpu(min: Option<u64>, max: Option<u64>) {
    if let (Some(min), Some(max)) = (min, max) {
        write_range(&format!("{}/gpu_min_clock", GPU), &format!("{}/gpu_max_clock", GPU), min, max);
    }
}

fn mif(apply: fn(&str)) {
    if let Ok(paths) = glob::glob("/sys/class/devfreq/*devfreq_mif*") {
        for path in paths.flatten() {
            apply(&path.to_string_lossy());
        }
    }
}

fn mali_policy(policy: &str) {
    if let Ok(mut paths) = glob::glob("/sys/devices/platform/**/*.mali") {
        if let Some(Ok(path)) = paths.next() {
            write_unlock(policy, &format!("{}/power_policy", path.display()));
        }
    }
}

pub fn exynos_balance() {
    if Path::new(GPU).exists() {
        let avail = format!("{}/gpu_available_frequencies", GPU);
        set_gpu(which_minfreq(&avail), which_maxfreq(&avail));
    }
    mali_policy("coarse_demand");
    mif(devfreq_unlock);
}

pub fn exynos_performance() {
    let max = get_perfmax();
    let lite = get_litemode();
    if Path::new(GPU).exists() {
        let avail = format!("{}/gpu_available_frequencies", GPU);
        let floor = if max {
            which_maxfreq(&avail)
        } else if lite {
            which_minfreq(&avail)
        } else {
            which_midfreq(&avail)
        };
        set_gpu(floor, which_maxfreq(&avail));
    }
    mali_policy(if max { "always_on" } else { "coarse_demand" });
    mif(if max { devfreq_max_perf } else if lite { devfreq_unlock } else { devfreq_mid_perf });
}

pub fn exynos_powersave() {
    if Path::new(GPU).exists() {
        let avail = format!("{}/gpu_available_frequencies", GPU);
        set_gpu(which_minfreq(&avail), which_midfreq(&avail));
    }
    mali_policy("coarse_demand");
    mif(devfreq_cap_mid);
}
