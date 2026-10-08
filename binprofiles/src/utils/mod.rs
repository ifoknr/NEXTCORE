use std::os::unix::fs::PermissionsExt;
use std::fs; 
use std::path::Path; 
use std::process::Command;
use glob::glob;
use std::collections::HashSet;

pub const CONFIG_PATH: &str = "/data/adb/.config/NextCore";
pub const MY_PATH: &str = "/system/bin:/system/xbin:/data/adb/ap/bin:/data/adb/ksu/bin:/data/adb/magisk:/debug_ramdisk:/sbin:/sbin/su:/su/bin:/su/xbin:/data/data/com.termux/files/usr/bin";

pub fn getprop(key: &str) -> String {
    if let Ok(output) = Command::new("getprop").arg(key).output() {
        String::from_utf8_lossy(&output.stdout).trim().to_string()
    } else {
        String::new()
    }
}

pub fn resetprop(key: &str, val: &str) {
    let status = if val.is_empty() {
        Command::new("resetprop").arg("--delete").arg(key).status()
    } else {
        Command::new("resetprop").arg(key).arg(val).status()
    };

    if let Err(e) = status {
        log_info(&format!("Failed to resetprop '{}': {}", key, e));
    }
}

pub fn setprop(key: &str, val: &str) {
    if let Err(e) = Command::new("setprop").arg(key).arg(val).status() {
        log_info(&format!("Failed to setprop '{}' to '{}': {}", key, val, e));
    }
}

pub fn log_verbose(message: &str) {
    if get_debugmode() {
        let _ = Command::new("sys.nextcore-service")
            .args(["--verboselog", "AZLog", "0", message])
            .status();
    }
}

pub fn log_info(message: &str) {
    let _ = Command::new("sys.nextcore-service")
        .args(["--log", "NextCore_Profiler", "1", message])
        .status();
}

pub fn chmod(path: &str, mode: u32) {
    if let Ok(metadata) = fs::metadata(path) {
        let mut perms = metadata.permissions();
        perms.set_mode(mode);
        let _ = fs::set_permissions(path, perms);
    }
}

/// Writes a tweak value. Nodes are left writable (0644) even when `lock` is
/// set: locking them read-only kept the vendor thermal and power services from
/// stepping clocks down on a hot device, which is not safe on a public build.
pub fn write_unlock_core(value: &str, path_str: &str, lock: bool) {
    let path = Path::new(path_str);
    let parent_name = path.parent().and_then(|p| p.file_name()).unwrap_or_default().to_string_lossy();
    let file_name = path.file_name().unwrap_or_default().to_string_lossy();
    let pathname = if parent_name.is_empty() { file_name.into_owned() } else { format!("{}/{}", parent_name, file_name) };

    if !path.exists() { return; }

    chmod(path_str, 0o644);

    let val_with_newline = format!("{}\n", value);
    
    if fs::write(path, val_with_newline).is_err() {
        log_verbose(&format!("Cannot write to /{} (permission denied)", pathname));
        if lock { chmod(path_str, 0o644); }
        return;
    }

    log_verbose(&format!("Set /{} to {}", pathname, value));
    if lock { chmod(path_str, 0o644); }
}

pub fn write_unlock(value: &str, path_str: &str) {
    write_unlock_core(value, path_str, false);
}

pub fn write_lock(value: &str, path_str: &str) {
    write_unlock_core(value, path_str, true);
}

pub fn systemv(command: &str) -> i32 {
    match Command::new("/system/bin/sh")
        .arg("-c")
        .arg(command)
        .env("PATH", MY_PATH)
        .status()
    {
        Ok(status) => status.code().unwrap_or(-1),
        Err(e) => {
            log_info(&format!("systemv failed for '{}': {}", command, e));
            -1
        }
    }
}

pub fn get_limiter() -> u64 {
    let val = getprop("persist.sys.nextcoreconf.freqoffset");
    if val == "Disabled" || val.is_empty() {
        100
    } else {
        val.replace('%', "").parse().unwrap_or(100)
    }
}

pub fn get_debugmode() -> bool {
    getprop("persist.sys.nextcore.debugmode") == "true"
}

pub fn get_clearapps() -> bool {
    getprop("persist.sys.nextcoreconf.clearbg") == "1"
}

pub fn get_litemode() -> bool {
    getprop("persist.sys.nextcoreconf.litemode") == "1"
}

pub fn get_curprofile() -> String {
    fs::read_to_string(format!("{}/API/current_profile", CONFIG_PATH))
        .unwrap_or_default()
        .trim()
        .to_string()
}

pub fn setprop_cmd(key: &str, value: &str) {
    let _ = Command::new("setprop").arg(key).arg(value).status();
}

pub fn applyfreqbalance() {
    if Path::new("/proc/ppm").exists() {
        dsetfreqppm();
    } else {
        dsetfreq();
    }
}

pub fn applyfreqgame() {
    if !get_perfmax() {
        setperffreq();
        return;
    }
    if Path::new("/proc/ppm").exists() {
        dsetgamefreqppm();
    } else {
        dsetgamefreq();
    }
}

pub fn applyppmnfreqsets(value: &str, path: &str) {
    if Path::new(path).exists() {
        chmod(path, 0o644);
        // FIX: Tambahkan \n
        let val_with_newline = format!("{}\n", value);
        let _ = fs::write(path, val_with_newline);
        chmod(path, 0o644);
    }
}

pub fn which_maxfreq(path: &str) -> Option<u64> {
    read_freqs(path).into_iter().max()
}

pub fn which_minfreq(path: &str) -> Option<u64> {
    read_freqs(path).into_iter().min()
}

pub fn which_midfreq(path: &str) -> Option<u64> {
    let freqs = read_freqs(path);
    if freqs.is_empty() {
        return None;
    }
    // Ekuivalen dengan logika awk mengambil nilai tengah
    Some(freqs[freqs.len() / 2])
}

pub fn setfreqs(file: &str, target: u64) -> u64 {
    let freqs = read_freqs(file);
    if freqs.is_empty() {
        return target;
    }
    // Mencari frekuensi yang selisihnya paling sedikit dengan target (closest match)
    *freqs
        .iter()
        .min_by_key(|&&f| (f as i64 - target as i64).abs())
        .unwrap_or(&target)
}

pub fn setgov(gov: &str) {
    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/cpu*/cpufreq/scaling_governor") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();
            chmod(p_str, 0o644);
            let _ = fs::write(p_str, gov);
            chmod(p_str, 0o644);
        }
    }

    // Lock additional policy paths
    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*/scaling_governor") {
        for path in paths.flatten() {
            chmod(path.to_str().unwrap(), 0o644);
        }
    }
}

pub fn sets_io(scheduler: &str) {
    for block in &["sda", "sdb", "sdc", "mmcblk0", "mmcblk1"] {
        let path = format!("/sys/block/{}/queue/scheduler", block);
        if Path::new(&path).exists() {
            chmod(&path, 0o644);
            let _ = fs::write(&path, scheduler);
            chmod(&path, 0o644);
        }
    }
}

pub fn setfreqppm() {
    if !Path::new("/proc/ppm").exists() {
        return;
    }

    let limiter = get_limiter();
    let curprofile = get_curprofile();
    let mut cluster = 0;

    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();
            let policy_name = path.file_name().unwrap_or_default().to_string_lossy();

            let cpu_maxfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_max_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);
            let cpu_minfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_min_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);

            let new_max_target = cpu_maxfreq * limiter / 100;
            let avail_file = format!("{}/scaling_available_frequencies", p_str);
            let new_maxfreq = setfreqs(&avail_file, new_max_target);

            if curprofile == "3" {
                let target_min_target = cpu_maxfreq * 40 / 100;
                let new_minfreq = setfreqs(&avail_file, target_min_target);

                write_lock(&format!("{} {}", cluster, new_maxfreq), "/proc/ppm/policy/hard_userlimit_max_cpu_freq");
                write_lock(&format!("{} {}", cluster, new_minfreq), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, new_maxfreq, new_minfreq));
            } else {

                write_unlock(&format!("{} {}", cluster, new_maxfreq), "/proc/ppm/policy/hard_userlimit_max_cpu_freq");
                write_unlock(&format!("{} {}", cluster, cpu_minfreq), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, new_maxfreq, cpu_minfreq));
            }
            cluster += 1;
        }
    }
}

pub fn setfreq() {
    let limiter = get_limiter();
    let curprofile = get_curprofile();

    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/*/cpufreq") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();

            let policy_name = path.parent()
                .and_then(|p: &std::path::Path| p.file_name())
                .unwrap_or_default()
                .to_string_lossy();

            let cpu_maxfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_max_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);
            let cpu_minfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_min_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);

            let new_max_target = cpu_maxfreq * limiter / 100;
            let avail_file = format!("{}/scaling_available_frequencies", p_str);
            let new_maxfreq = setfreqs(&avail_file, new_max_target);

            if curprofile == "3" {
                let target_min_target = cpu_maxfreq * 40 / 100;
                let new_minfreq = setfreqs(&avail_file, target_min_target);

                write_lock(&new_maxfreq.to_string(), &format!("{}/scaling_max_freq", p_str));
                write_lock(&new_minfreq.to_string(), &format!("{}/scaling_min_freq", p_str));

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, new_maxfreq, new_minfreq));
            } else {

                write_unlock(&new_maxfreq.to_string(), &format!("{}/scaling_max_freq", p_str));
                write_unlock(&cpu_minfreq.to_string(), &format!("{}/scaling_min_freq", p_str));

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, new_maxfreq, cpu_minfreq));

                if let Ok(sc_paths) = glob("/sys/devices/system/cpu/cpufreq/policy*/scaling_*_freq") {
                    for sp in sc_paths.flatten() {
                        chmod(sp.to_str().unwrap(), 0o644);
                    }
                }
            }
        }
    }
}

pub fn setgamefreqppm() {
    if !Path::new("/proc/ppm").exists() {
        return;
    }

    let litemode = get_litemode();
    let mut cluster = 0;

    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();
            let policy_name = path.file_name().unwrap_or_default().to_string_lossy();

            let cpu_maxfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_max_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);

            let new_midtarget = cpu_maxfreq * 80 / 100;
            let avail_file = format!("{}/scaling_available_frequencies", p_str);
            let new_midfreq = setfreqs(&avail_file, new_midtarget);

            if litemode {
                let cpu_minfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_min_freq", p_str))
                    .unwrap_or_default().trim().parse().unwrap_or(0);

                write_lock(&format!("{} {}", cluster, new_midfreq), "/proc/ppm/policy/hard_userlimit_max_cpu_freq");
                write_lock(&format!("{} {}", cluster, cpu_minfreq), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, new_midfreq, cpu_minfreq));
            } else {
                write_lock(&format!("{} {}", cluster, cpu_maxfreq), "/proc/ppm/policy/hard_userlimit_max_cpu_freq");             
                write_lock(&format!("{} {}", cluster, cpu_maxfreq), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, cpu_maxfreq, new_midfreq));
            }
            cluster += 1;
        }
    }
}


pub fn setgamefreq() {
    let litemode = get_litemode();

    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/*/cpufreq") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();
            let policy_name = path.parent()
                .and_then(|p: &std::path::Path| p.file_name())
                .unwrap_or_default()
                .to_string_lossy();

            let cpu_maxfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_max_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);

            let new_midtarget = cpu_maxfreq * 80 / 100;
            let avail_file = format!("{}/scaling_available_frequencies", p_str);
            let new_midfreq = setfreqs(&avail_file, new_midtarget);

            if litemode {
                let cpu_minfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_min_freq", p_str))
                    .unwrap_or_default().trim().parse().unwrap_or(0);

                // Bugfix: Menulis ke jalur sysfs standar, bukan ke /proc/ppm
                write_unlock(&new_midfreq.to_string(), &format!("{}/scaling_max_freq", p_str));
                write_unlock(&cpu_minfreq.to_string(), &format!("{}/scaling_min_freq", p_str));

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, new_midfreq, cpu_minfreq));
            } else {
                write_unlock(&cpu_maxfreq.to_string(), &format!("{}/scaling_max_freq", p_str));
                write_unlock(&cpu_maxfreq.to_string(), &format!("{}/scaling_min_freq", p_str));

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, cpu_maxfreq, new_midfreq));

            }
        }
    }
}

pub fn dsetfreqppm() {
    if !Path::new("/proc/ppm").exists() {
        return;
    }

    let limiter = get_limiter();
    let curprofile = get_curprofile();
    let mut cluster = 0;

    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();
            let cpu_maxfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_max_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);
            let cpu_minfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_min_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);

            let new_max_target = cpu_maxfreq * limiter / 100;
            let avail_file = format!("{}/scaling_available_frequencies", p_str);
            let new_maxfreq = setfreqs(&avail_file, new_max_target);

            if curprofile == "3" {
                let target_min_target = cpu_maxfreq * 40 / 100;
                let new_minfreq = setfreqs(&avail_file, target_min_target);

                applyppmnfreqsets(&format!("{} {}", cluster, new_maxfreq), "/proc/ppm/policy/hard_userlimit_max_cpu_freq");
                applyppmnfreqsets(&format!("{} {}", cluster, new_minfreq), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");
            } else {
                applyppmnfreqsets(&format!("{} {}", cluster, new_maxfreq), "/proc/ppm/policy/hard_userlimit_max_cpu_freq");
                applyppmnfreqsets(&format!("{} {}", cluster, cpu_minfreq), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");
            }
            cluster += 1;
        }
    }
}

pub fn dsetfreq() {
    let limiter = get_limiter();
    let curprofile = get_curprofile();

    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/*/cpufreq") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();
            let cpu_maxfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_max_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);
            let cpu_minfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_min_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);

            let new_max_target = cpu_maxfreq * limiter / 100;
            let avail_file = format!("{}/scaling_available_frequencies", p_str);
            let new_maxfreq = setfreqs(&avail_file, new_max_target);

            if curprofile == "3" {
                let target_min_target = cpu_maxfreq * 40 / 100;
                let new_minfreq = setfreqs(&avail_file, target_min_target);

                applyppmnfreqsets(&new_maxfreq.to_string(), &format!("{}/scaling_max_freq", p_str));
                applyppmnfreqsets(&new_minfreq.to_string(), &format!("{}/scaling_min_freq", p_str));
            } else {
                applyppmnfreqsets(&new_maxfreq.to_string(), &format!("{}/scaling_max_freq", p_str));
                applyppmnfreqsets(&cpu_minfreq.to_string(), &format!("{}/scaling_min_freq", p_str));

                if let Ok(sc_paths) = glob("/sys/devices/system/cpu/cpufreq/policy*/scaling_*_freq") {
                    for sp in sc_paths.flatten() {
                        chmod(sp.to_str().unwrap(), 0o644);
                    }
                }
            }
        }
    }
}

pub fn dsetgamefreqppm() {
    if !Path::new("/proc/ppm").exists() {
        return;
    }

    let litemode = get_litemode();
    let mut cluster = 0;

    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/cpufreq/policy*") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();
            let policy_name = path.file_name().unwrap_or_default().to_string_lossy();

            let cpu_maxfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_max_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);

            let new_midtarget = cpu_maxfreq;
            let avail_file = format!("{}/scaling_available_frequencies", p_str);
            let new_midfreq = setfreqs(&avail_file, new_midtarget);

            if litemode {
                let cpu_minfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_min_freq", p_str))
                    .unwrap_or_default().trim().parse().unwrap_or(0);

                write_unlock(&format!("{} {}", cluster, new_midfreq), "/proc/ppm/policy/hard_userlimit_max_cpu_freq");
                write_unlock(&format!("{} {}", cluster, cpu_minfreq), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, new_midfreq, cpu_minfreq));
            } else {
                applyppmnfreqsets(&format!("{} {}", cluster, new_midfreq), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");
            }
            cluster += 1;
        }
    }
}

pub fn dsetgamefreq() {
    let litemode = get_litemode();

    if let Ok(paths) = glob::glob("/sys/devices/system/cpu/*/cpufreq") {
        for path in paths.flatten() {
            let p_str = path.to_str().unwrap();
            let policy_name = path.parent()
                .and_then(|p: &std::path::Path| p.file_name())
                .unwrap_or_default()
                .to_string_lossy();

            let cpu_maxfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_max_freq", p_str))
                .unwrap_or_default().trim().parse().unwrap_or(0);

            let new_midtarget = cpu_maxfreq;
            let avail_file = format!("{}/scaling_available_frequencies", p_str);
            let new_midfreq = setfreqs(&avail_file, new_midtarget);

            if litemode {
                let cpu_minfreq: u64 = fs::read_to_string(format!("{}/cpuinfo_min_freq", p_str))
                    .unwrap_or_default().trim().parse().unwrap_or(0);

                write_unlock(&new_midfreq.to_string(), &format!("{}/scaling_max_freq", p_str));
                write_unlock(&cpu_minfreq.to_string(), &format!("{}/scaling_min_freq", p_str));

                log_info(&format!("Set {} maxfreq={} minfreq={}", policy_name, new_midfreq, cpu_minfreq));
            } else {
                applyppmnfreqsets(&cpu_maxfreq.to_string(), &format!("{}/scaling_max_freq", p_str));
                applyppmnfreqsets(&new_midfreq.to_string(), &format!("{}/scaling_min_freq", p_str));

                if let Ok(sc_paths) = glob("/sys/devices/system/cpu/cpufreq/policy*/scaling_*_freq") {
                    for sp in sc_paths.flatten() {
                        chmod(sp.to_str().unwrap(), 0o644);
                    }
                }
            }
        }
    }
}

pub fn devfreq_max_perf(path: &str) {
    let avail = format!("{}/available_frequencies", path);
    if let Some(max) = which_maxfreq(&avail) {
        write_range(&format!("{}/min_freq", path), &format!("{}/max_freq", path), max, max);
    }
}

pub fn devfreq_mid_perf(path: &str) {
    let avail = format!("{}/available_frequencies", path);
    if let (Some(max), Some(mid)) = (which_maxfreq(&avail), which_midfreq(&avail)) {
        write_range(&format!("{}/min_freq", path), &format!("{}/max_freq", path), mid, max);
    }
}

pub fn devfreq_unlock(path: &str) {
    let avail = format!("{}/available_frequencies", path);
    if let (Some(max), Some(min)) = (which_maxfreq(&avail), which_minfreq(&avail)) {
        write_range(&format!("{}/min_freq", path), &format!("{}/max_freq", path), min, max);
    }
}

/// Writes a min/max pair in an order the kernel accepts: raising goes max
/// first, lowering goes min first, so min never ends up above max midway
/// (older kernels reject that write).
pub fn write_range(min_path: &str, max_path: &str, min: u64, max: u64) {
    let cur_max = read_u64(max_path);
    if cur_max != 0 && min > cur_max {
        write_unlock(&max.to_string(), max_path);
        write_unlock(&min.to_string(), min_path);
    } else {
        write_unlock(&min.to_string(), min_path);
        write_unlock(&max.to_string(), max_path);
    }
}

/// Eco: caps a devfreq device at its middle frequency and lets it idle down.
pub fn devfreq_cap_mid(path: &str) {
    let avail = format!("{}/available_frequencies", path);
    if let (Some(mid), Some(min)) = (which_midfreq(&avail), which_minfreq(&avail)) {
        write_range(&format!("{}/min_freq", path), &format!("{}/max_freq", path), min, mid);
    }
}

pub fn clear_background_apps() {
    // Menjalankan dumpsys window displays
    let output = Command::new("dumpsys").args(["window", "displays"]).output();
    let stdout = match output {
        Ok(o) => String::from_utf8_lossy(&o.stdout).into_owned(),
        Err(_) => return,
    };

    let mut visible_pkgs = HashSet::new();
    let mut invisible_pkgs = HashSet::new();

    // Membaca output per baris (setara grep "Task{")
    for line in stdout.lines() {
        if line.contains("Task{") {
            // Replikasi logika sed: 's/.*A=[0-9]*:\([^ ]*\).*/\1/p'
            if let Some(a_idx) = line.find("A=") {
                let part = &line[a_idx..];
                if let Some(colon_idx) = part.find(':') {
                    if let Some(space_idx) = part.find(' ') {
                        if colon_idx < space_idx {
                            let pkg = &part[colon_idx + 1..space_idx];

                            if line.contains("visible=true") {
                                visible_pkgs.insert(pkg.to_string());
                            } else if line.contains("visible=false") {
                                invisible_pkgs.insert(pkg.to_string());
                            }
                        }
                    }
                }
            }
        }
    }

    // Only apps the user installed are candidates; system apps are never stopped.
    let user_apps: HashSet<String> = Command::new("pm")
        .args(["list", "packages", "-3"])
        .output()
        .map(|o| {
            String::from_utf8_lossy(&o.stdout)
                .lines()
                .filter_map(|l| l.strip_prefix("package:").map(|p| p.trim().to_string()))
                .collect()
        })
        .unwrap_or_default();

    // Apps that must keep running in the background: the keyboard and launcher
    // in use, and anything that delivers messages, calls, music or alarms.
    let mut keep: HashSet<String> = HashSet::new();
    for key in ["default_input_method", "sms_default_application", "dialer_default_application"] {
        let v = getsetting_secure(key);
        if let Some(pkg) = v.split('/').next().filter(|p| !p.is_empty()) {
            keep.insert(pkg.to_string());
        }
    }
    if let Ok(o) = Command::new("cmd").args(["package", "resolve-activity", "--brief", "-c", "android.intent.category.HOME", "-a", "android.intent.action.MAIN"]).output() {
        if let Some(line) = String::from_utf8_lossy(&o.stdout).lines().last() {
            if let Some(pkg) = line.split('/').next() {
                keep.insert(pkg.trim().to_string());
            }
        }
    }
    const KEEP_WORDS: [&str; 22] = [
        "whatsapp", "telegram", "messenger", "signal", "discord", "viber", "wechat", "naver.line",
        "snapchat", "instagram", "skype", "teams", "slack", "zoom",
        "music", "spotify", "deezer", "anghami", "podcast",
        "alarm", "clock", "keyboard",
    ];

    for pkg in invisible_pkgs {
        if visible_pkgs.contains(&pkg) || !user_apps.contains(&pkg) || keep.contains(&pkg) {
            continue;
        }
        let lower = pkg.to_lowercase();
        if KEEP_WORDS.iter().any(|w| lower.contains(w)) {
            continue;
        }
        let _ = Command::new("am").args(["force-stop", &pkg]).status();
        log_verbose(&format!("Stopped app: {}", pkg));
    }
}

pub fn get_mtk_gpu_max_freq() -> Option<u64> {
    let content = fs::read_to_string("/proc/gpufreq/gpufreq_opp_dump").unwrap_or_default();
    content.lines()
        .filter(|line: &&str| line.contains("freq = "))
        .filter_map(|line: &str| line.split("freq = ").nth(1)?.split_whitespace().next()?.parse::<u64>().ok())
        .max()
}

pub fn read_freqs(path: &str) -> Vec<u64> {
    let mut freqs: Vec<u64> = fs::read_to_string(path)
        .unwrap_or_default()
        .split_whitespace()
        .filter_map(|s: &str| s.parse().ok())
        .collect();
    freqs.sort_unstable();
    freqs
}

pub fn init_cpu_governor() {
    let cpu_path = "/sys/devices/system/cpu/cpu0/cpufreq";
    let gov_file = format!("{}/scaling_governor", cpu_path);
    chmod(&gov_file, 0o644);

    let mut default_gov = fs::read_to_string(&gov_file)
        .unwrap_or_default()
        .trim()
        .to_string();
        
    let performance_gov = "performance";
    let powersave_gov = "powersave";

    setprop_cmd("persist.sys.nextcore.default_cpu_gov", &default_gov);
    log_info(&format!("Default CPU governor detected: {}", default_gov));

    // Handle fallback if default is 'performance'
    if default_gov == "performance" && getprop("persist.sys.nextcore.custom_default_cpu_gov").is_empty() {
        log_info("Default governor is 'performance'");
        let avail_govs = fs::read_to_string(format!("{}/scaling_available_governors", cpu_path)).unwrap_or_default();
        let fallbacks = [
            "scx", "schedhorizon", "walt", "sched_pixel", "sugov_ext", "uag",
            "schedplus", "energy_step", "ondemand", "schedutil", "interactive",
            "conservative", "powersave"
        ];

        for gov in &fallbacks {
            if avail_govs.contains(gov) {
                setprop_cmd("persist.sys.nextcore.default_cpu_gov", gov);
                default_gov = gov.to_string();
                log_info(&format!("Fallback governor to: {}", gov));
                break;
            }
        }
    }

    // Apply custom governor if set
    let custom_gov = getprop("persist.sys.nextcore.custom_default_cpu_gov");
    if !custom_gov.is_empty() {
        default_gov = custom_gov;
    }
    
    log_info(&format!("Using CPU governor: {}", default_gov));
    setgov(&default_gov);

    // Set fallback props
    if getprop("persist.sys.nextcore.custom_powersave_cpu_gov").is_empty() {
        setprop_cmd("persist.sys.nextcore.custom_powersave_cpu_gov", &powersave_gov);
    }
    if getprop("persist.sys.nextcore.custom_performance_cpu_gov").is_empty() {
        setprop_cmd("persist.sys.nextcore.custom_performance_cpu_gov", &performance_gov);
    }
    
    log_info("Parsing CPU Governor complete");
}

pub fn init_io_scheduler() {
    let mut io_path = String::new();
    for dev in &["mmcblk0", "mmcblk1", "sda", "sdb", "sdc"] {
        let p = format!("/sys/block/{}/queue", dev);
        if Path::new(&format!("{}/scheduler", p)).exists() {
            io_path = p;
            log_info(&format!("Detected valid block device: {}", dev));
            break;
        }
    }

    if io_path.is_empty() {
        log_info("No valid block device with scheduler found");
        std::process::exit(1);
    }

    let sched_file = format!("{}/scheduler", io_path);
    chmod(&sched_file, 0o644);

    // Parse active IO scheduler (the one inside brackets [])
    let mut default_io = String::new();
    let sched_content = fs::read_to_string(&sched_file).unwrap_or_default();
    if let Some(start) = sched_content.find('[') {
        if let Some(end) = sched_content[start..].find(']') {
            default_io = sched_content[start + 1..start + end].to_string();
        }
    }

    setprop_cmd("persist.sys.nextcore.default_balanced_IO", &default_io);
    log_info(&format!("Default IO Scheduler detected: {}", default_io));

    // Apply custom IO if set
    let custom_io = getprop("persist.sys.nextcore.custom_default_balanced_IO");
    if !custom_io.is_empty() {
        default_io = custom_io;
    }
    
    log_info(&format!("Using IO Scheduler: {}", default_io));
    sets_io(&default_io);

    // Set fallback props
    if getprop("persist.sys.nextcore.custom_powersave_IO").is_empty() {
        setprop_cmd("persist.sys.nextcore.custom_powersave_IO", &default_io);
    }
    if getprop("persist.sys.nextcore.custom_performance_IO").is_empty() {
        setprop_cmd("persist.sys.nextcore.custom_performance_IO", &default_io);
    }
    
    log_info("Parsing IO Scheduler complete");
}

pub fn init_maligpu_governor() {
    let mut gpu_path = String::new();
    
    if let Ok(paths) = glob::glob("/sys/class/devfreq/*.mali") {
        for path in paths.flatten() {
            if let Some(p_str) = path.to_str() {
                gpu_path = p_str.to_string();
                log_info(&format!("Detected Mali GPU path: {}", gpu_path));
                break;
            }
        }
    }

    if gpu_path.is_empty() {
        return;
    }

    let gov_file = format!("{}/governor", gpu_path);
    chmod(&gov_file, 0o644);

    let mut default_maligpu_gov = fs::read_to_string(&gov_file)
        .unwrap_or_default()
        .trim()
        .to_string();

    setprop_cmd("persist.sys.nextcore.default_maligpu_gov", &default_maligpu_gov);
    log_info(&format!("Default Mali GPU governor detected: {}", default_maligpu_gov));

    let custom_maligpu_gov = getprop("persist.sys.nextcore.custom_default_maligpu_gov");
    if !custom_maligpu_gov.is_empty() {
        default_maligpu_gov = custom_maligpu_gov;
    }

    log_info(&format!("Using Mali GPU governor: {}", default_maligpu_gov));
    write_lock(&default_maligpu_gov, &gov_file);

    if getprop("persist.sys.nextcore.custom_powersave_maligpu_gov").is_empty() {
        setprop_cmd("persist.sys.nextcore.custom_powersave_maligpu_gov", &default_maligpu_gov);
    }
    if getprop("persist.sys.nextcore.custom_performance_maligpu_gov").is_empty() {
        setprop_cmd("persist.sys.nextcore.custom_performance_maligpu_gov", &default_maligpu_gov);
    }

    log_info("Parsing Mali GPU Governor complete");
}

pub fn sets_mali_gov(gov: &str) {
    if let Ok(paths) = glob("/sys/class/devfreq/*.mali/governor") {
        for path in paths.flatten() {
            if let Some(p_str) = path.to_str() {
                chmod(p_str, 0o644);
                let _ = fs::write(p_str, gov);
                chmod(p_str, 0o644); 
            }
        }
    }
}

pub fn is_tweak_disabled() -> bool {
    let disable_tweak = getprop("persist.sys.nextcore.disabletweak");
    disable_tweak == "1"
}

pub fn apply_custom_governor_io(perf_gov: &str, perf_io: &str, mali_gov: &str) {
    setgov(perf_gov);
    log_info(&format!("Applying governor to : {}", perf_gov));
    
    if !perf_io.is_empty() {
        sets_io(perf_io);
        log_info(&format!("Applying I/O scheduler to : {}", perf_io));
    }
    
    if !mali_gov.is_empty() {
        sets_mali_gov(mali_gov);
    }
}

pub fn setrender(renderer: &str) {
    if renderer == "default" || renderer.is_empty() {
        setprop("debug.hwui.renderer", "");
        setprop("debug.renderengine.backend", "");
        setprop("debug.hwui.render_thread", "");
        setprop("debug.skia.threaded_mode", "");
        resetprop("ro.hwui.use_vulkan", ""); 
        
        log_info("Resetting all renderers to system default");
        return;
    }

    setprop("debug.hwui.renderer", renderer);

    if renderer.contains("threaded") {
        setprop("debug.hwui.render_thread", "true");
        if renderer.contains("skia") {
            setprop("debug.skia.threaded_mode", "true");
        } else {
            setprop("debug.skia.threaded_mode", "false");
        }
    } else {
        setprop("debug.hwui.render_thread", "false");
        setprop("debug.skia.threaded_mode", "false");
    }

    match renderer {
        "skiavk" | "skiavkthreaded" | "vulkan" => {
            setprop("debug.renderengine.backend", "vulkan");
            resetprop("ro.hwui.use_vulkan", "true"); 
        }
        "skiagl" | "skiaglthreaded" | "gles" | "opengl" | "openglthreaded" => {
            setprop("debug.renderengine.backend", "gles");
            resetprop("ro.hwui.use_vulkan", "false");
        }
        "software" => {
            setprop("debug.renderengine.backend", "");
            resetprop("ro.hwui.use_vulkan", "false");
        }
        _ => {
            if renderer.contains("vk") || renderer.contains("vulkan") {
                setprop("debug.renderengine.backend", "vulkan");
                resetprop("ro.hwui.use_vulkan", "true");
            } else if renderer.contains("gl") || renderer.contains("gles") {
                setprop("debug.renderengine.backend", "gles");
                resetprop("ro.hwui.use_vulkan", "false");
            } else {
                setprop("debug.renderengine.backend", "");
            }
        }
    }
    log_info(&format!("Successfully applied renderer: {}", renderer));
}

pub fn init_renderer() {
    let renderer = getprop("persist.sys.nextcoreconf.renderer");
    
    if renderer.is_empty() || renderer.eq_ignore_ascii_case("default") {
        log_info("Renderer setting is default, skipping renderer setup");
        return;
    }
    
    log_info(&format!("Applying renderer: {}", renderer));
    setrender(&renderer);
}

/// Opt-in "max" mode: pins CPU and GPU at their top clocks and relaxes
/// MediaTek thermal and battery limits, the way the performance profile
/// always worked before. Off by default; the default is sustained mode.
pub fn get_perfmax() -> bool {
    getprop("persist.sys.nextcoreconf.perfmax") == "1"
}

fn read_u64(path: &str) -> u64 {
    fs::read_to_string(path).unwrap_or_default().trim().parse().unwrap_or(0)
}

/// Sustained performance clocks. Max stays at the top frequency (about 80%
/// in lite mode) and min is raised to a floor: 70% of max on the bigger
/// clusters and 50% on the smallest one. The governor keeps scaling above
/// the floor, so idle cores cool down and thermal management still works,
/// which holds frame rates longer than pinning every core at max.
pub fn setperffreq() {
    let litemode = get_litemode();
    let use_ppm = Path::new("/proc/ppm").exists();

    let policies: Vec<std::path::PathBuf> = match glob("/sys/devices/system/cpu/cpufreq/policy*") {
        Ok(paths) => paths.flatten().collect(),
        Err(_) => return,
    };
    let maxes: Vec<u64> = policies
        .iter()
        .map(|p| read_u64(&format!("{}/cpuinfo_max_freq", p.display())))
        .collect();
    let smallest = maxes.iter().copied().filter(|&m| m > 0).min().unwrap_or(0);

    for (cluster, path) in policies.iter().enumerate() {
        let cpu_max = maxes[cluster];
        if cpu_max == 0 {
            continue;
        }
        let p_str = path.to_string_lossy();
        let policy_name = path.file_name().unwrap_or_default().to_string_lossy();
        let avail = format!("{}/scaling_available_frequencies", p_str);

        let max_target = if litemode { cpu_max * 80 / 100 } else { cpu_max };
        let new_max = setfreqs(&avail, max_target);

        let floor = if maxes.len() > 1 && cpu_max == smallest { 50 } else { 70 };
        let floor = if litemode { floor - 20 } else { floor };
        let new_min = setfreqs(&avail, cpu_max * floor / 100).min(new_max);

        if use_ppm {
            write_lock(&format!("{} {}", cluster, new_max), "/proc/ppm/policy/hard_userlimit_max_cpu_freq");
            write_lock(&format!("{} {}", cluster, new_min), "/proc/ppm/policy/hard_userlimit_min_cpu_freq");
        } else {
            // Max first, so the new floor never lands above the old ceiling.
            write_unlock(&new_max.to_string(), &format!("{}/scaling_max_freq", p_str));
            write_unlock(&new_min.to_string(), &format!("{}/scaling_min_freq", p_str));
        }
        log_info(&format!("Set {} maxfreq={} minfreq={} (floor {}%)", policy_name, new_max, new_min, floor));
    }
}

/// cpuctl utilization-clamp nodes the profiles change (GKI kernels).
const UCLAMP_NODES: [&str; 4] = [
    "/dev/cpuctl/top-app/cpu.uclamp.min",
    "/dev/cpuctl/foreground/cpu.uclamp.min",
    "/dev/cpuctl/background/cpu.uclamp.max",
    "/dev/cpuctl/system-background/cpu.uclamp.max",
];

fn uclamp_backup_path() -> String {
    format!("{}/API/uclamp_default", CONFIG_PATH)
}

/// Saves the device's own uclamp values once, so balanced mode can put back
/// whatever the vendor set instead of a guess.
pub fn backup_uclamp() {
    let backup = uclamp_backup_path();
    if Path::new(&backup).exists() {
        return;
    }
    let lines: Vec<String> = UCLAMP_NODES
        .iter()
        .filter(|node| Path::new(node).exists())
        .filter_map(|node| {
            let value = fs::read_to_string(node).ok()?.trim().to_string();
            (!value.is_empty()).then(|| format!("{}={}", node, value))
        })
        .collect();
    if !lines.is_empty() {
        let _ = fs::write(&backup, lines.join("\n") + "\n");
    }
}

/// Sets utilization clamps: `top_min` and `fg_min` raise the floor for the
/// app on screen and foreground apps, `bg_max` caps background work so it
/// does not steal the big cores. Not locked, so the vendor power HAL can
/// still boost on touch.
pub fn set_uclamp(top_min: &str, fg_min: &str, bg_max: &str) {
    write_unlock(top_min, UCLAMP_NODES[0]);
    write_unlock(fg_min, UCLAMP_NODES[1]);
    write_unlock(bg_max, UCLAMP_NODES[2]);
    write_unlock(bg_max, UCLAMP_NODES[3]);
}

/// Puts back the values saved by [backup_uclamp], or neutral ones if there
/// is no backup.
pub fn restore_uclamp() {
    match fs::read_to_string(uclamp_backup_path()) {
        Ok(saved) => {
            for line in saved.lines() {
                if let Some((node, value)) = line.split_once('=') {
                    if UCLAMP_NODES.contains(&node) {
                        write_unlock(value, node);
                    }
                }
            }
        }
        Err(_) => set_uclamp("0", "0", "max"),
    }
}

fn node_defaults_path() -> String {
    format!("{}/API/node_default", CONFIG_PATH)
}

/// Saves the current value of every node matching `patterns` the first time
/// it is seen, so a profile can later put back what the vendor set.
pub fn backup_nodes(patterns: &[&str]) {
    let file = node_defaults_path();
    let mut saved = fs::read_to_string(&file).unwrap_or_default();
    let mut changed = false;
    for pattern in patterns {
        let Ok(paths) = glob(pattern) else { continue };
        for path in paths.flatten() {
            let p = path.to_string_lossy().into_owned();
            if saved.lines().any(|l| l.split_once('=').map(|(k, _)| k == p).unwrap_or(false)) {
                continue;
            }
            // A governor node reads like "performance", or "[a] b c" on some kernels.
            let raw = fs::read_to_string(&path).unwrap_or_default();
            let value = raw
                .split_whitespace()
                .find(|w| w.starts_with('['))
                .map(|w| w.trim_matches(|c| c == '[' || c == ']').to_string())
                .unwrap_or_else(|| raw.trim().to_string());
            if !value.is_empty() {
                saved.push_str(&format!("{}={}\n", p, value));
                changed = true;
            }
        }
    }
    if changed {
        let _ = fs::write(&file, saved);
    }
}

/// Puts back the saved vendor value of `path`; does nothing when none was saved.
pub fn restore_node(path: &str) {
    let saved = fs::read_to_string(node_defaults_path()).unwrap_or_default();
    if let Some(value) = saved.lines().find_map(|l| l.strip_prefix(path)?.strip_prefix('=')) {
        write_unlock(value, path);
    }
}

fn getsetting_secure(key: &str) -> String {
    Command::new("settings")
        .args(["get", "secure", key])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default()
}
