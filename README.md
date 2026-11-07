# DiceSeed

DiceSeed generates BIP39 seed phrases from physical dice rolls only — no
electronic randomness ever contributes to the seed, so the result can be
independently verified by re-entering the same rolls on another device.

Two implementations share one spec (`docs/requirements.md`) and one design
rationale (`docs/design-notes.md`):

| App | Directory | Platform | Artifact |
| --- | --- | --- | --- |
| Prime | `prime/` | Foundation Passport Prime (KeyOS, Rust + Slint) | `releases/prime/diceseed.app` |
| Droid | `droid/` | Android 8.0+ (Kotlin + Jetpack Compose) | `releases/droid/diceseed.apk` |

## What the app does

1. **Launch** — steampunk dice banner; Get started.
2. **Seed Length** — 12 words (128 bits) or 24 words (256 bits).
3. **Your Dice** — 6-sided or 8-sided dice.
4. **Dice Quality** — how much to compensate for imperfect dice:
   - *I trust them (1x)* — straight bit-mapping (d8: 3 bits/roll; d6: faces
     1-4 = 2 bits, 5-6 = 1 bit).
   - *Maybe biased (~1.5x)* — hash conditioning: rolls are collected with a
     1.5x entropy margin and the seed entropy is SHA-256 of the full roll
     transcript.
   - *Very sus (2x)* — modular sum: pairs of rolls combined as
     (a + b) mod faces before bit-mapping.
   - *Maybe loaded (~4-7x)* — von Neumann pairwise comparison: a < b emits 0,
     a > b emits 1, ties are discarded. Unbiased for any fixed face bias.
5. **Roll the Dice** — tap the face you rolled; progress bars track total
   bits and the word being built; finished words appear as you go (except in
   hash mode, where words appear at completion); undo the last roll with the
   arrow button; backing out with rolls at stake asks for confirmation.
6. **Your Seed Words** — the words in two columns (paged for 24); write them
   down.
7. **Verify** — every word confirmed once, in random order, from 6
   candidates; wrong taps turn red.

The BIP39 checksum is computed per the standard (SHA-256 over the entropy);
in the bit-extraction modes the dice supply the non-checksum bits of the
final word. The device RNG is used only to shuffle verification candidates —
never for seed entropy.

## Repository layout

```
docs/         Requirements and design notes (both apps)
prime/        Passport Prime app (Rust + Slint on KeyOS)
droid/        Android app (Kotlin + Jetpack Compose)
screenshots/  One PNG per screen/state
  prime/        from the KeyOS simulator
  droid/        rendered by the ScreenshotWalk test (Robolectric)
releases/     Built artifacts + sha256 files (make prime / make droid)
Makefile      Build entry points
repo-admin.sh The GitHub repository's settings (Repository settings, below)
```

## Building

```
make prime   # Foundation Prime .app  -> releases/prime/ (+ .sha256)
make droid   # Android .apk           -> releases/droid/ (+ .sha256)
make all     # both
```

Both build in containers, so the host needs Docker rather than Nix or a JDK: `make prime` in
`nixos/nix` (the SDK's Nix shell), `make droid` in `eclipse-temurin:17-jdk`, each pinned by digest
in the `Makefile`. The Nix store and the Gradle cache stay in Docker volumes (`seedkata-nix`,
`seedkata-home`, `seedkata-gradle`), so a rebuild is fast; `docker volume rm` them to start over.

### Prime prerequisites

- Foundation SDK (`curl -sSfL https://sdk.foundation.xyz/latest/install.sh | bash`), in
  `~/.foundation`; the build mounts it read-only into the Nix container (Nix itself comes with
  the container).
- A publisher signing identity (`foundation cert gen`); the packed `.app`
  embeds it. Install on device: Settings > Apps > Install App from a USB
  drive, then confirm the publisher fingerprint.
- Development loop: `cd prime && foundation sim` (simulator), or
  `foundation sideload` with a USB-connected device in Developer Mode.

### Droid prerequisites

- Android SDK (`ANDROID_HOME`, `~/android-sdk` unless set; platform 34 and build tools 34),
  mounted read-only into the JDK 17 container. Gradle comes with the wrapper.
- The release APK is signed with the local keystore configured in
  `droid/keystore.properties` (not committed).
- Install: `adb install releases/droid/diceseed.apk`.

## Verifying a release

```
cd releases/prime && sha256sum -c diceseed.app.sha256
cd releases/droid && sha256sum -c diceseed.apk.sha256
```

Prime publisher fingerprint (confirm on device at install):
`02b1ee1496c5fe753ed01dfc4411cf104b876012bec4e9711be4329b9d892ade`

## Repository settings

`repo-admin.sh check` reads this repository's GitHub settings back (exit 1 when one differs); `repo-admin.sh
apply`, run by an admin with `gh` logged in, sets them: a ruleset on `master` (a pull request to
merge, squash only, no force push or deletion), squash merging only with the branch deleted once merged, the Actions token
read-only, actions pinned by SHA, fork pull requests' runs approved first, and private vulnerability
reporting. Each MojoKata repository has the same script; only its settings block differs.
