use crate::detector::report::{DetectorReport, ThreatAlert};
use crate::detector::timing;
use crate::detector::debugger;
use crate::detector::hwbp;
use crate::detector::integrity;
use crate::detector::keyboard;
use crate::detector::patterns;
use crate::detector::anti_vm;

pub struct DetectorEngine {
    pub minecraft_pid: u32,
    pub shared_key: [u8; 32],
    pub tick_count: u64,
}

impl DetectorEngine {
    pub fn new(minecraft_pid: u32, shared_key: [u8; 32]) -> Self {
        Self {
            minecraft_pid,
            shared_key,
            tick_count: 0,
        }
    }

    pub fn tick(&mut self) -> Vec<ThreatAlert> {
        self.tick_count = self.tick_count.wrapping_add(1);
        let mut alerts = Vec::new();

        let timing = timing::timing_check();
        if timing.spoofed {
            alerts.push(ThreatAlert::new(
                "TIMING_SPOOF",
                if timing.monotonic_violation { "CRITICAL" } else { "HIGH" },
                format!(
                    "GetTickCount/QueryPerformanceCounter skew {}ms, monotonic_violation={}",
                    timing.skew_ms, timing.monotonic_violation
                ),
            ));
        }

        let dbg = debugger::debugger_check();
        if dbg.attached {
            alerts.push(ThreatAlert::new(
                "DEBUGGER_ATTACHED",
                "CRITICAL",
                format!(
                    "debugger detected via {:?}",
                    dbg.debugger_type.unwrap_or_default()
                ),
            ));
        }

        let kb = keyboard::keyboard_check();
        if kb.key_combo_held {
            alerts.push(ThreatAlert::new(
                "KEYBOARD_COMBO_HELD",
                "MEDIUM",
                "Win+Ctrl+Alt held for >2s (possible macro/AHK injection)".to_string(),
            ));
        }

        alerts
    }

    pub fn run_quick_scan(&self) -> DetectorReport {
        let mut report = DetectorReport::default();
        report.scan_ts = now_ms();

        let timing = timing::timing_check();
        report.timing_skew_ms = timing.skew_ms;
        report.timing_spoof = timing.spoofed;

        let dbg = debugger::debugger_check();
        report.debugger_attached = dbg.attached;
        report.debugger_type = dbg.debugger_type;

        report
    }

    pub fn run_deep_scan(&self) -> DetectorReport {
        let mut report = self.run_quick_scan();

        let h = hwbp::hwbp_check();
        report.hwbp_active = h.active;

        let intg = integrity::process_integrity(self.minecraft_pid);
        report.injected_modules = intg.injected_modules;
        report.integrity_score = intg.score;

        let patterns = patterns::default_patterns();
        report.memory_hits = patterns::scan_memory(self.minecraft_pid, &patterns);

        report.anti_vm_indicators = anti_vm::check_anti_vm();

        let kb = keyboard::keyboard_check();
        report.async_key_inconsistent = kb.key_combo_held;

        report
    }
}

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}
