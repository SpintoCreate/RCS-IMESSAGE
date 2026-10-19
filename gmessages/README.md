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

## How the safer transfer works

The helper does **not** read the browser cookie database. You copy the Cookie request header from Chrome DevTools; it selects only `SID`, `HSID`, `SSID`, `OSID`, `APISID`, `SAPISID`, and optional `__Secure-1PSIDTS`. Chrome's [app-bound cookie encryption](https://security.googleblog.com/2024/07/improving-security-of-chrome-cookies-on.html) protects database extraction on Windows; it does not prevent you from inspecting your own request in DevTools. Chrome is the recommended browser for this test. Edge is similar because it is Chromium-based; Firefox's interface differs. An extension could automate copying but would require broad cookie or request access, so the helper deliberately avoids it.

The helper generates nine random words from the 2048-word BIP39 English list (99 bits of randomness). PBKDF2-HMAC-SHA256 with 600,000 iterations and a fresh 16-byte salt derives an AES-256 key. AES-GCM authenticates and encrypts the cookie JSON with a fresh 12-byte nonce. Only a `UMGM2:` ciphertext is sent over **local USB via ADB**, which opens `GMessagesDebug` and fills its paste box. There is no LAN listener, cloud relay, plaintext file, or Google password entry in our app. The encrypted transfer expires after five minutes. The helper prints its exact length; ordinary transfers are a few hundred to a few thousand characters. The app accepts up to 100,000 characters; ADB auto-fill is limited to 25,000 because of Windows command-line length. If ADB cannot reach exactly one authorized USB phone, the helper offers a local clipboard fallback **only after you turn off Windows “Clipboard history” and “Sync across devices” under Settings → System → Clipboard** and type `OFF`. Paste through Android Studio's local USB device mirror if that works on your setup. Do not send it through email or chat.

The app clears its password field before decryption, wipes the mutable passphrase and derived key arrays afterward, and clears a matching phone clipboard entry immediately after successful import. Java and Android can keep temporary immutable copies until garbage collection, so no app can guarantee every physical memory copy disappears instantly. The helper clears its PC clipboard and terminal output when you press Enter after import, or after five minutes. The linked session is stored only as Android Keystore AES-GCM ciphertext and excluded from Android backup and device transfer.

## Beginner checklist: Windows and phone

Use only your own Google account. **Never send a Cookie header, nine-word phrase, encrypted transfer, phone number, or message text to anyone.** Each failure note below means send only the stated non-secret result and redacted `GMessagesDebug` lines.

1. **Phone:** Leave Google Messages as the default SMS app. If you cannot, report “Google Messages is not default.”
2. **Phone:** Open Google Messages → profile icon → Device pairing. If missing, report your Google Messages version.
3. **Phone:** Note the selected Google account privately. If it is the wrong account, switch it before continuing and report only “account mismatch.”
4. **PC:** Turn off Windows Settings → System → Clipboard → **Clipboard history** and **Sync across devices**. If either setting is unavailable, report which one before copying cookies.
5. **PC:** Open a new **Chrome Incognito** window. If Chrome is unavailable, report the browser name.
6. **PC:** Sign into the **same** Google account in that window. If sign-in fails, report the browser error without the account name.
7. **PC:** Press **F12** to open Chrome DevTools. If it does not open, try Chrome's ⋮ menu → More tools → Developer tools and report what happened.
8. **PC:** Click the **Network** tab in DevTools. If absent, report which tabs appear.
9. **PC:** Open `https://messages.google.com/web/config` in the same Incognito window. If the page fails, report its HTTP or browser error.
10. **PC:** In Network, click the request named **config** whose URL ends `/web/config`. If none appears, press **Ctrl+R** once and report “config request missing” if still absent.
11. **PC:** Click **Headers → Request Headers** for that request. If Headers is missing, report that without a screenshot of cookies.
12. **PC:** Copy only the **Cookie** request-header *value*. If missing, report “Cookie header missing”; never copy a cURL command or send the header in chat.
13. **PC:** Open PowerShell in the extracted project's `gmessages\tools` folder. If it opens elsewhere, report the folder path without account names.
14. **PC:** Run `py -m venv .venv`. If it fails, report the Python error.
15. **PC:** Run `.\.venv\Scripts\python -m pip install -r requirements.txt`. If it fails, report the package error.
16. **PC:** Connect the unlocked phone by USB. If Android Studio does not see it, report “USB phone missing.”
17. **Phone:** Accept the **USB debugging** prompt. If no prompt appears, report “USB prompt missing.”
18. **PC:** Run `.\.venv\Scripts\python export_cookies.py`. If it fails before `Cookie header:`, report the error.
19. **PC:** Type `OFF` at the helper’s first hidden prompt. If it stops, check the Windows clipboard setting.
20. **PC:** Paste the header **only at the hidden `Cookie header:` prompt** and press Enter. If the helper reports missing names, send only those names.
21. **Phone:** Open the automatically launched **GMessagesDebug** screen. If it did not open, report the helper's USB status.
22. **Phone:** Type the nine-word phrase, including hyphens, into the passphrase field. If you cannot read it on the PC, rerun the helper; do not send the phrase.
23. **Phone:** Tap **1. Import encrypted transfer and start pairing**. If it says wrong phrase, damaged, or expired, report exactly which appeared.
24. **Phone:** Tap the matching emoji in Google Messages → Device pairing. If no emoji appears, report “emoji missing.”
25. **Phone:** Tap **Confirm emoji tapped on phone** in GMessagesDebug. If it fails, send the redacted `GMessagesDebug` error line.
26. **PC:** Press Enter in the helper window to clear its clipboard and terminal output. If it already cleared after five minutes, report “helper timed out.”
27. **PC:** Close Chrome DevTools. If it does not close, report “DevTools still open.”
28. **PC:** Close the Incognito window. If it does not close, report “private window still open.”
29. **Phone:** Tap **2. List conversations**. If it fails, send the redacted `GMessagesDebug` line, never conversation labels.
30. **Phone:** Keep GMessagesDebug open while a consenting contact sends one test message. If the screen closes, reopen it and report that.
31. **Phone:** Tap **3. Check receive count**. If it does not increase, report only the before and after counts.
32. **Phone:** Select a listed conversation index. If no index is listed, report “no conversations.”
33. **Phone:** Type one test text. If the composer rejects it, report the on-screen reason without the text.
34. **Phone:** Tap **4. Send one test text**. If it fails, send the redacted error; if it succeeds, report whether the recipient actually received it.

If ADB says no authorized USB phone, recheck USB debugging and the phone authorization prompt. If ADB still fails, confirm Windows clipboard history and cloud sync are both off; the helper places only ciphertext on the PC clipboard. Use Android Studio's local USB device mirror to paste into GMessagesDebug. If the mirror cannot paste, stop and report that. No Wi-Fi discovery is needed.

In Android Studio, open **View → Tool Windows → Logcat**, select `com.unifiedmessages.app`, and filter for `GMessagesDebug`. Redact any account, token, phone number, conversation label, or content before sharing. Pair, list, receive, and send are unverified on a physical phone.

The debug screen reconnects a saved session when opened and retries a disconnected session twice while the screen is open. It is **not** a background service; it does not promise reception when closed or after reboot until opened again. Google Messages must stay installed, signed in, paired, online, and the default SMS app.

## Sign out and revoke

Tap **Sign out and erase Google Messages session** in the debug screen to remove the local encrypted session and Android Keystore key. In Google Messages > Device pairing, unpair the linked device. For broader Google cookie revocation, visit Google Account > Security > Your devices > Manage all devices, sign out of other sessions (or change the Google Account password). Check Google’s current account security controls if a session remains listed. Keep this test APK private.
