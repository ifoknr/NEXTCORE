use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct AppPreference {
    pub target_fps: String,
    pub governor: String,
    pub refresh_rate: String,
    pub bypass_charging: String,
    pub optimize_threads: bool,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct GlobalPreference {
    pub auto_detect: bool,
    pub current_soc: String,
    pub bypass_charging: String,
    pub global_governor: String,
    pub optimize_threads: bool,
    pub danger_warnings_accepted: bool,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ModuleConfig {
    pub global: GlobalPreference,
    pub apps: std::collections::HashMap<String, AppPreference>,
}

pub struct PreferenceEngine;

impl PreferenceEngine {
    const CONFIG_PATH: &'static str = "/data/adb/modules/nextcore/config.json";

    pub fn load_config() -> ModuleConfig {
        if Path::new(Self::CONFIG_PATH).exists() {
            if let Ok(content) = fs::read_to_string(Self::CONFIG_PATH) {
                if let Ok(cfg) = serde_json::from_str(&content) {
                    return cfg;
                }
            }
        }
        Self::default_config()
    }

    pub fn save_config(config: &ModuleConfig) -> bool {
        if let Ok(serialized) = serde_json::to_string_pretty(config) {
            return fs::write(Self::CONFIG_PATH, serialized).is_ok();
        }
        false
    }

    pub fn default_config() -> ModuleConfig {
        ModuleConfig {
            global: GlobalPreference {
                auto_detect: true,
                current_soc: "auto".to_string(),
                bypass_charging: "auto".to_string(),
                global_governor: "auto".to_string(),
                optimize_threads: false,
                danger_warnings_accepted: false,
            },
            apps: std::collections::HashMap::new(),
        }
    }

    pub fn resolve_fps(pkg: &str) -> Option<u32> {
        let cfg = Self::load_config();
        if let Some(app_pref) = cfg.apps.get(pkg) {
            if app_pref.target_fps != "auto" {
                return app_pref.target_fps.parse::<u32>().ok();
            }
        }
        None
    }
}