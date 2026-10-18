# Google Messages experimental module

This module is the removable boundary for a personal Google Messages linked-device spike. It contains no libgm source or bindings yet and does not enable pairing or messaging. Future libgm Go bindings, Android adapter code, and their notices belong **only here**. Other modules may reference it only through the app's provider interface.

`libgm` is part of `mautrix-gmessages`, licensed AGPL-3.0-or-later. The published embedding exceptions do not name UnifiedMessages. This module is for one user's personal, undistributed build only. Do not distribute an APK/AAB containing it without resolving the license and source obligations. See the repository `LICENSES.md`.

The app includes this empty boundary in debug builds. Release builds exclude it by default. `:app:verifyGmessagesReleaseIsolation` checks the release runtime classpath. The `includeGMessagesInRelease` Gradle property is an explicit build-time opt-in; it does **not** grant distribution rights. Removing the module means deleting this directory, its `settings.gradle.kts` include, and its app Gradle dependency lines.
