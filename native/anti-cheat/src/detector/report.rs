use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MemoryHit {
    pub offset: usize,
    pub pattern_id: String,
    pub context: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DetectorReport {
    pub debugger_attached: bool,
    pub debugger_type: Option<String>,
    pub timing_skew_ms: i64,
    pub hwbp_active: bool,
    pub injected_modules: Vec<String>,
    pub timing_spoof: bool,
    pub async_key_inconsistent: bool,
    pub memory_hits: Vec<MemoryHit>,
    pub anti_vm_indicators: Vec<String>,
    pub integrity_score: u8,
    pub scan_ts: u64,
}

impl Default for DetectorReport {
    fn default() -> Self {
        Self {
            debugger_attached: false,
            debugger_type: None,
            timing_skew_ms: 0,
            hwbp_active: false,
            injected_modules: Vec::new(),
            timing_spoof: false,
            async_key_inconsistent: false,
            memory_hits: Vec::new(),
            anti_vm_indicators: Vec::new(),
            integrity_score: 100,
            scan_ts: 0,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ThreatAlert {
    pub rule: String,
    pub severity: String,
    pub detail: String,
}

impl ThreatAlert {
    pub fn new(rule: impl Into<String>, severity: impl Into<String>, detail: impl Into<String>) -> Self {
        Self {
            rule: rule.into(),
            severity: severity.into(),
            detail: detail.into(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QuickReport {
    pub debugger_attached: bool,
    pub timing_skew_ms: i64,
    pub timing_spoof: bool,
    pub scan_ts: u64,
}

impl From<&DetectorReport> for QuickReport {
    fn from(r: &DetectorReport) -> Self {
        Self {
            debugger_attached: r.debugger_attached,
            timing_skew_ms: r.timing_skew_ms,
            timing_spoof: r.timing_spoof,
            scan_ts: r.scan_ts,
        }
    }
}
