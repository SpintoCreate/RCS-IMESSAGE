# iMessage native ABI experiment

This module is **compile-only**. It proves that an Android arm64 build can link the pinned rustpush engine and that Kotlin can call a narrow JNI binding. It can probe a configured HTTPS registration relay, but it does not perform Apple activation, Apple ID login, iMessage sending, receiving, or renewal. The main app keeps its inert iMessage provider and hides unsupported actions.

The build fetches [OpenBubbles/rustpush at `a7fab473`](https://github.com/OpenBubbles/rustpush/tree/a7fab473e7a33325a760635285db2860de8e1cb0) into this module's ignored `build/` directory. rustpush is SSPL-1.0 and its special exception names OpenBubbles only. Do not redistribute an APK containing this module without resolving its license obligations. `StablePush` is an optional iPhone-side relay candidate, not an Android engine.

Upstream omits production FairPlay files from the public tree. Its own CI copies legacy files under the missing names solely to compile. This module does the same. **A successful build does not mean Apple registration will work.** The JNI probe returns only fixed status words and never logs pairing codes, identifiers or response bodies.

Prerequisites: JDK 17+, Android SDK 36, Android NDK 27.2.12479018, Rust with `aarch64-linux-android` target, Git and `protoc` (`PROTOC` must point to the executable). Run `./gradlew :imessage-native:assembleDebug` to build the ABI, or `./gradlew :app:assembleDebug -PenableImessageNative=true` to package it into a debug APK. The production app does not depend on this module by default. No Apple network request is made by a build or unit test.

To test the JNI load on an arm64 Android device with USB debugging enabled, run `./gradlew :imessage-native:connectedDebugAndroidTest`. This test only checks the pinned engine version. It does not pair, register, send or receive iMessages.
