#[derive(Debug, Clone, Default)]
pub struct NmiDetection {
    pub kernel_debugger_present: bool,
    pub remote_thread_debug_port: bool,
    pub debug_object_handle: bool,
    pub veh_installed: bool,
    pub total_suspicious: u32,
}

#[cfg(windows)]
unsafe extern "system" {
    fn IsDebuggerPresent() -> i32;
    fn GetCurrentProcess() -> isize;
    fn GetModuleHandleW(lpModuleName: *const u16) -> *mut u8;
    fn GetProcAddress(hModule: *mut u8, lpProcName: *const u8) -> *mut u8;
    fn VirtualQuery(
        lpAddress: *const u8,
        lpBuffer: *mut std::ffi::c_void,
        dwLength: usize,
    ) -> usize;
}

#[cfg(windows)]
type NtQueryInformationProcessFn = unsafe extern "system" fn(
    isize, u32, *mut u8, u32, *mut u32,
) -> i32;

#[cfg(windows)]
unsafe fn load_ntfunc() -> Option<NtQueryInformationProcessFn> {
    let name: Vec<u16> = "ntdll.dll\0".encode_utf16().collect();
    let base = GetModuleHandleW(name.as_ptr());
    if base.is_null() { return None; }
    let func_name = b"NtQueryInformationProcess\0";
    let f = GetProcAddress(base, func_name.as_ptr());
    if f.is_null() { return None; }
    Some(std::mem::transmute::<*mut u8, NtQueryInformationProcessFn>(f))
}

pub fn check_nmi() -> NmiDetection {
    let mut out = NmiDetection::default();

    #[cfg(windows)]
    unsafe {
        if IsDebuggerPresent() != 0 {
            out.kernel_debugger_present = true;
        }

        if let Some(fn_) = load_ntfunc() {
            let mut port: i64 = 0;
            let mut ret_len: u32 = 0;
            let pid = GetCurrentProcess();
            let st = fn_(pid, 7, std::ptr::addr_of_mut!(port) as *mut u8,
                         std::mem::size_of::<i64>() as u32,
                         std::ptr::addr_of_mut!(ret_len));
            if st >= 0 && port != 0 {
                out.remote_thread_debug_port = true;
            }

            let mut obj_handle: i64 = 0;
            let st2 = fn_(pid, 30, std::ptr::addr_of_mut!(obj_handle) as *mut u8,
                          std::mem::size_of::<i64>() as u32,
                          std::ptr::addr_of_mut!(ret_len));
            if st2 >= 0 && obj_handle != 0 {
                out.debug_object_handle = true;
            }
        }

        out.debug_object_handle |= check_guard_pages();
    }

    out.total_suspicious = [
        out.kernel_debugger_present,
        out.remote_thread_debug_port,
        out.debug_object_handle,
        out.veh_installed,
    ].iter().filter(|&&b| b).count() as u32;

    out
}

#[cfg(windows)]
unsafe fn check_guard_pages() -> bool {
    const MEM_COMMIT: u32 = 0x1000;
    const PAGE_GUARD: u32 = 0x100;
    struct Mbi {
        base_address: *mut u8,
        allocation_base: *mut u8,
        allocation_protect: u32,
        region_size: usize,
        state: u32,
        protect: u32,
        type_: u32,
    }
    let mut addr = 0usize;
    let mut guard_pages = 0u32;
    let mut mbi: Mbi = std::mem::zeroed();
    while VirtualQuery(addr as *const u8, std::ptr::addr_of_mut!(mbi) as *mut std::ffi::c_void, std::mem::size_of::<Mbi>()) > 0 {
        if mbi.state == MEM_COMMIT && (mbi.protect & PAGE_GUARD) != 0 {
            guard_pages += 1;
        }
        let cur_end = (mbi.base_address as usize) + mbi.region_size;
        if cur_end <= addr { break; }
        addr = cur_end;
    }
    guard_pages > 5
}

#[cfg(not(windows))]
fn check_guard_pages() -> bool { false }
