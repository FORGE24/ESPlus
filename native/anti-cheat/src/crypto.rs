use hmac::{Hmac, Mac};
use rand::RngCore;
use sha2::{Digest, Sha256};

type HmacSha256 = Hmac<Sha256>;

pub fn challenge_response(shared_key: &[u8; 32], nonce: &[u8], client_response: &[u8]) -> bool {
    let mut mac = match HmacSha256::new_from_slice(shared_key) {
        Ok(m) => m,
        Err(_) => return false,
    };
    mac.update(nonce);
    let expected = mac.finalize().into_bytes();
    if client_response.len() != expected.len() {
        return false;
    }
    constant_time_eq(&expected, client_response)
}

pub fn hmac_sign(shared_key: &[u8; 32], data: &[u8]) -> [u8; 32] {
    let mut mac = HmacSha256::new_from_slice(shared_key).expect("HMAC can take key of any size");
    mac.update(data);
    let result = mac.finalize().into_bytes();
    let mut out = [0u8; 32];
    out.copy_from_slice(&result);
    out
}

pub fn random_nonce_16() -> [u8; 16] {
    let mut nonce = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut nonce);
    nonce
}

pub fn integrity_hash_bytes(data: &[u8]) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(data);
    let result = hasher.finalize();
    let mut out = [0u8; 32];
    out.copy_from_slice(&result);
    out
}

pub fn integrity_hash_file(path: &str) -> Result<[u8; 32], String> {
    use std::fs::File;
    use std::io::Read;

    let mut file = File::open(path).map_err(|e| format!("open failed: {e}"))?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 8192];
    loop {
        let n = file.read(&mut buf).map_err(|e| format!("read failed: {e}"))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    let result = hasher.finalize();
    let mut out = [0u8; 32];
    out.copy_from_slice(&result);
    Ok(out)
}

fn constant_time_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for i in 0..a.len() {
        diff |= a[i] ^ b[i];
    }
    diff == 0
}
