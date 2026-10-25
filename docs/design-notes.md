# DiceSeed Design Notes

## Backwards navigation (requirement 1.8)

Every screen past the launch screen supports backwards navigation through the
KeyOS platform back gesture: swipe right from the left edge of the screen. The
gesture is implemented by the `slint-keyos-platform` runtime for all routed
pages (see `SWIPE_RIGHT_EDGE_AREA_WIDTH_PX` handling in the runtime's
`platform.rs`) and pops the router history exactly like `Navigate.backward()`.

A header back button was intentionally not used: the KeyOS control-center
overlay owns the touch zone at the top of the screen (pull down for the
control center), so taps on controls placed there never reach the app. This
was verified empirically in the simulator.

Exception — the Dice Rolling screen: the device back button and the back
gesture both land in the router's backward navigation, which offers no veto
hook. The app registers an `on_navigation_end` hook: when the rolling route is
left with rolls at stake and without authorization (Continue pressed or
discard confirmed), it immediately re-pushes the rolling route and opens the
"Cancel dice rolling?" confirmation. Confirming discards the session and
navigates back; declining stays on the screen with every roll intact.

## Entropy extraction

- ENT = 128 bits (12 words) or 256 bits (24 words); CS = ENT/32 checksum bits
  from SHA-256 of the entropy (BIP39). In the bit-extraction modes the final
  word's non-checksum bits come from dice; the checksum completes it
  (requirement 2.9.1).
- "I trust them" (1x): fair-dice bit-mapping. d8: 3 bits per roll. d6: faces
  1-4 give 2 bits, faces 5-6 give 1 bit (expected 5/3 bits per roll).
- "Maybe biased" (~1.5x): hash conditioning. Rolls are collected until the
  bit-mapped raw entropy reaches 1.5x the target, then the seed entropy is
  SHA-256 over the full roll transcript, truncated to ENT bits. Tolerates
  modest bias and mild correlation (computational guarantee via the hash).
  Because the entropy is a digest of every roll, no word is determined before
  rolling completes: the progress bar sweeps through the words, and the words
  appear when the last roll is in.
- "Very sus" (2x): modular sum — each pair of rolls is combined as
  (a + b) mod faces before the fair-dice bit-mapping. Face bias decays
  quadratically (attenuated, not eliminated), at exactly twice the rolls.
- "Maybe loaded" (~4-7x): a von Neumann style extractor over consecutive roll
  pairs — a < b emits 0, a > b emits 1, ties emit nothing. Information-
  theoretically unbiased for any fixed face bias as long as rolls are
  independent. Expected rolls per bit: d6 = 2.4 (~4x), d8 = 16/7 (~7x).
- The roll counter shows "Roll N of M (estimated)"; M is recomputed from the
  measured progress so it converges as rolls come in.

## Verification

Words are verified once each, in random (Fisher-Yates, TRNG-backed) order.
Each step shows 6 candidates (2 columns x 3): the correct word plus 5 distinct
random decoys, with the correct word at a random position. Wrong taps turn the
tapped button red (Button `danger` variant); the correct tap advances.

## Randomness sources

- Seed entropy: dice rolls only — never the system RNG.
- UI shuffling (verify order, decoys, candidate position): `getrandom`, which
  the SDK routes to the device TRNG on hardware.
