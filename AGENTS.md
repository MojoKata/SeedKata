<!--
SPDX-FileCopyrightText: 2026 Foundation Devices, Inc. <hello@foundation.xyz>
SPDX-License-Identifier: MIT
-->

# DiceSeed Agent Guide

This repo holds two implementations of DiceSeed sharing `docs/requirements.md`:
the Foundation Prime app in `prime/` (everything below refers to it; run
`foundation` commands from `prime/`) and the Android app in `droid/`
(Kotlin + Compose; built with Gradle, see the root Makefile).

The `prime/` directory is a Foundation SDK app. The SDK ships its own guide, and it is the authority on both the CLI and on
what each SDK release changed. Read it before running `foundation` commands or porting this app to a newer
SDK. `foundation doctor` prints the SDK root used as `<sdk-root>` below.

- CLI reference: `<sdk-root>/docs/guide/src/foundation-cli.md`, or `sdk/docs/foundation-cli.md` in a KeyOS
  source checkout.
- Migration guide: `<sdk-root>/docs/guide/src/MIGRATIONS.md`, or `sdk/docs/MIGRATIONS.md` in a KeyOS source
  checkout.

Prefer `foundation preview` for UI checks, `foundation sim` for hosted runtime checks, and `foundation build`
only when signed hardware artifacts are needed. Do not run `foundation sideload`, `foundation logs`, or
`foundation cert gen` unless the user explicitly asks for hardware or signing work.

## Skills

`.claude/skills/` and `.agents/skills/` hold the skills the SDK ships: `/foundation-cli`,
`/foundation-localize`, `/foundation-new-page`, and `/foundation-sdk-migrate-newest`. They are copies of the
SDK's own files, so run `foundation skills` to refresh them after an SDK upgrade.

## Driving a real device (passport-drive MCP)

This app ships a `.mcp.json` that registers the `passport-drive` MCP server. With a Passport Prime connected
over USB, its tools let you act on the real device: capture screenshots, inject taps and swipes, and read its
logs. The device only exposes this debug channel when Developer Mode is enabled (in the device's Settings, under
Apps > Developer Mode); with it off, the connection is unavailable. On Linux, USB access also requires the
udev rules shipped at `<sdk-root>/share/99-passport.rules`; a permission error opening the device means they
are not installed (copy the file to `/etc/udev/rules.d/` and reload udev). Only drive hardware when the user
asks for on-device work.

## Large downloads and shared VMs

Installer ISOs and other large downloads, and VMs that projects share, follow the
[coworker protocol](https://github.com/MojoKata/coworker): its commands are installed in
`~/coworker/bin` by that repository's `install.sh` (`COWORKER_HOME` moves the whole folder). A
download lives in `~/coworker/downloads` beside its SHA-256; a project that needs one claims it
under its own name (here `diceseed`), which leaves an empty `<file>.diceseed`, and it goes only with
the last claim:

    ~/coworker/bin/downloads fetch FILE diceseed URL SHA_URL    # fetches (resuming), checks, claims
    ~/coworker/bin/downloads claim FILE diceseed
    ~/coworker/bin/downloads release FILE diceseed
    ~/coworker/bin/downloads list [--verify]

Shared VMs are in `~/coworker/vms`, used one project at a time (`~/coworker/bin/vms lock`). Never
download into this repository's folders. DiceSeed claims no download and no VM today.
