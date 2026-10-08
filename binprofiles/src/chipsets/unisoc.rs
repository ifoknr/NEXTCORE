use crate::utils::*;

// Unisoc: GPU devfreq only. Floor or ceiling moves; pinned only in max mode.

fn gpu(apply: fn(&str)) {
    if let Ok(mut paths) = glob::glob("/sys/class/devfreq/**/*.gpu") {
        if let Some(Ok(path)) = paths.next() {
            apply(&path.to_string_lossy());
        }
    }
}

pub fn unisoc_balance() {
    gpu(devfreq_unlock);
}

pub fn unisoc_performance() {
    let max = get_perfmax();
    let lite = get_litemode();
    gpu(if max { devfreq_max_perf } else if lite { devfreq_unlock } else { devfreq_mid_perf });
}

pub fn unisoc_powersave() {
    gpu(devfreq_cap_mid);
}
