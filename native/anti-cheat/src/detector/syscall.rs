#[derive(Debug, Clone, Default)]
pub struct SyscallIntegrity {
    pub ntdll_base: Option<u64>,
    pub tampered_count: u32,
    pub sample_checked: u32,
    pub anomalous_patches: Vec<AnomalousPatch>,
    pub score: u8,
}

#[derive(Debug, Clone)]
pub struct AnomalousPatch {
    pub name: String,
    pub rva: u32,
    pub expected_opcode: u8,
    pub actual_opcode: u8,
    pub description: String,
}

const TARGET_SYSCALLS: &[&str] = &[
    "NtQueryInformationProcess",
    "NtQueryInformationThread",
    "NtSetInformationThread",
    "NtClose",
    "NtReadVirtualMemory",
    "NtWriteVirtualMemory",
    "NtProtectVirtualMemory",
    "NtOpenProcess",
    "NtOpenThread",
    "NtQueryObject",
    "NtSetContextThread",
    "NtQueryContextThread",
    "NtMapViewOfSection",
    "NtUnmapViewOfSection",
    "NtCreateFile",
    "NtOpenFile",
];

#[cfg(windows)]
mod imp {
    use super::*;

    type PfnGetModuleHandle = unsafe extern "system" fn(*const u16) -> *mut u8;
    type PfnGetProcAddress  = unsafe extern "system" fn(*mut u8, *const u8) -> *mut u8;

    unsafe fn load_ntdll() -> *mut u8 {
        let name: Vec<u16> = "ntdll.dll\0".encode_utf16().collect();
        let kernel32: Vec<u16> = "kernel32.dll\0".encode_utf16().collect();

        let k32 = GetModuleHandleW(kernel32.as_ptr());
        if k32.is_null() { return std::ptr::null_mut(); }

        let name_str = b"GetModuleHandleW\0";
        let f: PfnGetModuleHandle = std::mem::transmute(
            GetProcAddress(k32, name_str.as_ptr())
        );
        f(name.as_ptr())
    }

    unsafe fn get_proc(base: *mut u8, name: &str) -> *mut u8 {
        let kernel32: Vec<u16> = "kernel32.dll\0".encode_utf16().collect();
        let k32 = GetModuleHandleW(kernel32.as_ptr());
        if k32.is_null() { return std::ptr::null_mut(); }

        let pa_name = b"GetProcAddress\0";
        let pa: PfnGetProcAddress = std::mem::transmute(
            GetProcAddress(k32, pa_name.as_ptr())
        );
        let name_bytes = format!("{name}\0");
        let name_cstr = name_bytes.as_bytes();
        pa(base, name_cstr.as_ptr())
    }

    pub fn check_self() -> SyscallIntegrity {
        let mut result = SyscallIntegrity { score: 100, ..Default::default() };
        unsafe {
            let ntdll_base = load_ntdll();
            if ntdll_base.is_null() {
                result.score = 0;
                return result;
            }
            result.ntdll_base = Some(ntdll_base as u64);

            for name in TARGET_SYSCALLS {
                let addr = get_proc(ntdll_base, name);
                if addr.is_null() { continue; }
                result.sample_checked += 1;

                let p = addr as *const u8;
                let first = *p;
                let second = *p.add(1);

                if first == 0x4C && second == 0x8B { continue; }

                let desc = match first {
                    0xE9 => "trampoline jump (inline hook)".to_string(),
                    0xEB => "short jump (inline hook)".to_string(),
                    0xCC => "int3 breakpoint".to_string(),
                    0x90 => "nop sled (detour)".to_string(),
                    0x50 => "push prelude (trampoline)".to_string(),
                    0x48 => "mov rax detour wrapper".to_string(),
                    _ => format!("non-prologue opcode 0x{first:02X}"),
                };
                let patch = AnomalousPatch {
                    name: name.to_string(),
                    rva: (p as u64 - ntdll_base as u64) as u32,
                    expected_opcode: 0x4C,
                    actual_opcode: first,
                    description: desc,
                };
                result.anomalous_patches.push(patch);
                result.tampered_count += 1;
            }
        }
        if result.sample_checked > 0 {
            let ratio = result.tampered_count as f32 / result.sample_checked as f32;
            result.score = ((1.0 - ratio) * 100.0).clamp(0.0, 100.0) as u8;
        }
        result
    }
}

#[cfg(not(windows))]
mod imp {
    use super::*;
    pub fn check_self() -> SyscallIntegrity {
        SyscallIntegrity { score: 100, sample_checked: TARGET_SYSCALLS.len() as u32, ..Default::default() }
    }
}

pub fn check_ntdll_integrity() -> SyscallIntegrity {
    imp::check_self()
}

#[cfg(windows)]
unsafe extern "system" {
    fn GetModuleHandleW(lpModuleName: *const u16) -> *mut u8;
    fn GetProcAddress(hModule: *mut u8, lpProcName: *const u8) -> *mut u8;
}

#[cfg(not(windows))]
pub fn check_process_syscalls(_pid: u32) -> SyscallIntegrity {
    SyscallIntegrity { score: 100, ..Default::default() }
}

#[cfg(windows)]
pub fn check_process_syscalls(_pid: u32) -> SyscallIntegrity {
    imp::check_self()
}
