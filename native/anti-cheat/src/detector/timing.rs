#[cfg(windows)]
use windows_sys::Win32::System::Performance::{QueryPerformanceCounter, QueryPerformanceFrequency};
#[cfg(windows)]
use windows_sys::Win32::System::SystemInformation::GetTickCount;

#[derive(Debug, Clone, Default)]
pub struct TimingResult {
    pub skew_ms: i64,
    pub spoofed: bool,
    pub monotonic_violation: bool,
}

#[cfg(windows)]
pub fn timing_check() -> TimingResult {
    let freq = unsafe {
        let mut f = 0i64;
        QueryPerformanceFrequency(&mut f);
        f.max(1)
    };

    let mut prev_tick: Option<u32> = None;
    let mut prev_qpc: Option<i64> = None;
    let mut max_skew: i64 = 0;
    let mut monotonic_violation = false;

    for _ in 0..5 {
        let tick = unsafe { GetTickCount() };
        let qpc = unsafe {
            let mut q = 0i64;
            QueryPerformanceCounter(&mut q);
            q
        };

        if let (Some(pt), Some(pq)) = (prev_tick, prev_qpc) {
            let tick_delta = (tick as i64).wrapping_sub(pt as i64);
            let qpc_delta = (qpc - pq) * 1000 / freq;

            if tick_delta < 0 {
                monotonic_violation = true;
            }

            let diff = (tick_delta - qpc_delta).abs();
            if diff > max_skew {
                max_skew = diff;
            }
        }

        prev_tick = Some(tick);
        prev_qpc = Some(qpc);

        std::thread::sleep(std::time::Duration::from_millis(20));
    }

    let overall_skew = max_skew;
    let spoofed = overall_skew > 50 || monotonic_violation;

    TimingResult {
        skew_ms: overall_skew,
        spoofed,
        monotonic_violation,
    }
}

#[cfg(not(windows))]
pub fn timing_check() -> TimingResult {
    TimingResult::default()
}
