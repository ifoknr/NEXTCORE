use crate::utils::*;
use std::fs;
use std::path::Path;
use std::process::Command;
use crate::chipsets::mediatek::*;
use crate::chipsets::snapdragon::*;
use crate::chipsets::exynos::*;
use crate::chipsets::unisoc::*;
use crate::chipsets::tensor::*;

/// The device's everyday CPU governor: the user's pick, else the one saved
/// at boot, else the best scaling governor the kernel offers. Never
/// performance, powersave or userspace: those pin a fixed clock, which is
/// what made Balanced run hot.
fn default_cpu_gov() -> String {
    for key in ["persist.sys.nextcore.custom_default_cpu_gov", "persist.sys.nextcore.default_cpu_gov"] {
        let gov = getprop(key);
        if !gov.is_empty() && !is_fixed_gov(&gov) {
            return gov;
        }
    }
    scaling_gov_fallback()
}

/// Performance profile.
///
/// Sustained mode (default): keeps the device governor and raises the clock
/// floor instead of pinning every core at max, boosts the app on screen with
/// uclamp, caps background work, and leaves thermal and battery protection
/// on. Frame rates hold longer because the chip does not hit its thermal
/// limit in the first minutes.
///
/// Max mode (`persist.sys.nextcoreconf.perfmax=1`): the previous behaviour,
/// CPU and GPU pinned at their top clocks with MediaTek limits relaxed.
pub fn performance_profile() {

    // Check if tweaks are disabled
    if is_tweak_disabled() {
        return;
    }

    let perfmax = get_perfmax();
    let lite_mode = get_litemode();

    // Sustained mode needs a real governor to scale above the floor, so a
    // fixed-clock governor (performance pins every core at max) is only
    // honoured in max mode, where min=max anyway.
    let mut performance_gov = getprop("persist.sys.nextcore.custom_performance_cpu_gov");
    if performance_gov.is_empty() || (!perfmax && is_fixed_gov(&performance_gov)) {
        performance_gov = default_cpu_gov();
    }

    // I/O Scheduler Tweaks
    let mut custom_perf_io = getprop("persist.sys.nextcore.custom_performance_IO");
    if custom_perf_io.is_empty() {
        let mut default_io = getprop("persist.sys.nextcore.custom_default_balanced_IO");
        if default_io.is_empty() {
            default_io = getprop("persist.sys.nextcore.default_balanced_IO");
        }
        if default_io.is_empty() {
            default_io = "none".to_string();
        }
        custom_perf_io = default_io;
    }

    // Mali GPU Governor Tweaks
    let mut custom_perf_mali = getprop("persist.sys.nextcore.custom_performance_maligpu_gov");
    if custom_perf_mali.is_empty() {
        let mut default_mali = getprop("persist.sys.nextcore.custom_default_maligpu_gov");
        if default_mali.is_empty() {
            default_mali = getprop("persist.sys.nextcore.default_maligpu_gov");
        }
        custom_perf_mali = default_mali;
    }

    apply_custom_governor_io(&performance_gov, &custom_perf_io, &custom_perf_mali);

    if perfmax {
        if Path::new("/proc/ppm").exists() {
            setgamefreqppm();
        } else {
            setgamefreq();
        }
        log_info("Max mode: CPU pinned at top frequencies");
    } else {
        setperffreq();
        log_info("Sustained mode: CPU floor raised, governor keeps scaling");
    }
    if lite_mode {
        log_info("Lite mode: CPU ceiling at about 80%");
    }

    // App on screen gets a 30% utilization floor, foreground apps 10%,
    // background work is capped at half a core's capacity.
    set_uclamp("30", "10", "50");

    // Page cache is kept: dropping it on every switch made games reload
    // their assets from storage right when they started.
    write_lock("80", "/proc/sys/vm/vfs_cache_pressure");
    write_lock("N", "/sys/module/workqueue/parameters/power_efficient");
    // EAS off spreads tasks over the big cores; only worth the heat in max mode.
    write_lock(if perfmax { "0" } else { "1" }, "/sys/devices/system/cpu/eas/enable");

    if let Ok(paths) = glob::glob("/dev/stune/*") {
        for path in paths.flatten() {
            if path.is_dir() {
                let p_str = path.to_str().unwrap();
                write_lock("30", &format!("{}/schedtune.boost", p_str));
                write_lock("1", &format!("{}/schedtune.sched_boost_enabled", p_str));
                write_lock("0", &format!("{}/schedtune.prefer_idle", p_str));
                write_lock("0", &format!("{}/schedtune.colocate", p_str));
            }
        }
    }

    let bs_path = "/sys/module/battery_saver/parameters/enabled";
    if Path::new(bs_path).exists() {
        let content = fs::read_to_string(bs_path).unwrap_or_default();
        if content.chars().any(|c: char| c.is_ascii_digit()) {
            write_lock("0", bs_path);
        } else {
            write_lock("N", bs_path);
        }
    }

    write_lock("0", "/proc/sys/kernel/split_lock_mitigate");

    let sched_feat = "/sys/kernel/debug/sched_features";
    if Path::new(sched_feat).exists() {
        write_lock("NEXT_BUDDY", sched_feat);
        write_lock("NO_TTWU_QUEUE", sched_feat);
    }

    // I/O Tweaks: bigger read-ahead and queue for faster game asset loading
    // (sd* also covers UFS storage).
    std::thread::spawn(|| {
        if let Ok(paths) = glob::glob("/sys/block/*") {
            for path in paths.flatten() {
                if let Some(file_name) = path.file_name().and_then(|n| n.to_str()) {
                    if file_name == "mmcblk0" || file_name == "mmcblk1" || file_name.starts_with("sd") {
                        if let Some(p_str) = path.to_str() {
                            write_lock("256", &format!("{}/queue/read_ahead_kb", p_str));
                            write_lock("128", &format!("{}/queue/nr_requests", p_str));
                        }
                    }
                }
            }
        }
    });

    if get_clearapps() {
        clear_background_apps();
    }

    if !lite_mode {
        match getprop("persist.sys.nextcore.soctype").as_str() {
            "1" => mediatek_performance(perfmax),
            "2" => snapdragon_performance(),
            "3" => exynos_performance(),
            "4" => unisoc_performance(),
            "5" => tensor_performance(),
            _ => log_info("Unknown SoC: chipset-specific tweaks skipped, general tweaks applied"),
        }
    }

    log_verbose(if perfmax { "Performance Profile (max) Applied Successfully!" } else { "Performance Profile (sustained) Applied Successfully!" });
}

pub fn balanced_profile() {

    // Check if tweaks are disabled
    if is_tweak_disabled() {
        return;
    }
    
    let default_gov = default_cpu_gov();

    // I/O Scheduler Tweaks
    let mut default_io = getprop("persist.sys.nextcore.custom_default_balanced_IO");
    if default_io.is_empty() {
        default_io = getprop("persist.sys.nextcore.default_balanced_IO");
    }
    if default_io.is_empty() {
        default_io = "none".to_string();
    }

    // Mali GPU Governor Tweaks
    let mut default_mali = getprop("persist.sys.nextcore.custom_default_maligpu_gov");
    if default_mali.is_empty() {
        default_mali = getprop("persist.sys.nextcore.default_maligpu_gov");
    }

    apply_custom_governor_io(&default_gov, &default_io, &default_mali);

    if Path::new("/proc/ppm").exists() {
        setfreqppm();
    } else {
        setfreq();
    }

    if getprop("persist.sys.nextcoreconf.freqoffset") == "Disabled" {
        log_info("Set CPU freq to normal Frequencies");
    } else {
        log_info("Set CPU freq to normal selected Frequencies");
    }

    write_lock("120", "/proc/sys/vm/vfs_cache_pressure");
    write_lock("Y", "/sys/module/workqueue/parameters/power_efficient");
    write_lock("1", "/sys/devices/system/cpu/eas/enable");
    restore_uclamp();

    if let Ok(paths) = glob::glob("/dev/stune/*") {
        for path in paths.flatten() {
            if path.is_dir() {
                let p_str = path.to_str().unwrap();
                write_lock("0", &format!("{}/schedtune.boost", p_str));
                write_lock("0", &format!("{}/schedtune.sched_boost_enabled", p_str));
                write_lock("0", &format!("{}/schedtune.prefer_idle", p_str));
                write_lock("0", &format!("{}/schedtune.colocate", p_str));
            }
        }
    }

    let bs_path = "/sys/module/battery_saver/parameters/enabled";
    if Path::new(bs_path).exists() {
        let content = fs::read_to_string(bs_path).unwrap_or_default();
        if content.chars().any(|c: char| c.is_ascii_digit()) {
            write_lock("0", bs_path);
        } else {
            write_lock("N", bs_path);
        }
    }

    write_lock("1", "/proc/sys/kernel/split_lock_mitigate");

    let sched_feat = "/sys/kernel/debug/sched_features";
    if Path::new(sched_feat).exists() {
        write_lock("NEXT_BUDDY", sched_feat);
        write_lock("TTWU_QUEUE", sched_feat);
    }
    
    // I/O Tweaks
    std::thread::spawn(|| {
        if let Ok(paths) = glob::glob("/sys/block/*") {
            for path in paths.flatten() {
                if let Some(file_name) = path.file_name().and_then(|n| n.to_str()) {
                    if file_name == "mmcblk0" || file_name == "mmcblk1" || file_name.starts_with("sd") {
                        if let Some(p_str) = path.to_str() {
                            write_lock("128", &format!("{}/queue/read_ahead_kb", p_str));
                            write_lock("64", &format!("{}/queue/nr_requests", p_str));
                        }
                    }
                }
            }
        }
    });

    match getprop("persist.sys.nextcore.soctype").as_str() {
        "1" => mediatek_balance(),
        "2" => snapdragon_balance(),
        "3" => exynos_balance(),
        "4" => unisoc_balance(),
        "5" => tensor_balance(),
        _ => log_info("Unknown SoC: chipset-specific tweaks skipped, general tweaks applied"),
    }

    log_verbose("Balanced Profile applied successfully!");
}

pub fn eco_mode() {

    // Check if tweaks are disabled
    if is_tweak_disabled() {
        return;
    }
    
    // Eco keeps a scaling governor and caps the top clock instead: the
    // powersave governor pins the lowest clock and makes the UI stutter.
    let mut powersave_gov = getprop("persist.sys.nextcore.custom_powersave_cpu_gov");
    if powersave_gov.is_empty() || is_fixed_gov(&powersave_gov) {
        powersave_gov = default_cpu_gov();
    }

    // I/O Scheduler Tweaks
    let mut powersave_io = getprop("persist.sys.nextcore.custom_powersave_IO");
    if powersave_io.is_empty() {
        powersave_io = "none".to_string();
    }

    // Mali GPU Governor Tweaks
    let mut custom_eco_mali = getprop("persist.sys.nextcore.custom_powersave_maligpu_gov");
    if custom_eco_mali.is_empty() {
        let mut default_mali = getprop("persist.sys.nextcore.custom_default_maligpu_gov");
        if default_mali.is_empty() {
            default_mali = getprop("persist.sys.nextcore.default_maligpu_gov");
        }
        custom_eco_mali = default_mali;
    }

    apply_custom_governor_io(&powersave_gov, &powersave_io, &custom_eco_mali);

    if Path::new("/proc/ppm").exists() {
        setfreqppm();
    } else {
        setfreq();
    }
    log_info(&format!("Set CPU max freq to {}% for eco", ECO_MAX_PERCENT));

    write_lock("120", "/proc/sys/vm/vfs_cache_pressure");
    write_lock("Y", "/sys/module/workqueue/parameters/power_efficient");
    write_lock("1", "/sys/devices/system/cpu/eas/enable");
    // Device defaults, then background work capped harder to save power.
    restore_uclamp();
    write_unlock("30", "/dev/cpuctl/background/cpu.uclamp.max");
    write_unlock("30", "/dev/cpuctl/system-background/cpu.uclamp.max");

    if let Ok(paths) = glob::glob("/dev/stune/*") {
        for path in paths.flatten() {
            if path.is_dir() {
                let p_str = path.to_str().unwrap();
                write_lock("0", &format!("{}/schedtune.boost", p_str));
                write_lock("0", &format!("{}/schedtune.sched_boost_enabled", p_str));
                write_lock("0", &format!("{}/schedtune.prefer_idle", p_str));
                write_lock("0", &format!("{}/schedtune.colocate", p_str));
            }
        }
    }

    let bs_path = "/sys/module/battery_saver/parameters/enabled";
    if Path::new(bs_path).exists() {
        let content = fs::read_to_string(bs_path).unwrap_or_default();
        if content.chars().any(|c| c.is_ascii_digit()) {
            write_lock("1", bs_path);
        } else {
            write_lock("Y", bs_path);
        }
    }

    write_lock("1", "/proc/sys/kernel/split_lock_mitigate");

    let sched_feat = "/sys/kernel/debug/sched_features";
    if Path::new(sched_feat).exists() {
        write_lock("NO_NEXT_BUDDY", sched_feat);
        write_lock("NO_TTWU_QUEUE", sched_feat);
    }

    match getprop("persist.sys.nextcore.soctype").as_str() {
        "1" => mediatek_powersave(),
        "2" => snapdragon_powersave(),
        "3" => exynos_powersave(),
        "4" => unisoc_powersave(),
        "5" => tensor_powersave(),
        _ => log_info("Unknown SoC: chipset-specific tweaks skipped, general tweaks applied"),
    }

    log_verbose("ECO Mode applied successfully!");
}

pub fn initialize() {
    // Kernel panic settings are left to the ROM: forcing them to 0 made a
    // crashed kernel keep running instead of rebooting.
    let _ = Command::new("sync").status();
    
    // Display / SurfaceFlinger config
    let scheme = getprop("persist.sys.nextcoreconf.schemeconfig");
    if scheme != "1000 1000 1000 1000" && !scheme.is_empty() {
        let parts: Vec<&str> = scheme.split_whitespace().collect();
        if parts.len() >= 4 {
            let r = parts[0].parse::<f32>().unwrap_or(1000.0) / 1000.0;
            let g = parts[1].parse::<f32>().unwrap_or(1000.0) / 1000.0;
            let b = parts[2].parse::<f32>().unwrap_or(1000.0) / 1000.0;
            let s = parts[3].parse::<f32>().unwrap_or(1000.0) / 1000.0;

            let _ = Command::new("service").args([
                "call", "SurfaceFlinger", "1015", "i32", "1",
                "f", &r.to_string(), "f", "0", "f", "0", "f", "0",
                "f", "0", "f", &g.to_string(), "f", "0", "f", "0",
                "f", "0", "f", "0", "f", &b.to_string(), "f", "0",
                "f", "0", "f", "0", "f", "0", "f", "1"
            ]).status();

            let _ = Command::new("service").args([
                "call", "SurfaceFlinger", "1022", "f", &s.to_string()
            ]).status();
        }
    }
    
    // Check if tweaks are disabled
    if is_tweak_disabled() {
        return;
    }

    // Save the vendor uclamp values before any profile changes them
    backup_uclamp();
    backup_nodes(&crate::chipsets::mediatek::DVFSRC_GOVERNORS);

    // Initialize CPU & I/O & Mali GPU
    init_cpu_governor();
    init_io_scheduler();
    init_maligpu_governor();
    init_renderer();
    
    // Thermal zone governors are left to the vendor: many devices rely on
    // power_allocator or their own governor, and step_wise there breaks throttling.

    // I/O Tweaks
    if let Ok(paths) = glob::glob("/sys/block/*") {
        for path in paths.flatten() {
            if let Some(p_str) = path.to_str() {
                write_lock("0", &format!("{}/queue/iostats", p_str));
                write_lock("0", &format!("{}/queue/add_random", p_str));
            }
        }
    }

    // Networking tweaks
    let tcp_avail = fs::read_to_string("/proc/sys/net/ipv4/tcp_available_congestion_control").unwrap_or_default();
    let algos = ["bbr3", "bbr2", "bbrplus", "bbr", "westwood", "cubic"];
    for algo in algos.iter() {
        if tcp_avail.contains(algo) {
            write_lock(algo, "/proc/sys/net/ipv4/tcp_congestion_control");
            break;
        }
    }

    write_lock("1", "/proc/sys/net/ipv4/tcp_low_latency");
    write_lock("1", "/proc/sys/net/ipv4/tcp_ecn");
    write_lock("3", "/proc/sys/net/ipv4/tcp_fastopen");
    write_lock("1", "/proc/sys/net/ipv4/tcp_sack");
    write_lock("0", "/proc/sys/net/ipv4/tcp_timestamps");

    // General Kernel & Scheduler Tweaks
    write_lock("3", "/proc/sys/kernel/perf_cpu_time_max_percent");
    write_lock("0", "/proc/sys/kernel/sched_schedstats");
    write_lock("0", "/proc/sys/kernel/task_cpustats_enable");
    write_lock("0", "/proc/sys/kernel/sched_autogroup_enabled");
    write_lock("1", "/proc/sys/kernel/sched_child_runs_first");
    write_lock("32", "/proc/sys/kernel/sched_nr_migrate");
    write_lock("50000", "/proc/sys/kernel/sched_migration_cost_ns");
    write_lock("1000000", "/proc/sys/kernel/sched_min_granularity_ns");
    write_lock("1500000", "/proc/sys/kernel/sched_wakeup_granularity_ns");

    // VM Tweaks
    write_lock("0", "/proc/sys/vm/page-cluster");
    write_lock("15", "/proc/sys/vm/stat_interval");
    write_lock("0", "/proc/sys/vm/compaction_proactiveness");

    // Vendor Bloats & Module Tweaks
    write_lock("0", "/sys/module/mmc_core/parameters/use_spi_crc");
    write_lock("0", "/sys/module/opchain/parameters/chain_on");
    write_lock("0", "/sys/module/cpufreq_bouncing/parameters/enable");
    write_lock("0", "/proc/task_info/task_sched_info/task_sched_info_enable");
    write_lock("0", "/proc/oplus_scheduler/sched_assist/sched_assist_enabled");

    // Libraries Max Perf Reporting
    let libs = "libunity.so, libil2cpp.so, libmain.so, libUE4.so, libgodot_android.so, libgdx.so, libgdx-box2d.so, libminecraftpe.so, libLive2DCubismCore.so, libyuzu-android.so, libryujinx.so, libcitra-android.so, libhdr_pro_engine.so, libandroidx.graphics.path.so, libeffect.so";
    write_lock(libs, "/proc/sys/kernel/sched_lib_name");
    write_lock("255", "/proc/sys/kernel/sched_lib_mask_force");

    systemv("sys.nextcore-utilityconf FSTrim");
    let _ = Command::new("sys.nextcore-preferencedtweaks").status();
    
    // Final Sync & Logs
    let _ = Command::new("sync").status();
    log_verbose("Initializing Complete");
    log_info("Initializing Complete");
}
