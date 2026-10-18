# Google Messages linked-device spike (research gate)

Date: 2026-10-18. Research target: `mautrix/gmessages` v0.2610.0 (2026-10-16), including its in-tree `pkg/libgm`. This is a design and feasibility record only. No Google Messages integration is present in UnifiedMessages.

## Decision

**No-go for an on-phone-only prototype now.** The supported mautrix login requires Google Account browser cookies extracted from a private browser session, normally through desktop developer tools or mautrix-manager. QR login was disabled in v0.2605.0. The project documents confirming an emoji **on** the phone, but does not document extracting cookies and pairing a linked client **on the same Android phone**. Android may suspend a long-polling client while the app is backgrounded; phone-only reliability is unproven. Finally, the reusable library is AGPL-3.0 and its embedding exceptions name Beeper and Element, not this app. A prototype can resume after a safe, workable same-phone pairing method and distribution/licensing decision are established. Do not collect or send Google cookies through chat or logs.

## Protocol and login

`libgm` is Go source in `mautrix-gmessages/pkg/libgm`, not a separately versioned Android SDK. The exposed login flow is `google`; `qr` remains in source but is commented out of the advertised flows. The Google flow asks for `SID`, `HSID`, `OSID`, `SSID`, `APISID`, and `SAPISID` cookies, sometimes `__Secure-1PSIDTS`, then displays an emoji to confirm in Google Messages. v0.2610.0 adds re-pairing with existing cookies by emoji tap. Pairing prepares P-256/Ukey2 key material, fetches Google configuration, exchanges protobuf requests with Google's relay, and saves an authenticated linked-device session. `libgm.Client.Connect` refreshes auth and starts a long poll; replies to commands come through that connection. It uses `messages.google.com` and `instantmessaging-pa.googleapis.com` / `instantmessaging-pa.clients6.google.com`, not a Matrix homeserver. The phone's Google Messages app remains the real SMS/RCS client and must have internet access.

The documented process calls for a private browser window to avoid cookie rotation, warns that device-bound session credentials are incompatible, and says to copy a `/web/config` request as cURL from browser developer tools. mautrix-manager can automate extraction in its own environment. Neither method is documented as safe and functional entirely on the same Android phone. Google documents Messages for web on a computer or Android tablet, not a second app on the same handset. Same-phone success must be observed on a physical device before changing the decision.

## Feature evidence at v0.2610.0

“Documented” means the bridge's roadmap/capabilities or methods expressly support it; it does not mean this Android app supports it. Capability depends on each conversation's actual SMS/MMS/RCS type and Google Messages settings.

| Feature | Evidence and status |
| --- | --- |
| SMS text; MMS text/media; RCS text/media | Documented by bridge roadmap, `SendMessage`, media upload/download, and SMS/RCS room capabilities. |
| Replies | Documented for RCS; not for SMS/MMS. |
| Reactions | Documented for RCS; SMS reaction support is partial/textual, not native reactions. |
| Edits | Receiving some updated message content is implemented; a user-facing outgoing edit command/capability is **undocumented/unsupported**. Do not expose edit. |
| Deletes | Documented **delete for me/own device only**. Remote unsend is unsupported. Release v0.2605.0 stopped processing some deletions during mobile SMS database sync due to false deletion events. |
| Typing | Documented for RCS, subject to settings; unavailable for SMS/MMS. |
| Read receipts | Documented for RCS, including group receipts inbound; not a universal SMS feature despite generic SMS bridge capability flags. |
| Group chats | Documented for MMS/RCS groups; bridge provisioning supports group creation. |
| History backfill | Implemented via `ListConversations`, paged `FetchMessages`, and connector chat resync; breadth/completeness is not guaranteed without a device test. |

## Android packaging and reliability

`gomobile bind` can build a Go package into an Android AAR with Java bindings and native `.so` libraries. It defaults to four ABIs (`arm`, `arm64`, `386`, `amd64`); an arm64-only build would fit Nothing Phone (2), but device ABI should be confirmed. gomobile's default/minimum Android API is 16; this app's minimum is 29, so API level itself is not the obstacle. An adapter is necessary because libgm's public API uses Go contexts, channels/callbacks, protobuf types, and logging types that should not be exposed directly to Kotlin. A JNI/native bridge is another route. **APK size is unknown** until an arm64 AAR is built and measured; no size number is claimed. The tag's `go.mod` specifies Go 1.26.0 and toolchain Go 1.27.2; Android NDK/gomobile compatibility would need a build proof.

Google Messages must stay installed, be the default SMS app, retain the paired account, and be connected. Closing its window may be tolerated, but force-stop, its background restrictions, lost data signal, and reboot effects are **undocumented** for same-phone libgm use; don't promise delivery while those conditions hold. The bridge itself warns when the phone does not respond and suggests keeping Messages open or relaxing battery optimization. Turning off Google Messages' Android notifications might suppress duplicate alerts but whether it changes linked-device transport is undocumented and requires testing. Even if the phone app remains connected, Android Doze restricts this app's network access/background work; a long poll cannot be assumed to run continuously. A foreground service could improve uptime while visibly active but is subject to Android service limits and battery cost. WorkManager catch-up after process start/reboot provides eventual sync, not instant receipt. Credentials/session keys would need Android Keystore-backed persistence; restoring them alone does not restart a connection after force-stop.

## License and account risk

`mautrix-gmessages` and in-tree `libgm` are AGPL-3.0-or-later. `LICENSE.exceptions` grants Beeper and Element narrow embedding/distribution exceptions; it grants none to UnifiedMessages. Bundling/binding this Go library into a distributed APK likely creates AGPL obligations for the combined work, including corresponding source, notices, and network-use obligations. Obtain an applicable exception from the copyright holder or plan an AGPL-compliant release before shipping; seek legal review for the exact distribution model. Personal local experimentation is different from publishing APKs. The protocol is unofficial and reverse engineered; Google may change or disable it, unpair a device, restrict access, or take account action under its terms. Browser cookies are highly sensitive Google Account credentials and must never enter app logs, Git, support messages, or an unsecured import path. No TLS verification bypass is acceptable.

## Future design if the research gate is cleared

Use a new `GoogleMessagesProvider` and separate `ProviderAccount`, with account-scoped remote conversation/message IDs mapped through `RemoteIdMapping` to stable Room IDs and cursors. The `MessageProvider` event stream can feed the existing pending-event reducer; the send router must register the new provider. Resolve actions per conversation network: SMS, MMS, or RCS. Advertise RCS replies/reactions/receipts/typing only when verified; hide outgoing edit and unsend. Track disconnected, pairing, connected, needs-attention, and retry states. In-app notifications while connected could use the event stream; closed-app delivery would require an evaluated Android background strategy, with no guaranteed instant delivery.

A future Settings mode switch would make either UnifiedMessages (native SMS) or Google Messages the default SMS app. Switching to Google mode would first request/verify Google Messages as default, then pair and sync; returning would request UnifiedMessages as default and resume its SMS receiver. Never run native sends when Google Messages owns the role. Import records by provider/account/remote ID or Android telephony URI and explicit linkage, never text/time alone. Preserve existing SMS history and private retained content; don't delete local data when changing modes. Google Messages remains installed and active in linked mode. Disable its *visible* notifications only after confirming linked transport still works; restore alerts if linked delivery is unreliable.

| Daily use | Native SMS mode | Proposed Google Messages linked mode |
| --- | --- | --- |
| Android and iPhone RCS | No public third-party RCS API; SMS/MMS fallback | Google Messages handles RCS where recipients/carriers support it, including supported iPhone RCS; bridge parity must be tested |
| Encryption | Carrier SMS/MMS has none | Google Messages RCS E2EE only in supported conversations; linked-device/key behavior needs verification; iPhone RCS E2EE is not guaranteed |
| Media quality | MMS carrier limits/compression | RCS can improve quality; MMS fallback still compresses |
| Receipts and typing | SMS has no native support | RCS-only where supported/enabled |
| Groups | MMS | MMS and RCS |
| iMessage | No iMessage | Still no iMessage; iPhone RCS is a different protocol |

Before leaving Google Messages UI: check backfilled history/media and stable ID mapping; test contact names/permissions; preserve spam/blocked settings and confirm whether they are exposed by libgm; identify unsupported drafts, scheduled messages, encrypted-content gaps, and Google-only features. Do not promise a complete migration. A phased rollout would be (1) pairing proof and Go/Android binding, (2) read-only sync on one device, (3) send/retry, (4) background reliability and mode switching, (5) media/RCS actions. Rough estimate: several weeks for a prototype and additional months for reliable daily use, contingent on the unproven pairing and licensing gates.

## Verification and references

The existing app's `:app:testDebugUnitTest` and `:app:assembleDebug` passed on the clean spike branch. No Go AAR or device test was run, and no integration code was written. Device validation, if this gate is later cleared, must confirm same-phone pairing, foreground send/receive, restart/reboot, screen-off/Doze, force-stop, offline recovery, and notification behavior without collecting secrets in logs.

- [mautrix-gmessages v0.2610.0](https://github.com/mautrix/gmessages/releases/tag/v0.2610.0), [v0.2605.0](https://github.com/mautrix/gmessages/releases/tag/v0.2605.0), [authentication docs](https://docs.mau.fi/bridges/go/gmessages/authentication.html)
- [Login implementation](https://github.com/mautrix/gmessages/blob/v0.2610.0/pkg/connector/login.go), [libgm client](https://github.com/mautrix/gmessages/blob/v0.2610.0/pkg/libgm/client.go), [pairing](https://github.com/mautrix/gmessages/blob/v0.2610.0/pkg/libgm/pair_google.go), [endpoints](https://github.com/mautrix/gmessages/blob/v0.2610.0/pkg/libgm/util/paths.go)
- [Feature roadmap](https://github.com/mautrix/gmessages/blob/v0.2610.0/ROADMAP.md), [capabilities](https://github.com/mautrix/gmessages/blob/v0.2610.0/pkg/connector/capabilities.go), [methods](https://github.com/mautrix/gmessages/blob/v0.2610.0/pkg/libgm/methods.go), [AGPL and exceptions](https://github.com/mautrix/gmessages/blob/v0.2610.0/LICENSE.exceptions)
- [Google Messages for web help](https://support.google.com/messages/answer/7611075?hl=en), [gomobile documentation](https://pkg.go.dev/golang.org/x/mobile/cmd/gomobile), [Android Doze](https://developer.android.com/training/monitoring-device-state/doze-standby), [Google Terms](https://policies.google.com/terms)
