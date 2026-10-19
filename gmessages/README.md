# Personal Google Messages linked-device prototype

This removable module contains an experimental **debug-only** libgm wrapper. It is separate from the normal SMS and Matrix providers and does not connect to the main inbox. The phone tests run from a separate `GMessagesDebug` launcher icon. The main UI and release build do not use the wrapper. The generated `libs/libgm.aar` is committed only to the private spike branch for the owner's personal phone test.

`libgm` belongs to `mautrix-gmessages` and is licensed AGPL-3.0-or-later. The published embedding exceptions do not name UnifiedMessages. This is for one personal, undistributed build. **Do not distribute its APK or AAB** until the license and source obligations are resolved. See [LICENSES.md](../LICENSES.md). Delete this module, its `settings.gradle.kts` include, and the app Gradle dependency lines to remove the experiment.

## Build the personal debug prototype

The wrapper is pinned in `go/go.mod`. Build it only from those Go modules; no copy of upstream source is committed here. Use Go 1.27.2, a JDK with `javac`, Android SDK 36, official Android NDK r28c (`28.2.13676358`), and the pinned `gomobile` version used for this spike. NDK r28c is the official package available in the cloud toolchain and passed the arm64, API 29 build. The private branch includes the generated AAR, so a phone tester does not need to install Go or the NDK. The steps below are only for rebuilding that AAR.

From `gmessages/go` in a shell with `go`, `gomobile`, `javac`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `ANDROID_NDK_HOME` available:

```sh
export GOFLAGS=-buildvcs=false
go test ./gmbind
gomobile bind -target=android/arm64 -androidapi=29 -o ../libs/libgm.aar ./gmbind
```

On Windows, download the **private** `spike/google-messages-linked-device` branch as a ZIP, extract it, and open the extracted project in Android Studio. Confirm `gmessages/libs/libgm.aar` exists. Enable Android SDK 36. Add `enableGMessagesPrototype=true` to the local copy of `gradle.properties`, sync, select the `app` debug run configuration and your USB-connected phone, then click Run. The debug build has a second launcher icon, `GMessagesDebug`. The release build excludes the prototype; `:app:verifyGmessagesReleaseIsolation` checks that the AGPL module is absent unless explicitly opted in. The opt-in does not grant distribution rights.

The built AAR is 7,152,639 bytes and contains only `arm64-v8a`; its declared minimum API is 29. The debug APK must be run on an arm64 device such as the Nothing Phone (2). Do not make this branch or repository public or share its AAR or APK without first resolving the AGPL license obligations.

## Pairing test, in order

This path is unverified on a phone. Use only your own Google account and browser session. The account selected in **Google Messages > Device pairing** on the phone must be the same account used in the desktop browser. A second account is only plausible if Google Messages on the phone is actually switched to that account; an unrelated throwaway browser account cannot pair with the phone account.

1. Keep **Google Messages as the default SMS app**. Open **Google Messages > profile icon > Device pairing** and note the selected Google account. On your Windows PC, open an InPrivate or Incognito desktop browser window, sign into that same account, and open `https://messages.google.com/web/config`. In browser DevTools > Network, select the `/web/config` request and copy **only the Cookie request-header value**. Do not copy a cURL command, share it, or paste it into a shell command. If the request or Cookie header is absent, stop and report that; do not substitute unrelated Google cookies.
2. In a private PowerShell terminal, from `gmessages/tools`, run `py -m venv .venv`, `.venv\Scripts\python -m pip install -r requirements.txt`, then `.venv\Scripts\python export_cookies.py`. Paste the Cookie header at its hidden prompt. The helper keeps only `SID`, `HSID`, `SSID`, `OSID`, `APISID`, `SAPISID`, and optional `__Secure-1PSIDTS`; it makes no network requests. Enter a new passphrase of at least 16 characters twice. It writes one AES-256 encrypted ZIP, `gmessages-cookie-export.zip`. Never type the Google password into our app or helper.
3. Copy the encrypted ZIP to the phone's Downloads folder using USB file transfer. Open `GMessagesDebug`, enter the export passphrase, tap **1. Choose encrypted ZIP and start pairing**, and select the ZIP. The app decrypts it locally, deletes the selected phone copy, and starts pairing. If the file provider refuses deletion, the import fails and you must delete that phone copy in Files. Keep the PC copy only until import succeeds; then delete it and empty Recycle Bin. The debug screen shows an emoji. Tap the matching emoji in Google Messages > Device pairing, then tap **Confirm emoji tapped on phone** in the debug screen. The linked session is stored as Android Keystore AES-GCM ciphertext and excluded from Android backup and device transfer.
4. Tap **2. List conversations**. Record whether a count and labels appear. Select one conversation index for a consenting test recipient. Keep the screen open, have the recipient send one message, and tap **3. Check receive count**. Finally type one test message and tap **4. Send one test text**. Verify arrival in the recipient's app; a success response only means the phone accepted the send request. The UI does not show or log received message content.
5. In Android Studio Logcat, filter by `GMessagesDebug` and send only the step/result and sanitized error lines. **Redact** any account, cookie, token, phone number, conversation label, or message content that appears before sharing. Report pair, list, receive, and send separately as success or failure. We cannot establish any of these device outcomes in the cloud.

The debug screen reconnects a saved session when opened and retries a disconnected session twice while the screen is open. It is **not** a background service; it does not promise reception when closed or after reboot until opened again. Google Messages must stay installed, signed in, paired, online, and the default SMS app.

## Sign out and revoke

Tap **Sign out and erase Google Messages session** in the debug screen to remove the local encrypted session and Android Keystore key. In Google Messages > Device pairing, unpair the linked device. Close the desktop private browser window. For broader Google cookie revocation, visit Google Account > Security > Your devices > Manage all devices, sign out of other sessions (or change the Google Account password). Check Google’s current account security controls if a session remains listed. Keep this test APK and the encrypted export private.
