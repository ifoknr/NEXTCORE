## NextCore v2.0.0
### • Changelog
- First NextCore release, based on AZenith 5.2 (uninstall AZenith before installing)
- New NextCore branding: module banner, in-app banner and app name
- Added an Arabic KernelSU WebUI: status, Auto/Manual mode, profiles, CPU governor and bypass charging
- Added action.sh commands: get_status, set_global, set_profile, set_auto
- Added in-manager updates through GitHub Releases
- Added Galaxy Tab S10 Ultra / Dimensity 9300+ (MT6989) support and better SoC detection
- Bypass charging now tries Samsung slate mode before store mode
- Security: daemon notifications no longer go through a shell, command inputs are validated, helper tools no longer run arbitrary commands, app receivers are no longer exported
- Moved blocking root calls in the app off the main thread
- Shorter version string and English module description
- New HUD interface: swipeable pages, bottom bar on phones, side rail on tablets
- New CPU, memory and frames engine pages, a sessions page and per-game settings
- Records temperature, CPU clock and FPS while games run, with per-game history
- Safe on any device: no read-only locks, vendor thermal untouched, GPU/DDR only pinned in max mode, conservative background cleaner
- Renamed all internal identifiers to nextcore (old game list is migrated)
