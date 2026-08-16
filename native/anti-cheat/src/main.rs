use std::time::Duration;

use detector::DetectorEngine;
use hwid::HwidCollector;
use ipc::{IpcChannel, IpcMessage};

mod detector;
mod hwid;
mod ipc;
mod crypto;

const CLIENT_HANDSHAKE_TIMEOUT_MS: u64 = 15_000;

fn main() {
    let args: Vec<String> = std::env::args().collect();

    let mut minecraft_pid: Option<u32> = None;
    let mut pipe_name = String::from("\\\\.\\pipe\\esplus-ac-default");
    let mut shared_key = [0u8; 32];

    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--pid" if i + 1 < args.len() => {
                minecraft_pid = args[i + 1].parse().ok();
                i += 2;
            }
            "--pipe" if i + 1 < args.len() => {
                pipe_name = args[i + 1].clone();
                i += 2;
            }
            "--key" if i + 1 < args.len() => {
                let raw = &args[i + 1];
                shared_key = decode_key_arg(raw);
                i += 2;
            }
            "--hwid" => {
                let hwid = HwidCollector::collect_and_hash();
                println!("{}", hwid);
                return;
            }
            _ => {
                i += 1;
            }
        }
    }

    if minecraft_pid.is_none() {
        eprintln!("esplus-ac: --pid required (Minecraft client process ID)");
        std::process::exit(1);
    }

    let mut ipc = match IpcChannel::connect(&pipe_name, Duration::from_millis(CLIENT_HANDSHAKE_TIMEOUT_MS)) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("esplus-ac: IPC connect failed: {e}");
            std::process::exit(2);
        }
    };

    let mut engine = DetectorEngine::new(minecraft_pid.unwrap(), shared_key);

    if let Err(e) = ipc.send(&IpcMessage::HandshakeAck {
        version: env!("CARGO_PKG_VERSION").to_string(),
        pid: std::process::id(),
    }) {
        eprintln!("esplus-ac: handshake ack failed: {e}");
        return;
    }

    println!("esplus-ac v{} attached to pid {}", env!("CARGO_PKG_VERSION"), minecraft_pid.unwrap());

    loop {
        match ipc.recv_timeout(Duration::from_millis(500)) {
            Ok(Some(IpcMessage::Stop)) => {
                println!("esplus-ac: received Stop, shutting down");
                break;
            }
            Ok(Some(IpcMessage::Ping)) => {
                let _ = ipc.send(&IpcMessage::Pong { ts: now_ms() });
            }
            Ok(Some(IpcMessage::RunQuickScan)) => {
                let report = engine.run_quick_scan();
                let _ = ipc.send(&IpcMessage::ScanReport {
                    hwid: HwidCollector::collect_and_hash(),
                    report_json: serde_json::to_string(&report).unwrap_or_default(),
                });
            }
            Ok(Some(IpcMessage::RunDeepScan)) => {
                let report = engine.run_deep_scan();
                let _ = ipc.send(&IpcMessage::ScanReport {
                    hwid: HwidCollector::collect_and_hash(),
                    report_json: serde_json::to_string(&report).unwrap_or_default(),
                });
            }
            Ok(None) => {}
            Ok(Some(_)) => {}
            Err(e) => {
                eprintln!("esplus-ac: IPC error: {e}");
                break;
            }
        }

        let tick_reports = engine.tick();
        for report in tick_reports {
            let _ = ipc.send(&IpcMessage::ThreatAlert {
                rule: report.rule,
                severity: report.severity,
                detail: report.detail,
            });
        }

        std::thread::sleep(Duration::from_millis(250));
    }
}

fn now_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn decode_key_arg(s: &str) -> [u8; 32] {
    let mut out = [0u8; 32];
    let bytes: Vec<u8> = if let Ok(b) = base64::Engine::decode(
        &base64::engine::general_purpose::STANDARD,
        s,
    ) {
        b
    } else {
        s.as_bytes().to_vec()
    };
    let copy_len = bytes.len().min(32);
    out[..copy_len].copy_from_slice(&bytes[..copy_len]);
    out
}
