use crate::utils::*;
use std::path::Path;

// Snapdragon: Adreno GPU (kgsl) and the bus_dcvs memory buses. The vendor
// devfreq governors are left alone in every profile; profiles only move the
// floor or the ceiling, so the governor and thermal engine keep control.

const GPU: &str = "/sys/class/kgsl/kgsl-3d0/devfreq";
const BUSES: [&str; 4] = ["LLCC", "L3", "DDR", "DDRQOS"];

/// Sets min/max on every bus_dcvs voter of the listed buses.
fn set_buses(pick: impl Fn(&[u64]) -> (u64, u64)) {
    for bus in BUSES {
        let base = format!("/sys/devices/system/cpu/bus_dcvs/{}", bus);
        if !Path::new(&base).exists() {
            continue;
        }
        let freqs = read_freqs(&format!("{}/available_frequencies", base));
        if freqs.is_empty() {
            continue;
        }
        let (min, max) = pick(&freqs);
        if let Ok(voters) = glob::glob(&format!("{}/*/max_freq", base)) {
            for max_path in voters.flatten() {
                let max_path = max_path.to_string_lossy().into_owned();
                let min_path = max_path.replace("/max_freq", "/min_freq");
                write_range(&min_path, &max_path, min, max);
            }
        }
    }
}

fn set_gpu(pick: impl Fn(&[u64]) -> (u64, u64)) {
    if !Path::new(GPU).exists() {
        return;
    }
    let freqs = read_freqs(&format!("{}/available_frequencies", GPU));
    if freqs.is_empty() {
        return;
    }
    let (min, max) = pick(&freqs);
    write_range(&format!("{}/min_freq", GPU), &format!("{}/max_freq", GPU), min, max);
}

fn lowest(f: &[u64]) -> u64 { f[0] }
fn middle(f: &[u64]) -> u64 { f[f.len() / 2] }
fn highest(f: &[u64]) -> u64 { f[f.len() - 1] }

/// Full range: the vendor governors decide.
pub fn snapdragon_balance() {
    set_gpu(|f| (lowest(f), highest(f)));
    set_buses(|f| (lowest(f), highest(f)));
    write_unlock("0", "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost");
}

/// GPU and memory floor at the middle step (lowest in lite mode), ceiling
/// open. Max mode pins both at the top, as before.
pub fn snapdragon_performance() {
    let max = get_perfmax();
    let lite = get_litemode();
    if max {
        set_gpu(|f| (highest(f), highest(f)));
        set_buses(|f| (highest(f), highest(f)));
        write_unlock("3", "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost");
        return;
    }
    set_gpu(|f| (if lite { lowest(f) } else { middle(f) }, highest(f)));
    set_buses(|f| (if lite { lowest(f) } else { middle(f) }, highest(f)));
    // Mild boost: the GPU ramps up sooner without being held at max.
    write_unlock(if lite { "0" } else { "1" }, "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost");
}

/// GPU and memory capped at the middle step, free to idle down.
pub fn snapdragon_powersave() {
    set_gpu(|f| (lowest(f), middle(f)));
    set_buses(|f| (lowest(f), middle(f)));
    write_unlock("0", "/sys/class/kgsl/kgsl-3d0/devfreq/adrenoboost");
}
