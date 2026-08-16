use crate::detector::report::MemoryHit;

#[cfg(windows)]
use windows_sys::Win32::Foundation::{CloseHandle, FALSE};
#[cfg(windows)]
use windows_sys::Win32::System::Diagnostics::Debug::ReadProcessMemory;
#[cfg(windows)]
use windows_sys::Win32::System::Diagnostics::ToolHelp::{
    CreateToolhelp32Snapshot, Module32FirstW, Module32NextW, MODULEENTRY32W, TH32CS_SNAPMODULE,
};
#[cfg(windows)]
use windows_sys::Win32::System::Threading::{OpenProcess, PROCESS_VM_READ};

#[derive(Debug, Clone)]
pub struct ScanPattern {
    pub id: String,
    pub bytes: Vec<u8>,
    pub context: String,
}

pub fn default_patterns() -> Vec<ScanPattern> {
    vec![
        ScanPattern {
            id: "cheat_engine_window_class".to_string(),
            bytes: vec![
                0x43, 0x68, 0x65, 0x61, 0x74, 0x20, 0x45, 0x6E, 0x67, 0x69, 0x6E, 0x65,
            ],
            context: "Cheat Engine window class string found in memory".to_string(),
        },
        ScanPattern {
            id: "bypass_miicom_unicode".to_string(),
            bytes: "BYPASS_MIICOM".encode_utf16().flat_map(|c| [c as u8, ((c >> 8) & 0xFF) as u8]).collect(),
            context: "MIICOM bypass signature (unicode)".to_string(),
        },
        ScanPattern {
            id: "dinput8_create_trampoline".to_string(),
            bytes: vec![
                0x48, 0xB8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0xFF, 0xE0, 0xCC,
            ],
            context: "DirectInput8 CreateDevice hook trampoline (x64 jmp)".to_string(),
        },
    ]
}

#[cfg(windows)]
pub fn scan_memory(target_pid: u32, patterns: &[ScanPattern]) -> Vec<MemoryHit> {
    let mut hits = Vec::new();

    unsafe {
        let h_process = OpenProcess(PROCESS_VM_READ, FALSE, target_pid);
        if h_process.is_null() {
            return hits;
        }

        let snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, target_pid);
        if snapshot as i64 == -1 {
            CloseHandle(h_process);
            return hits;
        }

        let mut entry: MODULEENTRY32W = std::mem::zeroed();
        entry.dwSize = std::mem::size_of::<MODULEENTRY32W>() as u32;

        if Module32FirstW(snapshot, &mut entry) == 0 {
            CloseHandle(snapshot);
            CloseHandle(h_process);
            return hits;
        }

        let mut scanned = 0u32;
        loop {
            let mod_base = entry.modBaseAddr as *const u8;
            let mod_size = entry.modBaseSize as usize;

            if scanned < 20 {
                let mut buf = vec![0u8; mod_size.min(512 * 1024)];
                let mut bytes_read = 0usize;
                let ok = ReadProcessMemory(
                    h_process,
                    mod_base as *const core::ffi::c_void,
                    buf.as_mut_ptr() as *mut core::ffi::c_void,
                    buf.len(),
                    &mut bytes_read,
                );

                if ok != 0 && bytes_read > 0 {
                    for pattern in patterns {
                        for offset in find_all(&buf[..bytes_read], &pattern.bytes) {
                            hits.push(MemoryHit {
                                offset,
                                pattern_id: pattern.id.clone(),
                                context: pattern.context.clone(),
                            });
                            if hits.len() >= 200 {
                                break;
                            }
                        }
                    }
                }
                scanned += 1;
            }

            if Module32NextW(snapshot, &mut entry) == 0 {
                break;
            }
        }

        CloseHandle(snapshot);
        CloseHandle(h_process);
    }

    hits
}

#[cfg(not(windows))]
pub fn scan_memory(_target_pid: u32, _patterns: &[ScanPattern]) -> Vec<MemoryHit> {
    Vec::new()
}

fn find_all(haystack: &[u8], needle: &[u8]) -> Vec<usize> {
    let mut results = Vec::new();
    if needle.is_empty() || haystack.len() < needle.len() {
        return results;
    }
    let mut pos = 0;
    while pos <= haystack.len() - needle.len() {
        if let Some(found) = haystack[pos..].windows(needle.len()).position(|w| w == needle) {
            let idx = pos + found;
            results.push(idx);
            pos = idx + 1;
        } else {
            break;
        }
    }
    results
}
