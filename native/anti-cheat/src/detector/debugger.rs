#[cfg(windows)]
use windows_sys::Win32::Foundation::{CloseHandle, FALSE};
#[cfg(windows)]
use windows_sys::Win32::System::Diagnostics::Debug::{CheckRemoteDebuggerPresent, IsDebuggerPresent};
#[cfg(windows)]
use windows_sys::Win32::System::Threading::{GetCurrentProcess, OpenProcess, PROCESS_QUERY_INFORMATION};

#[derive(Debug, Clone, Default)]
pub struct DebuggerResult {
    pub attached: bool,
    pub debugger_type: Option<String>,
}

#[cfg(windows)]
const PROCESS_DEBUG_PORT: u32 = 7;

#[cfg(windows)]
extern "system" {
    fn NtQueryInformationProcess(
        process_handle: *mut core::ffi::c_void,
        process_information_class: u32,
        process_information: *mut core::ffi::c_void,
        process_information_length: u32,
        return_length: *mut u32,
    ) -> i32;
}

#[cfg(windows)]
pub fn debugger_check() -> DebuggerResult {
    let mut result = DebuggerResult::default();

    if unsafe { IsDebuggerPresent() } != FALSE {
        result.attached = true;
        result.debugger_type = Some("IsDebuggerPresent".to_string());
    }

    let mut remote_present: i32 = 0;
    let ok = unsafe {
        CheckRemoteDebuggerPresent(GetCurrentProcess(), &mut remote_present)
    };
    if ok != 0 && remote_present != 0 {
        result.attached = true;
        result.debugger_type = Some("CheckRemoteDebuggerPresent".to_string());
    }

    unsafe {
        let handle = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE, std::process::id());
        if !handle.is_null() {
            let mut port: usize = 0;
            let mut return_len: u32 = 0;
            let status = NtQueryInformationProcess(
                handle,
                PROCESS_DEBUG_PORT,
                &mut port as *mut usize as *mut core::ffi::c_void,
                std::mem::size_of::<usize>() as u32,
                &mut return_len,
            );
            if status == 0 && port != 0 && port != usize::MAX {
                result.attached = true;
                result.debugger_type = Some("NtQueryInformationProcess.DebugPort".to_string());
            }
            CloseHandle(handle);
        }
    }

    result
}

#[cfg(not(windows))]
pub fn debugger_check() -> DebuggerResult {
    DebuggerResult::default()
}
