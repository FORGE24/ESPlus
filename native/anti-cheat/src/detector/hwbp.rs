#[cfg(windows)]
use windows_sys::Win32::Foundation::{CloseHandle, FALSE};
#[cfg(windows)]
use windows_sys::Win32::System::Threading::{OpenProcess, PROCESS_QUERY_INFORMATION};

#[derive(Debug, Clone, Default)]
pub struct HwbpResult {
    pub active: bool,
}

#[cfg(windows)]
const PROCESS_DEBUG_OBJECT_HANDLE: u32 = 30;

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
pub fn hwbp_check() -> HwbpResult {
    let mut result = HwbpResult::default();

    unsafe {
        let handle = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE, std::process::id());
        if handle.is_null() {
            return result;
        }

        let mut debug_object_handle: usize = 0;
        let mut return_len: u32 = 0;

        let status = NtQueryInformationProcess(
            handle,
            PROCESS_DEBUG_OBJECT_HANDLE,
            &mut debug_object_handle as *mut usize as *mut core::ffi::c_void,
            std::mem::size_of::<usize>() as u32,
            &mut return_len,
        );

        if status == 0 && debug_object_handle != 0 {
            result.active = true;
        }

        CloseHandle(handle);
    }

    result
}

#[cfg(not(windows))]
pub fn hwbp_check() -> HwbpResult {
    HwbpResult::default()
}
