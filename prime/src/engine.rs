// Dice-roll entropy engine.
//
// Three extraction modes, selected on the Dice Quality screen:
//
// - Trust ("I trust them", 1x): straight bit-mapping, assumes fair dice.
//   d8: each roll yields exactly 3 bits (value - 1).
//   d6: rolls 1-4 yield 2 bits (value - 1), rolls 5-6 yield 1 bit (value - 5).
//
// - Hash ("Maybe biased", ~1.5x): hash conditioning. Rolls are bit-mapped
//   only to *count* collected raw entropy; once raw entropy reaches 1.5x the
//   target, the seed entropy is SHA-256 over the full roll transcript
//   (truncated to the target size). Tolerates modest bias and mild
//   correlation, at the cost of the words only being known once every roll
//   is in.
//
// - ModSum ("Very sus", 2x): each pair of rolls is combined as
//   (a + b) mod faces before the fair-dice bit-mapping. Face bias decays
//   quadratically in the combined value, at exactly twice the rolls.
//
// - Pairwise ("Maybe loaded", ~4-7x): a von Neumann style extractor over
//   consecutive roll pairs. For each pair (a, b): a < b emits 0, a > b emits
//   1, a == b emits nothing. As long as rolls are independent this produces
//   unbiased bits from a die with any fixed face bias, at the cost of many
//   more rolls.

use crate::sha256::sha256;

#[derive(Clone, Copy, PartialEq, Debug)]
pub enum Mode {
    Trust,
    Hash,
    ModSum,
    Pairwise,
}

/// Hash mode collects this numerator/denominator times the target entropy
/// in raw (bit-mapped) dice entropy before hashing: 3/2 = ~1.5x.
const HASH_MARGIN_NUM: usize = 3;
const HASH_MARGIN_DEN: usize = 2;

pub struct Engine {
    pub sides: u8,
    pub mode: Mode,
    ent_bits: usize,
    /// Trust/Pairwise: extracted entropy bits.
    bits: Vec<u8>,
    /// Hash: raw bit-mapped entropy collected so far.
    raw_bits: usize,
    /// Hash: every roll value, hashed at completion.
    transcript: Vec<u8>,
    /// Pairwise/ModSum: first roll of the current pair.
    pending: Option<u8>,
    /// Every accepted roll, so the last one can be undone.
    roll_history: Vec<u8>,
    pub rolls: u32,
}

impl Engine {
    pub fn new(word_count: usize, sides: u8, mode: Mode) -> Self {
        let ent_bits = if word_count == 24 { 256 } else { 128 };
        Engine {
            sides,
            mode,
            ent_bits,
            bits: Vec::with_capacity(ent_bits),
            raw_bits: 0,
            transcript: Vec::new(),
            pending: None,
            roll_history: Vec::new(),
            rolls: 0,
        }
    }

    pub fn word_total(&self) -> usize {
        (self.ent_bits + self.ent_bits / 32) / 11
    }

    pub fn checksum_bits(&self) -> usize {
        self.ent_bits / 32
    }

    fn raw_bits_needed(&self) -> usize {
        self.ent_bits * HASH_MARGIN_NUM / HASH_MARGIN_DEN
    }

    pub fn is_complete(&self) -> bool {
        match self.mode {
            Mode::Hash => self.raw_bits >= self.raw_bits_needed(),
            _ => self.bits.len() >= self.ent_bits,
        }
    }

    /// Bit-mapped entropy of one roll under the fair-dice mapping.
    fn mapped_bit_count(&self, value: u8) -> usize {
        if self.sides == 8 {
            3
        } else if value <= 4 {
            2
        } else {
            1
        }
    }

    /// Feed one physical roll (face value, 1-based). Ignored once complete.
    pub fn roll(&mut self, value: u8) {
        if self.is_complete() || value < 1 || value > self.sides {
            return;
        }
        self.roll_history.push(value);
        self.process(value);
    }

    /// Roll back the last dice roll: the state is rebuilt by replaying the
    /// remaining roll history, so it works uniformly in every mode.
    pub fn undo(&mut self) {
        if self.roll_history.pop().is_none() {
            return;
        }
        let history = std::mem::take(&mut self.roll_history);
        self.bits.clear();
        self.raw_bits = 0;
        self.transcript.clear();
        self.pending = None;
        self.rolls = 0;
        for &v in &history {
            self.process(v);
        }
        self.roll_history = history;
    }

    fn process(&mut self, value: u8) {
        self.rolls += 1;

        match self.mode {
            Mode::Pairwise => match self.pending.take() {
                None => self.pending = Some(value),
                Some(prev) => {
                    if prev != value {
                        self.push_bit(u8::from(prev > value));
                    }
                }
            },
            Mode::Hash => {
                self.transcript.push(value);
                self.raw_bits += self.mapped_bit_count(value);
            }
            Mode::ModSum => match self.pending.take() {
                None => self.pending = Some(value),
                Some(prev) => {
                    let combined = ((prev - 1) + (value - 1)) % self.sides + 1;
                    self.push_mapped(combined);
                }
            },
            Mode::Trust => self.push_mapped(value),
        }
    }

    /// Push one die value through the fair-dice bit-mapping.
    fn push_mapped(&mut self, value: u8) {
        if self.sides == 8 {
            let v = value - 1;
            self.push_bit((v >> 2) & 1);
            self.push_bit((v >> 1) & 1);
            self.push_bit(v & 1);
        } else if value <= 4 {
            let v = value - 1;
            self.push_bit((v >> 1) & 1);
            self.push_bit(v & 1);
        } else {
            self.push_bit(value - 5);
        }
    }

    fn push_bit(&mut self, bit: u8) {
        if self.bits.len() < self.ent_bits {
            self.bits.push(bit);
        }
    }

    /// Rolls per bit-mapped raw bit for this die (fair-dice mapping).
    fn rolls_per_mapped_bit(&self) -> f64 {
        if self.sides == 8 {
            1.0 / 3.0
        } else {
            3.0 / 5.0 // d6 fair: E[bits/roll] = 4/6*2 + 2/6*1 = 5/3
        }
    }

    /// Total rolls this session is estimated to need, given progress so far.
    pub fn estimated_total_rolls(&self) -> u32 {
        let (remaining, rolls_per_bit) = match self.mode {
            Mode::Hash => (
                (self.raw_bits_needed() - self.raw_bits) as f64,
                self.rolls_per_mapped_bit(),
            ),
            Mode::Trust => (
                (self.ent_bits - self.bits.len()) as f64,
                self.rolls_per_mapped_bit(),
            ),
            Mode::ModSum => (
                (self.ent_bits - self.bits.len()) as f64,
                self.rolls_per_mapped_bit() * 2.0,
            ),
            Mode::Pairwise => (
                (self.ent_bits - self.bits.len()) as f64,
                if self.sides == 8 { 16.0 / 7.0 } else { 12.0 / 5.0 },
            ),
        };
        self.rolls + (remaining * rolls_per_bit).ceil() as u32
    }

    /// Overall progress through entropy collection, 0..1.
    fn overall_progress(&self) -> f32 {
        self.bits_collected() as f32 / self.bits_target() as f32
    }

    /// Entropy bits collected so far (raw bits in Hash mode).
    pub fn bits_collected(&self) -> usize {
        match self.mode {
            Mode::Hash => self.raw_bits.min(self.raw_bits_needed()),
            _ => self.bits.len(),
        }
    }

    /// Bits needed to finish (the 1.5x raw target in Hash mode).
    pub fn bits_target(&self) -> usize {
        match self.mode {
            Mode::Hash => self.raw_bits_needed(),
            _ => self.ent_bits,
        }
    }

    /// Number of fully-determined words so far (all words once complete).
    /// In Hash mode no word is determined before completion: the entropy is
    /// the hash of every roll, so words appear only at the end.
    pub fn words_done(&self) -> usize {
        if self.is_complete() {
            self.word_total()
        } else if self.mode == Mode::Hash {
            0
        } else {
            (self.bits.len() / 11).min(self.word_total() - 1)
        }
    }

    /// 1-based number of the word currently being rolled for the UI.
    pub fn current_word_number(&self) -> usize {
        if self.is_complete() {
            return self.word_total();
        }
        match self.mode {
            Mode::Hash => {
                let idx = (self.overall_progress() * self.word_total() as f32) as usize;
                idx.min(self.word_total() - 1) + 1
            }
            _ => self.words_done() + 1,
        }
    }

    /// Progress (0..1) through the word currently being rolled. In the
    /// bit-extraction modes the final word only needs its non-checksum bits
    /// from dice.
    pub fn current_word_progress(&self) -> f32 {
        if self.is_complete() {
            return 1.0;
        }
        match self.mode {
            Mode::Hash => {
                let scaled = self.overall_progress() * self.word_total() as f32;
                scaled - (self.current_word_number() - 1) as f32
            }
            _ => {
                let done = self.words_done();
                let have = self.bits.len() - done * 11;
                let need = if done == self.word_total() - 1 {
                    11 - self.checksum_bits()
                } else {
                    11
                };
                have as f32 / need as f32
            }
        }
    }

    /// Entropy bytes; only meaningful once complete.
    pub fn entropy(&self) -> Vec<u8> {
        if self.mode == Mode::Hash {
            return sha256(&self.transcript)[..self.ent_bits / 8].to_vec();
        }
        self.bits
            .chunks(8)
            .map(|c| c.iter().fold(0u8, |acc, &b| (acc << 1) | b))
            .collect()
    }

    /// Word indices for every fully-determined word. Before completion the
    /// final (checksum-dependent) word is never included; in Hash mode no
    /// word is included before completion.
    pub fn word_indices(&self) -> Vec<u16> {
        if self.is_complete() {
            return crate::bip39::mnemonic_indices(&self.entropy());
        }
        if self.mode == Mode::Hash {
            return Vec::new();
        }
        (0..self.words_done())
            .map(|w| {
                (0..11).fold(0u16, |acc, b| (acc << 1) | self.bits[w * 11 + b] as u16)
            })
            .collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn d8_trust_all_ones_gives_zero_entropy() {
        let mut e = Engine::new(12, 8, Mode::Trust);
        while !e.is_complete() {
            e.roll(1);
        }
        assert_eq!(e.rolls, 43);
        assert_eq!(e.entropy(), vec![0u8; 16]);
        let idx = e.word_indices();
        assert_eq!(idx.len(), 12);
        assert_eq!(idx[11], 3); // "about" — matches the BIP39 test vector
    }

    #[test]
    fn pairwise_mode_discards_ties() {
        let mut e = Engine::new(12, 6, Mode::Pairwise);
        e.roll(4);
        e.roll(4); // tie: no bit
        assert_eq!(e.word_indices().len(), 0);
        e.roll(2);
        e.roll(5); // 2 < 5 -> bit 0
        assert_eq!(e.rolls, 4);
    }

    #[test]
    fn d6_trust_counts() {
        let mut e = Engine::new(12, 6, Mode::Trust);
        e.roll(1); // 2 bits
        e.roll(6); // 1 bit
        assert_eq!(e.current_word_progress(), 3.0 / 11.0);
    }

    #[test]
    fn hash_mode_words_only_at_completion() {
        let mut e = Engine::new(12, 6, Mode::Hash);
        // d6 raw target: 128 * 3/2 = 192 bits; rolling 1s gives 2 bits each.
        for _ in 0..95 {
            e.roll(1);
        }
        assert!(!e.is_complete());
        assert_eq!(e.words_done(), 0);
        assert_eq!(e.word_indices().len(), 0);
        e.roll(1);
        assert!(e.is_complete());
        assert_eq!(e.rolls, 96);

        // Entropy is the truncated SHA-256 of the transcript.
        let expected = &sha256(&vec![1u8; 96])[..16];
        assert_eq!(e.entropy(), expected);
        assert_eq!(e.word_indices().len(), 12);
    }

    #[test]
    fn undo_rolls_back_one_roll_in_every_mode() {
        for mode in [Mode::Trust, Mode::Hash, Mode::ModSum, Mode::Pairwise] {
            let mut e = Engine::new(12, 6, mode);
            e.roll(3);
            e.roll(5);
            let progress_after_two = e.current_word_progress();
            let rolls_after_two = e.rolls;
            e.roll(2);
            e.undo();
            assert_eq!(e.rolls, rolls_after_two, "mode {mode:?}");
            assert_eq!(e.current_word_progress(), progress_after_two, "mode {mode:?}");
        }

        // Undoing everything returns to a clean slate.
        let mut e = Engine::new(12, 6, Mode::Trust);
        e.roll(4);
        e.undo();
        e.undo(); // extra undo is a no-op
        assert_eq!(e.rolls, 0);
        assert_eq!(e.current_word_progress(), 0.0);
    }

    #[test]
    fn undo_reverts_completion() {
        let mut e = Engine::new(12, 8, Mode::Trust);
        while !e.is_complete() {
            e.roll(1);
        }
        e.undo();
        assert!(!e.is_complete());
        assert_eq!(e.rolls, 42);
    }

    #[test]
    fn modsum_combines_pairs() {
        let mut e = Engine::new(12, 6, Mode::ModSum);
        e.roll(5);
        assert_eq!(e.current_word_progress(), 0.0); // pending, no bits yet
        e.roll(4); // (4 + 3) % 6 = 1 -> face 2 -> bits 0,1
        assert_eq!(e.rolls, 2);
        assert_eq!(e.current_word_progress(), 2.0 / 11.0);

        // Exactly 2x the trust-mode estimate from a fresh engine.
        let fresh = Engine::new(12, 6, Mode::ModSum);
        assert_eq!(fresh.estimated_total_rolls(), 154);
    }

    #[test]
    fn hash_mode_estimate_has_margin() {
        let e = Engine::new(12, 6, Mode::Hash);
        // ceil(192 * 3/5) = 116 rolls: ~1.5x the 77 rolls of Trust mode.
        assert_eq!(e.estimated_total_rolls(), 116);
        let t = Engine::new(12, 6, Mode::Trust);
        assert_eq!(t.estimated_total_rolls(), 77);
    }

    #[test]
    fn bits_progress_counts_per_mode() {
        let mut t = Engine::new(12, 6, Mode::Trust);
        assert_eq!((t.bits_collected(), t.bits_target()), (0, 128));
        t.roll(1); // 2 bits
        assert_eq!(t.bits_collected(), 2);

        let h = Engine::new(12, 6, Mode::Hash);
        assert_eq!(h.bits_target(), 192);

        let p = Engine::new(24, 8, Mode::Pairwise);
        assert_eq!(p.bits_target(), 256);
    }
}
