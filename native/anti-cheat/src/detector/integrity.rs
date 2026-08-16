#[cfg(windows)]
use std::ffi::OsString;
#[cfg(windows)]
use std::os::windows::ffi::OsStringExt;

#[cfg(windows)]
use windows_sys::Win32::Foundation::CloseHandle;
#[cfg(windows)]
use windows_sys::Win32::System::Diagnostics::ToolHelp::{
    CreateToolhelp32Snapshot, Module32FirstW, Module32NextW, MODULEENTRY32W, TH32CS_SNAPMODULE,
};
#[cfg(windows)]
use windows_sys::Win32::System::Threading::{
    OpenProcess, PROCESS_QUERY_INFORMATION,
};

#[derive(Debug, Clone, Default)]
pub struct IntegrityResult {
    pub injected_modules: Vec<String>,
    pub score: u8,
}

#[cfg(windows)]
const ALLOWED_MODULES: &[&str] = &[
    "jvm.dll",
    "ntdll.dll",
    "kernel32.dll",
    "kernelbase.dll",
    "user32.dll",
    "advapi32.dll",
    "gdi32.dll",
    "winmm.dll",
    "d3d11.dll",
    "dxgi.dll",
    "opengl32.dll",
    "d3d9.dll",
    "d3dcompiler_47.dll",
    "d3dcompiler_33.dll",
    "vcruntime140.dll",
    "msvcp140.dll",
    "msvcp140_1.dll",
    "msvcp140_2.dll",
    "msvcp140_atomic_wait.dll",
    "msvcp140_codecvt_ids.dll",
    "msvcp140_concurrency.dll",
    "msvcp140_observation.dll",
    "ucrtbase.dll",
    "api-ms-win-core-",
];

#[cfg(windows)]
pub fn process_integrity(target_pid: u32) -> IntegrityResult {
    let mut result = IntegrityResult::default();
    let mut total_modules: u32 = 0;
    let mut suspicious_count: u32 = 0;

    unsafe {
        let h_process = OpenProcess(PROCESS_QUERY_INFORMATION, 0, target_pid);
        if h_process.is_null() {
            result.score = 50;
            return result;
        }

        let snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, target_pid);
        if snapshot as i64 == -1 {
            CloseHandle(h_process);
            result.score = 50;
            return result;
        }

        let mut entry: MODULEENTRY32W = std::mem::zeroed();
        entry.dwSize = std::mem::size_of::<MODULEENTRY32W>() as u32;

        if Module32FirstW(snapshot, &mut entry) == 0 {
            CloseHandle(snapshot);
            CloseHandle(h_process);
            result.score = 60;
            return result;
        }

        loop {
            total_modules += 1;

            let name_wide = &entry.szModule;
            let null_idx = name_wide.iter().position(|&c| c == 0).unwrap_or(name_wide.len());
            let os_name = OsString::from_wide(&name_wide[..null_idx]);
            let name_lower = os_name.to_string_lossy().to_lowercase();

            let path_wide = &entry.szExePath;
            let path_null = path_wide.iter().position(|&c| c == 0).unwrap_or(path_wide.len());
            let os_path = OsString::from_wide(&path_wide[..path_null]);
            let path_lower = os_path.to_string_lossy().to_lowercase();

            let is_allowed = ALLOWED_MODULES.iter().any(|m| {
                let ml = m.to_lowercase();
                if ml.ends_with('-') {
                    name_lower.starts_with(&ml[..ml.len().saturating_sub(1)])
                } else {
                    name_lower == ml
                }
            });

            let in_system_path = path_lower.contains("\\windows\\system32\\")
                || path_lower.contains("\\windows\\syswow64\\")
                || path_lower.contains("\\program files\\")
                || path_lower.contains("\\program files (x86)\\")
                || path_lower.contains("\\programdata\\")
                || path_lower.contains("\\microsoft\\");

            let in_jre_path = path_lower.contains("\\jre")
                || path_lower.contains("\\java")
                || path_lower.contains("\\jdk");

            if !is_allowed && !in_system_path && !in_jre_path {
                let full_name = os_name.to_string_lossy().to_string();
                let full_path = os_path.to_string_lossy().to_string();
                result.injected_modules.push(format!("{} @ {}", full_name, full_path));
                suspicious_count += 1;
            }

            if Module32NextW(snapshot, &mut entry) == 0 {
                break;
            }
        }

        CloseHandle(snapshot);
        CloseHandle(h_process);
    }

    if total_modules == 0 {
        result.score = 0;
    } else {
        let ratio = suspicious_count as f32 / total_modules as f32;
        result.score = ((1.0 - ratio) * 100.0).clamp(0.0, 100.0) as u8;
    }

    result
}

#[cfg(not(windows))]
pub fn process_integrity(_target_pid: u32) -> IntegrityResult {
    IntegrityResult {
        score: 100,
        ..Default::default()
    }
}
