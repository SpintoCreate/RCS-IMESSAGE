# UnifiedMessages

UnifiedMessages is a free, native Android messaging app built with Kotlin, Jetpack Compose, and Material 3 for a Nothing Phone (2). It can use the phone's **SMS and MMS** service when you make it the default SMS app and grant the requested permissions. Its inbox, conversations, search, and unread counts come from a local Room database populated from Android's system message store. There are no fictional chats in the running app.

**iMessage and RCS are not connected.** Matrix support is experimental and still needs testing with real accounts on a phone. iMessage/OpenBubbles, RCS, and OpenPigeon remain placeholders. A Matrix bridge label does not install or configure that bridge. Making UnifiedMessages the default SMS app may interrupt RCS in Google Messages. The app uses no paid API or subscription; your carrier may charge for SMS, MMS, or mobile data under your plan.

## What you need

- A computer with the free [Android Studio](https://developer.android.com/studio), its bundled JDK, and Android SDK Platform **36**.
- An Android 10 (API 29) or newer phone. The intended device is the Nothing Phone (2).
- An active SIM and carrier SMS/MMS service to test real messaging. MMS also depends on your carrier's MMS and mobile-data configuration. An emulator is useful for looking at the app but does not replace testing with a SIM and carrier.
- A data-capable USB cable for installing directly from Android Studio.

## Install and run with Android Studio

1. Download this repository from [GitHub](https://github.com/SpintoCreate/RCS-IMESSAGE) using **Code → Download ZIP**, then extract it. In Android Studio choose **Open** and select the extracted folder containing `settings.gradle.kts`.
2. Let Gradle sync. Install Android SDK Platform **36** if Android Studio asks. The first sync downloads free dependencies and needs internet access.
3. On your phone, enable **Developer options** by tapping **Build number** seven times under **Settings → About phone**. In **Settings → System → Developer options**, enable **USB debugging**. The exact menu names can vary by Nothing OS version.
4. Connect the unlocked phone with a data-capable USB cable. Accept its USB debugging prompt. Select the phone and the **app** run configuration in Android Studio, then click the green **Run** triangle. Android Studio builds, installs, and opens the app.
5. On the welcome screen, tap **Get started**. Android asks whether to make UnifiedMessages the **default SMS app**. Choose UnifiedMessages, then grant the SMS/MMS permissions it requests. The app imports existing SMS and MMS threads from Android's message store; a large history may take a moment to appear.

If you decline the role or permissions, you can still open the app, but sending and importing SMS/MMS require both. Use the banner in Chats, a conversation, or New Message to request access again. The app asks for optional Contacts access when you first compose a new message; you can still type numbers if you decline it. If Android Studio does not show your phone, reconnect and unlock it, accept the debugging prompt, and check **Device Manager**; a charge-only cable will not deploy an app.

### Get the APK

In Android Studio choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**. When the build completes, click **locate** in the notification, or find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`. The green **Run** button already installs that build on your selected phone. The debug APK is for personal testing; Android Studio's signed-release workflow is needed if you later distribute the app.

## Use the app

- Tap **+** to open New Message. In **To:**, type a number and tap its **Send to** suggestion or press Done to add a recipient chip; you can also choose a contact. Add more chips for a group, type your message, then tap Send. The app finds the thread with exactly those recipients or creates one and opens it. One-to-one text goes by SMS; group text and messages with attachments go by MMS. The conversation shows sending, sent, delivered, or failed status when Android and the carrier provide it; failed outgoing messages have **Retry**. A delivery receipt is not guaranteed by every carrier. If an imported MMS group has an incomplete recipient list, sending is blocked with an explanation so no member is guessed; start a New Message and add every recipient.
- To send a photo, video, or audio file by MMS, tap the paperclip in New Message or a conversation and choose files. You can also share supported media from Gallery or Files to UnifiedMessages. To open a received attachment, tap its message bubble and choose the attachment. Carrier size limits apply.
- Search the inbox by contact name, phone number, or message text. Matching message text appears in the preview. The source menu offers networks present in your local inbox; a label does not activate RCS or iMessage. Long-press a chat to pin or unpin it. Unread counts update as messages are opened.
- Open the top-right menu and choose **Settings**. Appearance changes apply immediately. Under **Message display**, turn **Show sender deletions** on to retain a visible “Deleted by sender” marker, or off to hide that message from the conversation. The underlying database retains deletion and edit history.
- Some incoming SMS messages such as `Liked 'hello'` are recognized as text fallback reactions and stored as reactions on the matching message. SMS itself does not support native reaction, edit, unsend, read receipt, or app-message actions, so those controls are unavailable.

The app must remain your default SMS app to receive and send through its SMS/MMS provider. To switch back, open your phone's **Settings → Apps → Default apps → SMS app** and select your previous messaging app. Existing SMS/MMS in Android's system message store are not erased by changing the default app. UnifiedMessages may still show previously imported local records, but it will stop receiving new messages and cannot send while another app holds the role.

## Experimental Matrix testing

Matrix works directly from the Android phone, without your own server. Open **Settings → Matrix accounts**, enter a homeserver (default `matrix.org`), check its offered sign-in methods, and sign in. Password, SSO, and OIDC browser sign-in are implemented where the homeserver offers them. The app stores the Matrix session using Android Keystore and the SDK's local store, restores it after restart, and asks you to sign in again if authentication expires. Joined rooms, text/media messages, replies, reactions, edits, redactions, receipts, and typing are mapped into the local inbox. Encrypted rooms may require the recovery key and verification on another trusted device. These flows compile but have not been validated against live Matrix accounts in this workspace.

To test with two `matrix.org` accounts, sign in to UnifiedMessages with account A and to Element with account B. Invite A to a room from Element, open its invitation in UnifiedMessages, and tap **Join Matrix room**. Send one text and one image from B and check that each appears once in UnifiedMessages. Reply and send an image from A, then check them in Element. React to, edit, and redact a message in Element and verify the timeline updates here; try the reverse from UnifiedMessages where the action menu offers it. Turn off data, attempt a send, restore data, and retry a failed message. Restart UnifiedMessages and confirm the room and history return without duplicates. Lock the phone and send from B; a new-message notification may arrive after background sync rather than immediately. Report any failed step and its exact message.

Android WorkManager performs connected-account background sync no more frequently than its 15-minute periodic minimum, and Android may delay it further in Doze. It is the phone-only notification option implemented here. UnifiedPush could provide faster delivery with a compatible distributor and push gateway; FCM also needs a gateway and its associated service configuration. Neither push path is configured here, so notifications are **not real-time** while the app is closed.

For rooms already connected to a Matrix bridge, you may enter the bridge bot's full Matrix ID under that account. Only `m.bridge` or `uk.half-shot.bridge` room state sent by that same bot and advertising that bot is trusted. Recognized WhatsApp, Telegram, and Google Messages protocol IDs receive network badges and filters; unknown protocols remain labeled Matrix. A verified bridged conversation discloses that the bridge can read messages and end-to-end encryption does not continue past it. Remote edit, reaction, unsend, and reply actions stay hidden on bridged rooms because their support is not established. Bridge setup and live bridge testing are separate work.

## Appearance

Choose **Solid** or **Liquid Glass** surfaces, **Light**, **Dark**, or **System** brightness, and one of ten accent presets or a custom color in **Settings → Appearance**. The live preview and app update immediately; DataStore saves these settings on the phone. Liquid Glass uses the free Haze library for background blur on Android 12 (API 31) and newer and a more opaque tint on Android 10 and 11. On a wide screen, the inbox and conversation appear side by side.

## Project structure

- `app/src/main/java/com/unifiedmessages/app/data/local/` — Room entities, queries, and the event reducer.
- `app/src/main/java/com/unifiedmessages/app/data/repository/` — inbox, conversation, and contact projections plus the deleted-message setting.
- `app/src/main/java/com/unifiedmessages/app/provider/` — provider events, capabilities, and inactive future-provider stubs.
- `app/src/main/java/com/unifiedmessages/app/sms/` — default-SMS role integration, system message import, SMS transport, and MMS transport under `mms/`.
- `app/src/main/java/com/unifiedmessages/app/matrix/` — experimental Matrix SDK session, sync, media, background work, and verified bridge detection.
- `app/src/main/java/com/unifiedmessages/app/ui/` — Compose screens and Hilt ViewModels.
- `app/src/main/java/com/unifiedmessages/app/ui/theme/` and `ui/components/` — existing appearance palettes and reusable surfaces.
- `app/src/debug/` — fictional sample conversations used only by Compose previews and debug tests, never inserted into the running inbox.

## Build checks and current testing limit

With Android SDK Platform 36 and JDK 17 or newer installed, run in this folder:

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`. The reducer, reaction and bridge parsers, phone normalization, thread matching, search, and appearance have unit tests. Build and unit-test checks can run without a SIM. SMS/MMS carrier behavior and all Matrix login, encrypted-room, sync, media, and push behavior still need physical-device tests; this cloud workspace has no connected Android device.
