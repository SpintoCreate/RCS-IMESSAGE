# UnifiedMessages

UnifiedMessages is a **free, native Android interface preview** built with Kotlin, Jetpack Compose, and Material 3. It is designed for a Nothing Phone (2), with a near-black Nothing-inspired look and an optional Liquid Glass appearance.

This first version contains **fictional sample conversations only**. It does not access your contacts or messages. It does not connect to iMessage or RCS, send SMS, or send anything typed in the composer. The source badges show what future conversations could look like; they are labels on sample data, not live connections.

## Run it in Android Studio

1. Install the free [Android Studio](https://developer.android.com/studio) on your computer. During setup, let it install the Android SDK and bundled JDK.
2. Open the [UnifiedMessages GitHub repository](https://github.com/SpintoCreate/RCS-IMESSAGE), select **Code → Download ZIP**, and extract it. In Android Studio, choose **Open** and select the extracted folder containing `settings.gradle.kts`.
3. Let Android Studio sync the Gradle project. If it asks to install Android SDK Platform **36**, accept. An internet connection is needed for the first dependency download.
4. On your Nothing Phone (2), open **Settings → About phone**, find **Build number**, and tap it seven times to enable Developer options. Then open **Settings → System → Developer options** and turn on **USB debugging**. Menu names can vary with your Nothing OS version.
5. Connect the phone to your computer with a data-capable USB cable. Allow the USB debugging prompt on the phone.
6. In Android Studio's device selector, choose your phone. Select the **app** run configuration and click the green **Run** triangle.

You can also choose an Android emulator in Android Studio if you do not want to use your phone. The app requires Android 10 (API 29) or newer.

If the phone does not appear, reconnect the cable, unlock the phone, approve the debugging prompt, and check Android Studio's **Device Manager**. A charge-only USB cable will not work for deployment.

## Try the preview

- Browse sample chats and see iMessage, RCS, and SMS source labels.
- Search names, sample messages, or source names; use the source filters.
- Open a conversation and type in the draft field. The send control is disabled because messaging is not connected.
- Open **Settings** to switch between Nothing and Liquid Glass appearances. Your selection is saved on the device.
- On a wider screen, the inbox and conversation appear side by side.

## Project structure

- `app/src/main/java/com/unifiedmessages/app/model/` — conversation and message models, with explicit source types.
- `app/src/main/java/com/unifiedmessages/app/data/` — fictional sample content and inbox filtering.
- `app/src/main/java/com/unifiedmessages/app/ui/` — Compose screens and navigation.
- `app/src/main/java/com/unifiedmessages/app/ui/theme/` — Nothing and Liquid Glass palettes.
- `app/src/main/java/com/unifiedmessages/app/ui/components/` — reusable surfaces, source badges, and dot details.

The sample repository is isolated from the screens so a future, separately tested connector can replace it. Conversations retain unique IDs and source labels; two chats with the same contact are kept separate when they use different transports.

## Build checks

With Android SDK Platform 36 and JDK 17 or newer installed, run from this folder:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Gradle, Android Studio, and the Android SDK are free. No API keys, subscriptions, or paid services are used.

## What is not connected yet

Live messaging requires separate work and real device testing. OpenBubbles and `mautrix-gmessages` were reviewed for later integration, but no code from either project is bundled here. In particular, an Android app cannot claim native third-party RCS support from the standard Android SMS API. The app deliberately asks for no SMS, contact, or network permissions at this stage.
