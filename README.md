# UnifiedMessages

UnifiedMessages is a free, native Android messaging app built with Kotlin, Jetpack Compose, and Material 3 for a Nothing Phone (2). It can use the phone's **SMS and MMS** service when you make it the default SMS app and grant the requested permissions. Its inbox, conversations, search, and unread counts come from a local Room database populated from Android's system message store. There are no fictional chats in the running app.

**iMessage and RCS are not connected.** The iMessage/OpenBubbles, RCS, Matrix, and OpenPigeon providers are inactive placeholders for later work. The iMessage and RCS source filters do not turn on those services, including when Google Messages has RCS on the same number. Making UnifiedMessages the default SMS app may interrupt RCS in Google Messages; this phase handles SMS/MMS only. The app uses no paid API or subscription; your mobile carrier may charge for SMS, MMS, or mobile data under your plan.

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

If you decline the role or permissions, you can still open the app, but sending and importing SMS/MMS require both. Tap the **+** button to try the setup prompts again. If Android Studio does not show your phone, reconnect and unlock it, accept the debugging prompt, and check **Device Manager**; a charge-only cable will not deploy an app.

### Get the APK

In Android Studio choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**. When the build completes, click **locate** in the notification, or find the debug APK at `app/build/outputs/apk/debug/app-debug.apk`. The green **Run** button already installs that build on your selected phone. The debug APK is for personal testing; Android Studio's signed-release workflow is needed if you later distribute the app.

## Use the app

- Tap **+**, enter a phone number, and tap **Continue** to open a thread. Type a message and tap Send. One-to-one text goes by SMS; text in a group thread goes by MMS. The conversation shows pending, sent, delivered, or failed status when Android and the carrier provide it. A delivery receipt is not guaranteed by every carrier. If Android cannot identify this phone's number in an incoming group MMS, the app retains the sender as the safe reply recipient and does not guess the other recipients.
- To send a photo, video, or audio file by MMS, open it in a Gallery or Files app, tap **Share**, choose **UnifiedMessages**, enter a phone number, and tap **Send**. You can share multiple supported media items together. To open a received attachment, tap its message bubble and choose the attachment. The app does not yet provide an in-app media picker or full media viewer. Carrier size limits apply.
- Search the inbox by contact, number, message text, or source. The **All sources** menu filters SMS/MMS, RCS, and iMessage rows already stored locally; there is no live RCS or iMessage connection. Long-press a chat to pin or unpin it. Unread counts update as messages are opened.
- Open the top-right menu and choose **Settings**. Appearance changes apply immediately. Under **Message display**, turn **Show sender deletions** on to retain a visible “Deleted by sender” marker, or off to hide that message from the conversation. The underlying database retains deletion and edit history.
- Some incoming SMS messages such as `Liked 'hello'` are recognized as text fallback reactions and stored as reactions on the matching message. SMS itself does not support native reaction, edit, unsend, read receipt, or app-message actions, so those controls are unavailable.

The app must remain your default SMS app to receive and send through its SMS/MMS provider. To switch back, open your phone's **Settings → Apps → Default apps → SMS app** and select your previous messaging app. Existing SMS/MMS in Android's system message store are not erased by changing the default app. UnifiedMessages may still show previously imported local records, but it will stop receiving new messages and cannot send while another app holds the role.

## Appearance

Choose **Solid** or **Liquid Glass** surfaces, **Light**, **Dark**, or **System** brightness, and one of ten accent presets or a custom color in **Settings → Appearance**. The live preview and app update immediately; DataStore saves these settings on the phone. Liquid Glass uses the free Haze library for background blur on Android 12 (API 31) and newer and a more opaque tint on Android 10 and 11. On a wide screen, the inbox and conversation appear side by side.

## Project structure

- `app/src/main/java/com/unifiedmessages/app/data/local/` — Room entities, queries, and the event reducer.
- `app/src/main/java/com/unifiedmessages/app/data/repository/` — inbox and conversation projections plus the deleted-message setting.
- `app/src/main/java/com/unifiedmessages/app/provider/` — provider events, capabilities, and inactive future-provider stubs.
- `app/src/main/java/com/unifiedmessages/app/sms/` — default-SMS role integration, system message import, SMS transport, and MMS transport under `mms/`.
- `app/src/main/java/com/unifiedmessages/app/ui/` — Compose screens and Hilt ViewModels.
- `app/src/main/java/com/unifiedmessages/app/ui/theme/` and `ui/components/` — existing appearance palettes and reusable surfaces.
- `app/src/debug/` — fictional sample conversations used only by Compose previews and debug tests, never inserted into the running inbox.

## Build checks and current testing limit

With Android SDK Platform 36 and JDK 17 or newer installed, run in this folder:

```bash
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
```

On Windows use `gradlew.bat` instead of `./gradlew`. The reducer, reaction parser, and appearance have unit tests. Build and unit-test checks can run without a SIM. SMS/MMS sending, reception, carrier delivery receipts, and MMS download still need a physical-device test with an active SIM and carrier; they have not been verified on a physical phone in this workspace.
