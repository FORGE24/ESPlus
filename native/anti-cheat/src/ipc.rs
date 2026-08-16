use std::error::Error;
use std::time::Duration;

use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub enum IpcMessage {
    Handshake {
        minecraft_pid: u32,
        hwid_seed: String,
    },
    HandshakeAck {
        version: String,
        pid: u32,
    },
    Ping,
    Pong {
        ts: u64,
    },
    RunQuickScan,
    RunDeepScan,
    ScanReport {
        hwid: String,
        report_json: String,
    },
    ThreatAlert {
        rule: String,
        severity: String,
        detail: String,
    },
    Stop,
}

pub type IpcError = Box<dyn Error + Send + Sync>;

pub struct IpcChannel {
    inner: Box<dyn ChannelImpl>,
}

impl IpcChannel {
    pub fn connect(pipe_name: &str, timeout: Duration) -> Result<Self, IpcError> {
        Ok(Self {
            inner: create_channel(pipe_name, timeout)?,
        })
    }

    pub fn send(&mut self, msg: &IpcMessage) -> Result<(), IpcError> {
        let json = serde_json::to_vec(msg)?;
        let len = json.len() as u32;
        let mut frame = Vec::with_capacity(4 + json.len());
        frame.extend_from_slice(&len.to_le_bytes());
        frame.extend_from_slice(&json);
        self.inner.write_all(&frame)
    }

    pub fn recv_timeout(&mut self, timeout: Duration) -> Result<Option<IpcMessage>, IpcError> {
        match self.inner.read_frame(timeout)? {
            Some(frame) => {
                let msg: IpcMessage = serde_json::from_slice(&frame)?;
                Ok(Some(msg))
            }
            None => Ok(None),
        }
    }
}

impl Drop for IpcChannel {
    fn drop(&mut self) {
        let _ = self.inner.close();
    }
}

trait ChannelImpl: Send {
    fn write_all(&mut self, buf: &[u8]) -> Result<(), IpcError>;
    fn read_frame(&mut self, timeout: Duration) -> Result<Option<Vec<u8>>, IpcError>;
    fn close(&mut self) -> Result<(), IpcError>;
}

#[cfg(windows)]
fn create_channel(pipe_name: &str, timeout: Duration) -> Result<Box<dyn ChannelImpl>, IpcError> {
    Ok(Box::new(WindowsPipeChannel::connect(pipe_name, timeout)?))
}

#[cfg(windows)]
struct WindowsPipeChannel {
    handle: windows_sys::Win32::Foundation::HANDLE,
}

#[cfg(windows)]
impl WindowsPipeChannel {
    fn connect(pipe_name: &str, timeout: Duration) -> Result<Self, IpcError> {
        use windows_sys::Win32::Foundation::{
            GetLastError, INVALID_HANDLE_VALUE,
            GENERIC_READ, GENERIC_WRITE,
            ERROR_ACCESS_DENIED, ERROR_BAD_NETPATH, ERROR_FILE_NOT_FOUND, ERROR_PIPE_BUSY,
        };
        use windows_sys::Win32::Storage::FileSystem::{CreateFileW, OPEN_EXISTING};
        use windows_sys::Win32::System::Threading::Sleep;

        let wide: Vec<u16> = pipe_name.encode_utf16().chain(std::iter::once(0)).collect();
        let deadline = std::time::Instant::now() + timeout;

        loop {
            let remaining = match deadline.checked_duration_since(std::time::Instant::now()) {
                Some(d) => d,
                None => {
                    return Err(format!("IPC connect timeout: {pipe_name}").into());
                }
            };

            let h = unsafe {
                CreateFileW(
                    wide.as_ptr(),
                    GENERIC_READ | GENERIC_WRITE,
                    0,
                    std::ptr::null_mut(),
                    OPEN_EXISTING,
                    0,
                    std::ptr::null_mut(),
                )
            };

            if h != INVALID_HANDLE_VALUE {
                return Ok(Self { handle: h });
            }

            let err = unsafe { GetLastError() };
            match err {
                ERROR_PIPE_BUSY | ERROR_FILE_NOT_FOUND | ERROR_BAD_NETPATH => {
                    let sleep_ms = remaining.as_millis().min(100) as u32;
                    if sleep_ms == 0 {
                        return Err(format!("IPC connect timeout: {pipe_name} (err={err})").into());
                    }
                    unsafe { Sleep(sleep_ms) };
                    continue;
                }
                ERROR_ACCESS_DENIED => {
                    return Err(format!("IPC access denied: {pipe_name}").into());
                }
                _ => {
                    return Err(format!("IPC CreateFileW failed error={err}: {pipe_name}").into());
                }
            }
        }
    }

    fn read_exact(&mut self, buf: &mut [u8], timeout: Duration) -> Result<bool, IpcError> {
        use windows_sys::Win32::Foundation::GetLastError;
        use windows_sys::Win32::Storage::FileSystem::ReadFile;
        use windows_sys::Win32::System::Threading::Sleep;

        let deadline = std::time::Instant::now() + timeout;
        let mut read_total = 0usize;

        loop {
            let remaining = match deadline.checked_duration_since(std::time::Instant::now()) {
                Some(d) => d,
                None => return Ok(false),
            };

            let mut got: u32 = 0;
            let ok = unsafe {
                ReadFile(
                    self.handle,
                    buf[read_total..].as_mut_ptr(),
                    (buf.len() - read_total) as u32,
                    &mut got,
                    std::ptr::null_mut(),
                )
            };

            if ok != 0 {
                read_total += got as usize;
                if read_total == buf.len() {
                    return Ok(true);
                }
                if got == 0 {
                    return Ok(false);
                }
                continue;
            }

            let err = unsafe { GetLastError() };
            if err == windows_sys::Win32::Foundation::ERROR_BROKEN_PIPE {
                return Ok(false);
            }
            if err == windows_sys::Win32::Foundation::ERROR_NO_DATA {
                let sleep_ms = remaining.as_millis().min(10) as u32;
                if sleep_ms == 0 {
                    return Ok(false);
                }
                unsafe { Sleep(sleep_ms) };
                continue;
            }

            return Err(format!("ReadFile failed error={err}").into());
        }
    }
}

#[cfg(windows)]
impl ChannelImpl for WindowsPipeChannel {
    fn write_all(&mut self, buf: &[u8]) -> Result<(), IpcError> {
        use windows_sys::Win32::Foundation::GetLastError;
        use windows_sys::Win32::Storage::FileSystem::WriteFile;

        let mut written: u32 = 0;
        let ok = unsafe {
            WriteFile(
                self.handle,
                buf.as_ptr(),
                buf.len() as u32,
                &mut written,
                std::ptr::null_mut(),
            )
        };
        if ok == 0 || written != buf.len() as u32 {
            let err = unsafe { GetLastError() };
            return Err(format!("WriteFile failed error={err}").into());
        }
        Ok(())
    }

    fn read_frame(&mut self, timeout: Duration) -> Result<Option<Vec<u8>>, IpcError> {
        let mut len_buf = [0u8; 4];
        if !self.read_exact(&mut len_buf, timeout)? {
            return Ok(None);
        }
        let len = u32::from_le_bytes(len_buf) as usize;
        if len > 16 * 1024 * 1024 {
            return Err(format!("IPC frame too large: {len} bytes").into());
        }
        let mut payload = vec![0u8; len];
        if !self.read_exact(&mut payload, timeout)? {
            return Ok(None);
        }
        Ok(Some(payload))
    }

    fn close(&mut self) -> Result<(), IpcError> {
        use windows_sys::Win32::Foundation::{CloseHandle, GetLastError, INVALID_HANDLE_VALUE};
        unsafe {
            if self.handle != INVALID_HANDLE_VALUE {
                let ok = CloseHandle(self.handle);
                self.handle = INVALID_HANDLE_VALUE;
                if ok == 0 {
                    return Err(format!("CloseHandle failed error={}", GetLastError()).into());
                }
            }
        }
        Ok(())
    }
}

#[cfg(windows)]
unsafe impl Send for WindowsPipeChannel {}

#[cfg(not(windows))]
fn create_channel(pipe_name: &str, _timeout: Duration) -> Result<Box<dyn ChannelImpl>, IpcError> {
    Ok(Box::new(UnixFallbackChannel::connect(pipe_name)?))
}

#[cfg(not(windows))]
struct UnixFallbackChannel {
    sock: std::os::unix::net::UnixStream,
}

#[cfg(not(windows))]
impl UnixFallbackChannel {
    fn connect(pipe_name: &str) -> Result<Self, IpcError> {
        let sock_path = format!(
            "/tmp/esplus-ac-{}.sock",
            pipe_name.replace('\\', "_").replace('/', "_")
        );
        let sock = std::os::unix::net::UnixStream::connect(&sock_path)?;
        Ok(Self { sock })
    }
}

#[cfg(not(windows))]
impl ChannelImpl for UnixFallbackChannel {
    fn write_all(&mut self, buf: &[u8]) -> Result<(), IpcError> {
        use std::io::Write;
        self.sock.write_all(buf)?;
        self.sock.flush()?;
        Ok(())
    }

    fn read_frame(&mut self, timeout: Duration) -> Result<Option<Vec<u8>>, IpcError> {
        use std::io::Read;
        self.sock.set_read_timeout(Some(timeout))?;
        let mut len_buf = [0u8; 4];
        match self.sock.read_exact(&mut len_buf) {
            Ok(()) => {}
            Err(e)
                if e.kind() == std::io::ErrorKind::WouldBlock
                    || e.kind() == std::io::ErrorKind::TimedOut
                    || e.kind() == std::io::ErrorKind::UnexpectedEof =>
            {
                return Ok(None);
            }
            Err(e) => return Err(e.into()),
        }
        let len = u32::from_le_bytes(len_buf) as usize;
        if len > 16 * 1024 * 1024 {
            return Err(format!("IPC frame too large: {len} bytes").into());
        }
        let mut payload = vec![0u8; len];
        self.sock.set_read_timeout(Some(timeout))?;
        match self.sock.read_exact(&mut payload) {
            Ok(()) => Ok(Some(payload)),
            Err(e)
                if e.kind() == std::io::ErrorKind::WouldBlock
                    || e.kind() == std::io::ErrorKind::TimedOut
                    || e.kind() == std::io::ErrorKind::UnexpectedEof =>
            {
                Ok(None)
            }
            Err(e) => Err(e.into()),
        }
    }

    fn close(&mut self) -> Result<(), IpcError> {
        let _ = self.sock.shutdown(std::net::Shutdown::Both);
        Ok(())
    }
}
