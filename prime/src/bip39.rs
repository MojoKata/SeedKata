// BIP39 mnemonic encoding from raw entropy.

use crate::sha256::sha256;

pub const WORDS: &str = include_str!("bip39/english.txt");

pub fn wordlist() -> Vec<&'static str> {
    WORDS.lines().collect()
}

/// Encode entropy (16 or 32 bytes) into the BIP39 mnemonic word indices.
/// Returns one 11-bit index per word (12 words for 128 bits, 24 for 256).
pub fn mnemonic_indices(entropy: &[u8]) -> Vec<u16> {
    let ent_bits = entropy.len() * 8;
    let cs_bits = ent_bits / 32;
    let total_bits = ent_bits + cs_bits;

    let checksum = sha256(entropy)[0];

    // Bit stream: entropy bits MSB-first, then the top cs_bits of the checksum.
    let bit = |i: usize| -> u16 {
        if i < ent_bits {
            ((entropy[i / 8] >> (7 - (i % 8))) & 1) as u16
        } else {
            let j = i - ent_bits;
            ((checksum >> (7 - j)) & 1) as u16
        }
    };

    (0..total_bits / 11)
        .map(|w| (0..11).fold(0u16, |acc, b| (acc << 1) | bit(w * 11 + b)))
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn all_zero_128() {
        // Standard BIP39 test vector: 16 zero bytes ->
        // "abandon abandon ... about" (index 3 = "about" as the last word).
        let idx = mnemonic_indices(&[0u8; 16]);
        assert_eq!(idx.len(), 12);
        assert!(idx[..11].iter().all(|&i| i == 0));
        assert_eq!(idx[11], 3);
        assert_eq!(wordlist()[3], "about");
    }

    #[test]
    fn all_ff_256() {
        // Test vector: 32 0xff bytes end with "vote" (index 1964).
        let idx = mnemonic_indices(&[0xffu8; 32]);
        assert_eq!(idx.len(), 24);
        assert_eq!(wordlist()[idx[23] as usize], "vote");
    }
}
