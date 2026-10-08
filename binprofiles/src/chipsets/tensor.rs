use crate::utils::*;

// Tensor: Mali GPU exposed with scaling_*_freq, and the MIF devfreq.
// Profiles move the floor or the ceiling only; nothing is pinned outside max mode.

fn gpu() -> Option<String> {
    let mut paths = glob::glob("/sys/devices/platform/**/*.mali").ok()?;
    paths.next()?.ok().map(|p| p.to_string_lossy().into_owned())
}

fn set_gpu(path: &str, min: Option<u64>, max: Option<u64>) {
    if let (Some(min), Some(max)) = (min, max) {
        write_range(&format!("{}/scaling_min_freq", path), &format!("{}/scaling_max_freq", path), min, max);
    }
}

fn mif(apply: fn(&str)) {
    if let Ok(paths) = glob::glob("/sys/class/devfreq/*devfreq_mif*") {
        for path in paths.flatten() {
            apply(&path.to_string_lossy());
        }
    }
}

pub fn tensor_balance() {
    if let Some(path) = gpu() {
        let avail = format!("{}/available_frequencies", path);
        set_gpu(&path, which_minfreq(&avail), which_maxfreq(&avail));
    }
    mif(devfreq_unlock);
}

pub fn tensor_performance() {
    let max = get_perfmax();
    let lite = get_litemode();
    if let Some(path) = gpu() {
        let avail = format!("{}/available_frequencies", path);
        let floor = if max {
            which_maxfreq(&avail)
        } else if lite {
            which_minfreq(&avail)
        } else {
            which_midfreq(&avail)
        };
        set_gpu(&path, floor, which_maxfreq(&avail));
    }
    mif(if max { devfreq_max_perf } else if lite { devfreq_unlock } else { devfreq_mid_perf });
}

pub fn tensor_powersave() {
    if let Some(path) = gpu() {
        let avail = format!("{}/available_frequencies", path);
        set_gpu(&path, which_minfreq(&avail), which_midfreq(&avail));
    }
    mif(devfreq_cap_mid);
}
