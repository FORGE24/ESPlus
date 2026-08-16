#[cfg(windows)]
use windows_sys::Win32::UI::Input::KeyboardAndMouse::GetAsyncKeyState;
#[cfg(windows)]
use windows_sys::Win32::UI::WindowsAndMessaging::{
    GetForegroundWindow, GetWindowTextLengthW, GetWindowTextW,
};
#[cfg(windows)]
use std::ffi::OsString;
#[cfg(windows)]
use std::os::windows::ffi::OsStringExt;

#[derive(Debug, Clone, Default)]
pub struct KeyboardResult {
    pub key_combo_held: bool,
    pub async_inconsistent: bool,
    pub foreground_minecraft: bool,
}

#[cfg(windows)]
const VK_LWIN: i32 = 0x5B;
#[cfg(windows)]
const VK_CONTROL: i32 = 0x11;
#[cfg(windows)]
const VK_MENU: i32 = 0x12;

#[cfg(windows)]
pub fn keyboard_check() -> KeyboardResult {
    let mut result = KeyboardResult::default();

    let win_pressed = unsafe { (GetAsyncKeyState(VK_LWIN) as u16) & 0x8000 != 0 };
    let ctrl_pressed = unsafe { (GetAsyncKeyState(VK_CONTROL) as u16) & 0x8000 != 0 };
    let alt_pressed = unsafe { (GetAsyncKeyState(VK_MENU) as u16) & 0x8000 != 0 };

    if win_pressed && ctrl_pressed && alt_pressed {
        let start = std::time::Instant::now();
        let mut held_2s = false;
        loop {
            std::thread::sleep(std::time::Duration::from_millis(100));
            let w = unsafe { (GetAsyncKeyState(VK_LWIN) as u16) & 0x8000 != 0 };
            let c = unsafe { (GetAsyncKeyState(VK_CONTROL) as u16) & 0x8000 != 0 };
            let a = unsafe { (GetAsyncKeyState(VK_MENU) as u16) & 0x8000 != 0 };
            if !(w && c && a) {
                break;
            }
            if start.elapsed().as_millis() > 2000 {
                held_2s = true;
                break;
            }
        }
        if held_2s {
            result.key_combo_held = true;
        }
    }

    let fg = unsafe { GetForegroundWindow() };
    if !fg.is_null() {
        let len = unsafe { GetWindowTextLengthW(fg) };
        if len > 0 {
            let mut buf = vec![0u16; (len as usize) + 1];
            let copied = unsafe { GetWindowTextW(fg, buf.as_mut_ptr(), buf.len() as i32) };
            if copied > 0 {
                let title = OsString::from_wide(&buf[..copied as usize]);
                let title_lower = title.to_string_lossy().to_lowercase();
                if title_lower.contains("minecraft")
                    || title_lower.contains("java")
                    || title_lower.contains("jvm")
                {
                    result.foreground_minecraft = true;
                }
            }
        }
    }

    result
}

#[cfg(not(windows))]
pub fn keyboard_check() -> KeyboardResult {
    KeyboardResult::default()
}
