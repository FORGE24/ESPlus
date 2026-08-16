#[derive(Debug, Clone)]
pub struct EntropySample {
    pub bytes: Vec<u8>,
    pub shannon_entropy: f64,
    pub kolmogorov_complexity_proxy: f64,
    pub zlib_ratio: f64,
    pub classified: EntropyClass,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum EntropyClass {
    Plain,
    Structured,
    Random,
    Encrypted,
}

pub fn shannon_entropy(data: &[u8]) -> f64 {
    if data.is_empty() {
        return 0.0;
    }
    let mut counts = [0u32; 256];
    for &b in data {
        counts[b as usize] += 1;
    }
    let len = data.len() as f64;
    let mut h = 0.0;
    for c in counts {
        if c == 0 {
            continue;
        }
        let p = c as f64 / len;
        h -= p * p.log2();
    }
    h
}

pub fn kolmogorov_proxy(data: &[u8]) -> f64 {
    if data.len() < 4 {
        return 0.0;
    }
    let mut runs = 1u32;
    for i in 1..data.len() {
        if data[i] != data[i - 1] {
            runs += 1;
        }
    }
    let run_density = runs as f64 / data.len() as f64;
    let uniq = {
        let mut seen = [false; 256];
        let mut n = 0u32;
        for &b in data {
            if !seen[b as usize] {
                seen[b as usize] = true;
                n += 1;
            }
        }
        n
    };
    let divers = uniq as f64 / 256.0;
    run_density * 0.5 + divers * 0.5
}

#[cfg(not(windows))]
mod zlib_fallback {
    pub fn zlib_ratio(_data: &[u8]) -> f64 { 1.0 }
}

#[cfg(windows)]
fn zlib_ratio(_data: &[u8]) -> f64 {
    1.0
}

pub fn classify(sample: &EntropySample) -> EntropyClass {
    let e = sample.shannon_entropy;
    if e < 1.5 {
        return EntropyClass::Plain;
    }
    if e < 4.0 {
        return EntropyClass::Structured;
    }
    if e < 6.5 {
        return EntropyClass::Random;
    }
    EntropyClass::Encrypted
}

pub fn analyze(data: &[u8]) -> EntropySample {
    let shannon = shannon_entropy(data);
    let kproxy = kolmogorov_proxy(data);
    let mut s = EntropySample {
        bytes: data.to_vec(),
        shannon_entropy: shannon,
        kolmogorov_complexity_proxy: kproxy,
        zlib_ratio: 1.0,
        classified: EntropyClass::Plain,
    };
    s.classified = classify(&s);
    s
}

pub fn sliding_window_entropy(buffer: &[u8], window: usize, step: usize) -> Vec<EntropySample> {
    let mut out = Vec::new();
    if buffer.len() < window {
        return out;
    }
    let mut i = 0;
    while i + window <= buffer.len() {
        out.push(analyze(&buffer[i..i + window]));
        i += step.max(1);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_constant_bytes_have_zero_entropy() {
        let data = vec![0u8; 100];
        assert!(shannon_entropy(&data) < 0.01);
    }

    #[test]
    fn test_random_bytes_have_high_entropy() {
        use rand::RngCore;
        let mut data = vec![0u8; 1024];
        rand::thread_rng().fill_bytes(&mut data);
        let e = shannon_entropy(&data);
        assert!(e > 7.5, "got entropy={e}");
    }

    #[test]
    fn test_classification() {
        let plain = vec![b'A'; 200];
        assert_eq!(analyze(&plain).classified, EntropyClass::Plain);
    }
}
