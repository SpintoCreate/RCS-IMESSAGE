# Direct iMessage integration audit

This audit is for `feature/direct-imessage-rustpush`. It records source evidence, not a working iMessage connection. No registration request, Apple account login, or device pairing was attempted in the cloud environment.

The follow-up [activation-path audit](imessage-activation-path.md) separates compilation from runtime Apple registration. [Distribution requirements](imessage-distribution.md) are documented separately.

## Existing app

- The feature branch starts from `main` at `b4ce6e3`. The Google Messages spike and its local untracked files are preserved separately. The working tree had no tracked edits, and no `AGENTS.md` was present.
- `OpenBubblesProvider` is an inert `MessageProvider` in `DeferredProviders.kt`. The send router accepts SMS and Matrix only. Consequently, no iMessage route can send today.
- Room schema version 8 already has provider accounts, stable remote ID mappings, sync cursors, pending events, threads, messages, attachments and reactions. `MessageProvider` events and the reducer are shared by SMS and Matrix. The UI uses Kotlin/Compose; Hilt provides the database and active providers.
- Existing SMS/MMS and Matrix behavior must stay on their current providers. iMessage capability flags must remain false until each operation is backed by native code and device tests.

## Upstream source and licensing

| Component | Finding |
| --- | --- |
| [OpenBubbles app](https://github.com/OpenBubbles/openbubbles-app) | Apache-2.0 app fork of BlueBubbles. Inspected `eed1b6332efbb17adbf5ebfa2263ad770169f75e`. Its Android app uses Flutter/Dart for the main UI and a Rust crate built with Flutter Rust Bridge. |
| [Pinned rustpush](https://github.com/OpenBubbles/rustpush/tree/a7fab473e7a33325a760635285db2860de8e1cb0) | The app pins `a7fab473e7a33325a760635285db2860de8e1cb0`. `rustpush` is SSPL-1.0 with a special exception explicitly naming OpenBubbles; this repository is not named in that exception. Including it in a distributed APK requires a separate license review and compliance plan. A private local build is a different scope, but does not extend the exception to this project. |
| [StablePush](https://github.com/SpintoCreate/StablePush) | A TrollStore iPhone relay candidate based on ValidationRelay, not an Android iMessage client or replacement for rustpush. It can potentially serve the existing pairing-code protocol. Its own README states no genuine TIPA or physical registration test has passed. It has no repository license file; do not copy its source into this app. |

The pinned rustpush source has **no standalone Kotlin/JNI binding**. OpenBubbles' `rust/src/api/api.rs` exports most messaging operations through Flutter Rust Bridge. Its UniFFI Kotlin bindings in `rust/src/native.rs` handle native background/keystore callbacks and depend on the surrounding OpenBubbles Rust/Dart architecture; they are not a drop-in provider for this app. Reusing the protocol engine requires a small, independently tested Android binding and a Kotlin implementation of the orchestration currently performed in Dart.

The public pinned tree does not contain the ten `certs/fairplay/*.crt` and `*.pem` files included by `activation.rs`; an unmodified source build fails with missing-file errors. [rustpush's own CI](https://github.com/OpenBubbles/rustpush/blob/a7fab473e7a33325a760635285db2860de8e1cb0/.github/workflows/build.yml) creates **fake FairPlay keys** from legacy files solely to get a compile artifact. The optional `imessage-native` ABI experiment follows that compile-only practice and exposes no Apple activation route. It is not a production registration implementation. An authorized, functional production activation path is still required before a pairing-to-message milestone can be claimed.

The Rust build requires the pinned `apple-private-apis` submodule and, for the default `macos-validation-data` feature, `OpenAbsinthe-Stub`. The stub is not the production Mac validation component. The iPhone relay path can disable the Mac validation feature while enabling `remote-anisette-v3`; without the latter, the pinned source fails to compile because `icloud_auth` imports anisette symbols that are feature-gated in `omnisette`. Android arm64 native compilation also needs a Rust Android target, an Android NDK, OpenSSL and protobuf build dependencies. The current app targets Android API 29+.

## Registration and security boundary

The app first checks the relay's reported software name in Dart, then `config_from_relay` fetches version information again from Rust. In the pinned [relay implementation](https://github.com/OpenBubbles/rustpush/blob/a7fab473e7a33325a760635285db2860de8e1cb0/src/relay.rs), the activation request uses `MacOS` and `iMac13,1` labels while taking the serial and unique device ID from the iPhone relay's version response. The relay also supplies validation data. [Activation](https://github.com/OpenBubbles/rustpush/blob/a7fab473e7a33325a760635285db2860de8e1cb0/src/activation.rs) posts to Apple and requires a `DeviceCertificate` in the response. A missing certificate is not, by itself, proof of why Apple rejected the request.

No serial substitution, label change, retry loop, or identity rotation belongs in the first build. On rejection, preserve the stable identity and log only a fixed redacted status/classification. Maintain TLS verification. Pairing codes, device IDs, validation blobs, Apple credentials and message bodies must never enter diagnostics.

## Milestones and verification gates

1. **Android native build:** a reproducible arm64 build, a narrow Kotlin binding, and a smoke call that loads the native library on a physical Android device. The old Flutter UI is not imported.
2. **Relay and account:** connect to a compatible iPhone relay using the existing pairing code, persist an account and encrypted session, restore after process restart, and prove a usable Apple activation/IDS registration on device. Stop on rejection.
3. **Text path:** map stable remote IDs to Room; receive and send one-to-one text with optimistic sending, sent/failed states, and retry through `ProviderSendRouter`. Show only verified capabilities.
4. **Lifetime:** foreground connection, reconnect, Android background limits, boot recovery, and actual scheduled registration renewal. A provider request is not proof of successful renewal.
5. **Richer events:** media, groups, reactions, replies, receipts, edits and redactions only after native and device evidence for each.

FaceTime exists in rustpush as a separate `FTClient`/`FTState` subsystem with call signaling and media dependencies. The OpenBubbles wrapper exposes some FaceTime operations, but the app's call UI and WebRTC/native media path are separate from iMessage text. It needs its own milestone and device testing; none of the messaging milestones imply calling support.

Cloud builds can validate compilation and pure tests. Pairing, Apple registration, SMS number verification, APNs delivery, carrier behavior and renewal require a physical Android phone and iPhone; no cloud result should be reported as success for these.

## Milestone 1 build evidence

- `:imessage-native:assembleDebug` passed with NDK 27.2.12479018 and Android API 29. The AAR contains an arm64 Android JNI library and the Kotlin bridge. `readelf` shows exported JNI symbols for the two bridge methods.
- `:app:assembleDebug -PenableImessageNative=true` passed; its debug APK contains the arm64 JNI library. The diagnostic bridge is not wired to the app's iMessage provider or send router.
- `:app:testDebugUnitTest` passed: 75 tests, 0 failures. `:imessage-native:assembleDebugAndroidTest` compiled an on-device library-load test; no Android device was connected to run it.
- `cargo test --manifest-path imessage-native/native/Cargo.toml --lib` passed: 1 test, 0 failures. The test rejects plaintext relay URLs and URLs that contain credentials or query tokens.
- `:app:assembleRelease -PenableImessageNative=true` passed. The release APK excludes the experimental native library because the module is a debug-only dependency.
- No pairing, activation, send, receive, background recovery, renewal or FaceTime operation was run or verified.
