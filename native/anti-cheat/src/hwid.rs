use std::sync::OnceLock;

use sha2::{Digest, Sha256};

#[derive(Debug, Clone)]
pub struct HwidComponents {
    pub macs: Vec<String>,
    pub cpu_brand: String,
    pub disk_serial: String,
    pub machine_guid: String,
    pub boot_uuid: String,
    pub build_id: String,
    pub os_major: u32,
    pub os_minor: u32,
    pub os_build: u32,
}

#[derive(Debug, Clone)]
pub struct HwidCollector {
    pub salt: String,
    pub components: HwidComponents,
}

fn process_salt() -> &'static String {
    static SALT: OnceLock<String> = OnceLock::new();
    SALT.get_or_init(|| {
        use rand::RngCore;
        let mut buf = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut buf);
        buf.iter().map(|b| format!("{:02x}", b)).collect()
    })
}

impl HwidCollector {
    pub fn collect() -> Self {
        let macs = platform::collect_macs().unwrap_or_default();
        let cpu_brand = platform::collect_cpu().unwrap_or_default();
        let disk_serial = platform::collect_disk_serial().unwrap_or_default();
        let machine_guid = platform::collect_machine_guid().unwrap_or_default();
        let boot_uuid = platform::collect_boot_uuid().unwrap_or_default();
        let (os_major, os_minor, os_build) =
            platform::collect_os_version().unwrap_or((0, 0, 0));

        HwidCollector {
            salt: process_salt().clone(),
            components: HwidComponents {
                macs,
                cpu_brand,
                disk_serial,
                machine_guid,
                boot_uuid,
                build_id: String::new(),
                os_major,
                os_minor,
                os_build,
            },
        }
    }

    pub fn collect_and_hash() -> String {
        let collector = Self::collect();
        collector.hash()
    }

    pub fn hash(&self) -> String {
        let c = &self.components;
        let raw = format!(
            "{}|{}|{}|{}|{}|{}|{}|{}|{}|{}",
            self.salt,
            c.macs.join(":"),
            c.cpu_brand,
            c.disk_serial,
            c.machine_guid,
            c.boot_uuid,
            c.build_id,
            c.os_major,
            c.os_minor,
            c.os_build,
        );
        let mut hasher = Sha256::new();
        hasher.update(raw.as_bytes());
        let result = hasher.finalize();
        result.iter().map(|b| format!("{:02x}", b)).collect()
    }
}

#[cfg(windows)]
mod platform {
    use std::ptr;

    use windows_sys::Win32::Foundation::TRUE;
    use windows_sys::Win32::NetworkManagement::IpHelper::{
        GetAdaptersInfo, IP_ADAPTER_INFO,
    };
    use windows_sys::Win32::Storage::FileSystem::GetVolumeInformationW;
    use windows_sys::Win32::System::Registry::{
        RegCloseKey, RegOpenKeyExW, RegQueryValueExW, HKEY, HKEY_LOCAL_MACHINE, KEY_READ,
    };
    use windows_sys::Win32::System::SystemInformation::{
        GetVersionExW, OSVERSIONINFOW,
    };

    fn wstr_null(s: &str) -> Vec<u16> {
        s.encode_utf16().chain(std::iter::once(0)).collect()
    }

    fn read_reg_sz(hkey: HKEY, subkey: &str, value: &str) -> Option<String> {
        let subkey_w = wstr_null(subkey);
        let value_w = wstr_null(value);
        let mut opened: HKEY = ptr::null_mut();

        let open_res = unsafe {
            RegOpenKeyExW(hkey, subkey_w.as_ptr(), 0, KEY_READ, &mut opened)
        };
        if open_res != 0 {
            return None;
        }

        let mut data_len: u32 = 0;
        let query_size = unsafe {
            RegQueryValueExW(
                opened,
                value_w.as_ptr(),
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
                &mut data_len,
            )
        };
        if query_size != 0 || data_len == 0 {
            unsafe { RegCloseKey(opened) };
            return None;
        }

        let mut buf = vec![0u8; data_len as usize];
        let query_data = unsafe {
            RegQueryValueExW(
                opened,
                value_w.as_ptr(),
                ptr::null_mut(),
                ptr::null_mut(),
                buf.as_mut_ptr(),
                &mut data_len,
            )
        };
        unsafe { RegCloseKey(opened) };
        if query_data != 0 {
            return None;
        }

        let wslice = unsafe {
            std::slice::from_raw_parts(
                buf.as_ptr() as *const u16,
                (data_len as usize) / 2,
            )
        };
        let trimmed = wslice
            .iter()
            .copied()
            .take_while(|&c| c != 0)
            .collect::<Vec<u16>>();
        String::from_utf16(&trimmed).ok()
    }

    pub fn collect_macs() -> Option<Vec<String>> {
        let mut buf_len: u32 = 0;
        let first = unsafe { GetAdaptersInfo(ptr::null_mut(), &mut buf_len) };
        if first != 0 {
            return None;
        }

        let mut buf = vec![0u8; buf_len as usize];
        let second = unsafe {
            GetAdaptersInfo(buf.as_mut_ptr() as *mut IP_ADAPTER_INFO, &mut buf_len)
        };
        if second != 0 {
            return None;
        }

        let mut macs: Vec<String> = Vec::new();
        let mut current = buf.as_ptr() as *mut IP_ADAPTER_INFO;
        while !current.is_null() {
            let info = unsafe { &*current };
            let len = info.AddressLength as usize;
            if len > 0 && len <= 6 {
                let addr = &info.Address;
                let bytes = &addr[..len];
                let all_zero = bytes.iter().all(|&b| b == 0);
                if !all_zero {
                    let mac = bytes
                        .iter()
                        .map(|b| format!("{:02x}", b))
                        .collect::<Vec<_>>()
                        .join(":");
                    macs.push(mac);
                }
            }
            current = info.Next;
        }
        Some(macs)
    }

    pub fn collect_cpu() -> Option<String> {
        read_reg_sz(
            HKEY_LOCAL_MACHINE,
            r"HARDWARE\DESCRIPTION\System\CentralProcessor\0",
            "ProcessorNameString",
        )
    }

    pub fn collect_disk_serial() -> Option<String> {
        let root = wstr_null("C:\\");
        let mut serial: u32 = 0;
        let ok = unsafe {
            GetVolumeInformationW(
                root.as_ptr(),
                ptr::null_mut(),
                0,
                &mut serial,
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
                0,
            )
        };
        if ok == TRUE {
            Some(format!("{:08x}", serial))
        } else {
            None
        }
    }

    pub fn collect_machine_guid() -> Option<String> {
        read_reg_sz(
            HKEY_LOCAL_MACHINE,
            r"SOFTWARE\Microsoft\Cryptography",
            "MachineGuid",
        )
    }

    pub fn collect_boot_uuid() -> Option<String> {
        read_reg_sz(
            HKEY_LOCAL_MACHINE,
            r"SYSTEM\CurrentControlSet\Control\CryptoDNGUID",
            "MachineGuid",
        )
        .or_else(|| {
            read_reg_sz(
                HKEY_LOCAL_MACHINE,
                r"SOFTWARE\Microsoft\Windows NT\CurrentVersion",
                "CM_DFU_MachineId",
            )
        })
    }

    pub fn collect_os_version() -> Option<(u32, u32, u32)> {
        let mut info: OSVERSIONINFOW = unsafe { std::mem::zeroed() };
        info.dwOSVersionInfoSize = std::mem::size_of::<OSVERSIONINFOW>() as u32;
        let ok = unsafe { GetVersionExW(&mut info) };
        if ok == TRUE {
            Some((
                info.dwMajorVersion,
                info.dwMinorVersion,
                info.dwBuildNumber,
            ))
        } else {
            None
        }
    }
}

#[cfg(not(windows))]
mod platform {
    use std::fs;

    pub fn collect_macs() -> Option<Vec<String>> {
        let mut macs = Vec::new();
        let net_dir = fs::read_dir("/sys/class/net").ok()?;
        for entry in net_dir.flatten() {
            let name = entry.file_name();
            let name_str = name.to_string_lossy();
            if name_str == "lo" {
                continue;
            }
            let addr_path = format!("/sys/class/net/{}/address", name_str);
            if let Ok(mac) = fs::read_to_string(&addr_path) {
                let mac = mac.trim().to_string();
                if !mac.is_empty() && mac != "00:00:00:00:00:00" {
                    macs.push(mac);
                }
            }
        }
        Some(macs)
    }

    pub fn collect_cpu() -> Option<String> {
        let cpuinfo = fs::read_to_string("/proc/cpuinfo").ok()?;
        for line in cpuinfo.lines() {
            if let Some(rest) = line.strip_prefix("model name") {
                let val = rest.split(':').nth(1).map(|s| s.trim().to_string());
                if val.is_some() {
                    return val;
                }
            }
        }
        None
    }

    pub fn collect_disk_serial() -> Option<String> {
        None
    }

    pub fn collect_machine_guid() -> Option<String> {
        fs::read_to_string("/etc/machine-id")
            .ok()
            .map(|s| s.trim().to_string())
    }

    pub fn collect_boot_uuid() -> Option<String> {
        None
    }

    pub fn collect_os_version() -> Option<(u32, u32, u32)> {
        None
    }
}
