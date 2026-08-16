#[cfg(windows)]
use windows_sys::Win32::System::Registry::{
    HKEY, HKEY_LOCAL_MACHINE, KEY_READ, RegCloseKey, RegOpenKeyExW, RegQueryValueExW,
};

#[cfg(windows)]
use std::ffi::OsString;
#[cfg(windows)]
use std::os::windows::ffi::OsStringExt;

#[cfg(windows)]
const VM_INDICATORS: &[&str] = &[
    "vmware",
    "virtualbox",
    "qemu",
    "microsoft corporation (virtual)",
    "parallels",
    "hyper-v",
];

#[cfg(windows)]
pub fn check_anti_vm() -> Vec<String> {
    let mut indicators = Vec::new();

    let subkey: Vec<u16> = "HARDWARE\\DESCRIPTION\\System\\BIOS\0".encode_utf16().collect();
    let value_name: Vec<u16> = "SystemManufacturer\0".encode_utf16().collect();

    let mut h_key: HKEY = std::ptr::null_mut();
    let open_status = unsafe {
        RegOpenKeyExW(
            HKEY_LOCAL_MACHINE,
            subkey.as_ptr(),
            0,
            KEY_READ,
            &mut h_key,
        )
    };

    if open_status == 0 && !h_key.is_null() {
        let mut value_len: u32 = 0;
        let mut value_type: u32 = 0;

        let query_status = unsafe {
            RegQueryValueExW(
                h_key,
                value_name.as_ptr(),
                std::ptr::null_mut(),
                &mut value_type,
                std::ptr::null_mut(),
                &mut value_len,
            )
        };

        if query_status == 0 && value_len > 0 {
            let mut buf = vec![0u8; value_len as usize];
            let mut read_len = value_len;
            let _ = unsafe {
                RegQueryValueExW(
                    h_key,
                    value_name.as_ptr(),
                    std::ptr::null_mut(),
                    &mut value_type,
                    buf.as_mut_ptr(),
                    &mut read_len,
                )
            };

            let wide = unsafe {
                let slice = std::slice::from_raw_parts(
                    buf.as_ptr() as *const u16,
                    (read_len as usize / 2).min(256),
                );
                slice
            };

            let manufacturer = OsString::from_wide(wide)
                .to_string_lossy()
                .to_lowercase()
                .to_string();

            for indicator in VM_INDICATORS {
                if manufacturer.contains(indicator) {
                    indicators.push(format!("VM manufacturer: {}", manufacturer));
                    break;
                }
            }
        }

        unsafe { RegCloseKey(h_key) };
    }

    let product_name_subkey: Vec<u16> = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\0"
        .encode_utf16()
        .collect();
    let mut h_product: HKEY = std::ptr::null_mut();
    let open2 = unsafe {
        RegOpenKeyExW(
            HKEY_LOCAL_MACHINE,
            product_name_subkey.as_ptr(),
            0,
            KEY_READ,
            &mut h_product,
        )
    };
    if open2 == 0 && !h_product.is_null() {
        let product_name_wide: Vec<u16> = "ProductName\0".encode_utf16().collect();
        let mut value_len: u32 = 0;
        let mut value_type: u32 = 0;
        let qs = unsafe {
            RegQueryValueExW(
                h_product,
                product_name_wide.as_ptr(),
                std::ptr::null_mut(),
                &mut value_type,
                std::ptr::null_mut(),
                &mut value_len,
            )
        };
        if qs == 0 && value_len > 0 {
            let mut buf = vec![0u8; value_len as usize];
            let mut read_len = value_len;
            let _ = unsafe {
                RegQueryValueExW(
                    h_product,
                    product_name_wide.as_ptr(),
                    std::ptr::null_mut(),
                    &mut value_type,
                    buf.as_mut_ptr(),
                    &mut read_len,
                )
            };
            let wide = unsafe {
                std::slice::from_raw_parts(
                    buf.as_ptr() as *const u16,
                    (read_len as usize / 2).min(256),
                )
            };
            let product = OsString::from_wide(wide)
                .to_string_lossy()
                .to_lowercase()
                .to_string();
            if product.contains("virtual") {
                indicators.push(format!("VM product: {}", product));
            }
        }
        unsafe { RegCloseKey(h_product) };
    }

    indicators
}

#[cfg(not(windows))]
pub fn check_anti_vm() -> Vec<String> {
    Vec::new()
}
