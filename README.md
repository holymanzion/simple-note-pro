# Simple Note Pro

An offline note-taking app for Android, built with Kotlin, Jetpack Compose (Material 3) and Room.

## Features

**Writing**
- **Text notes and checklists.** Press Enter to add a checklist item, or paste several lines to create one item per line. Drag the handle to reorder items (screen-reader users get "Move up" and "Move down" actions instead). You can switch a note between text and checklist, and ticked items can move to a collapsible section at the bottom.
- **Formatting.** A toolbar above the keyboard adds **bold**, *italic*, ~~strikethrough~~, headings, bulleted lists and numbered lists. Enter continues a list, and Enter on an empty item ends it. Formatting is stored as plain Markdown-style marks, so notes stay readable when shared or exported. Links are detected and appear as tappable chips.
- **Images.** Take a photo or add pictures from your gallery. They appear below the note's text, whole and at their own shape. Large photos are shrunk (to at most 2048 px) and turned the right way up. Tap a picture to view it full screen, zoom, share or delete it. Sharing a picture from another app into Simple Note Pro creates a note with it.
- **Text in photos.** Printed text in pictures (receipts, documents, whiteboards, screenshots) is read on the phone, with no upload, so search finds notes by it and marks them "Found in image". In the picture viewer, **Text in image** lets you copy that text or add it to the note.
- **Autosave.** Notes save while you type. An empty note is thrown away when you leave it; a note with only a picture is kept.
- **Undo and redo** in the editor. Each step is a burst of typing, and up to 100 steps are kept.

**Organising**
- **Pin, color, label and archive.** 12 note colors; labels you can create, rename and delete.
- **Swipe cards** on the home screen: right to archive (or unarchive), left to move to the trash, with Undo either way.
- **Select several notes** with a long press, then pin, color, archive, copy or delete them together.
- **Search** across titles, note text, checklist items and label names.
- **Grid or list view**, sorted by date modified, date created or title.
- **Trash.** Deleted notes can be restored. They are deleted for good after 30 days, or straight away if you empty the trash.

**Reminders**
- Quick choices (later today, tomorrow morning, next week) or any date and time you pick.
- **Repeat** daily, weekly, monthly or yearly. A repeating reminder keeps its time of day across daylight-saving changes, and if the phone was off when it was due, it moves on to the next time rather than firing a backlog.
- **Snooze 1 hour** and **Done** buttons on every reminder notification. Snoozing a repeating reminder adds a one-off extra alert and leaves its schedule unchanged.
- Reminders are set up again after the phone restarts. If notifications are turned off, the note and the Reminders view show a warning with a button to turn them on.

**Privacy and safety**
- **App lock.** Ask for your fingerprint, face or screen lock when the app opens and after it has been in the background for a minute. While the lock is on, notes are hidden from screenshots and the recent-apps screen.
- **Automatic backups** to a folder you choose, daily or weekly, keeping the newest 7. Pick a folder that Google Drive or another cloud app syncs to keep an off-phone copy.
- **Backup files.** Export everything (notes, labels and images) to a ZIP file and import it again. Importing adds to your notes rather than replacing them. JSON backups from version 1.0 still import.
- **Backup password.** Optionally encrypt every backup (exported and automatic) with AES-256, in the standard encrypted-ZIP format that 7-Zip can also open. The password is stored on the phone, encrypted with an Android keystore key, so automatic backups can run unattended. Restoring on the same phone needs no prompt; on another phone the app asks for it.

**Quick capture**
- **Home-screen widget** showing your pinned notes (or your most recent), with buttons for a new note and a new checklist. Resizable. With the app lock on, it shows no note contents.
- **Single-note widget:** pick one note or checklist when placing it. A checklist's items can be ticked right on the home screen. On Android 12+, touch and hold it to choose a different note.
- **Quick Settings tile:** "New note" in the swipe-down panel. On a locked phone it asks you to unlock first.
- **Launcher shortcuts** (long-press the app icon) for "New note" and "New checklist".

**Ads**
- A banner on the notes list, from Google AdMob. Nothing in the editor, widgets or lock screen, and no space is used until an ad has loaded. Where the law requires it, Google's consent form comes first, and **Settings › Ad privacy choices** changes the answer later.

**Everything else**
- **Share** a note as text.
- **Note info:** when it was created and last edited, plus word, character and checklist-progress counts.
- **Themes:** light, dark or follow the system, plus Material You dynamic color on Android 12 and newer.

## Build

```bash
./gradlew bundleRelease    # app/build/outputs/bundle/release/app-release.aab — upload this to Play
./gradlew assembleRelease  # app/build/outputs/apk/release/app-<abi>-release.apk — install directly
# Run these as two separate commands: the build tools refuse to make both in one run.
```

`assembleRelease` makes one APK per processor type, because the text-in-photos reader is about 11 MB of native code per type. Almost every current phone needs **arm64-v8a** (about 14 MB). Older 32-bit phones need armeabi-v7a, and x86_64 is for emulators and Chromebooks. To check a connected phone, run `adb shell getprop ro.product.cpu.abi`. Google Play does this split itself from the `.aab`.

Requires JDK 17+ and Android SDK 36. minSdk is 26 (Android 8.0).

Release builds need your AdMob app ID and banner ad unit ID in `admobAppId` and `admobBannerUnitId` at the top of `app/build.gradle.kts`, and stop with an error if they're blank. Debug builds always use Google's test ads. Never tap your own live ads: AdMob bans accounts for it.

### Signing

Release builds are signed with the upload key in `upload-keystore.jks`; its password and alias are in `keystore.properties`. Both files are git-ignored. **Back them up somewhere safe** (a password manager or encrypted drive). Every future update must be signed with this key. If it's lost, you have to ask Google to reset it before you can publish again.

On a new machine, copy both files into the project root. Without them, release builds fall back to the debug key: they still install, but Play rejects them. To make a new key instead:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -storetype PKCS12 -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

then copy `keystore.properties.example` to `keystore.properties` and fill it in.

When you create the app in Play Console, keep **Play App Signing** on (the default). Google then holds the key that signs what users download, and this file is only your *upload* key, which Google can reset if needed.

## Tests

- **Unit tests** (`app/src/test`, run on the computer): formatting and list handling, repeat-date maths including daylight saving, and app-lock timing.
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Device tests** (`app/src/androidTest`): 28 end-to-end tests of the user journeys (notes, checklists, search, labels, reminders including notification actions, trash, undo, drag, swipe, images, text in photos, formatting, app lock, widgets and tile, backups including passwords and 1,000-note speed checks), with a screenshot of each step, plus database upgrade tests from 1.0 and 1.1 and picture-rotation tests. The debug build installs as a separate app (`.debug` suffix), so tests never touch your real notes. Keep the phone unlocked and don't use it while they run.
  ```bash
  ./gradlew installDebug installDebugAndroidTest
  adb shell am instrument -w com.holymanzion.simplenotepro.debug.test/androidx.test.runner.AndroidJUnitRunner
  ```

The system prompts (notification permission, fingerprint, folder and photo pickers) can't be driven by tests, so those steps are checked by hand.

## Structure

```
app/src/main/java/com/holymanzion/simplenotepro/
├── data/        Room entities, DAO and migrations, repository, image storage and text reading, settings, ZIP backup, repeat rules
├── ads/         Banner ad and Google consent form (AdMob)
├── backup/      Automatic backups (WorkManager)
├── lock/        App lock timing, fingerprint/screen-lock prompt, lock screen
├── reminder/    AlarmManager scheduling, notification receiver, boot receiver
├── text/        Formatting: styling, toolbar actions, list continuation
├── tile/        Quick Settings "New note" tile
├── widget/      Home-screen widget (Glance)
├── ui/home/     Note grid, drawer, search, selection mode, swipe actions
├── ui/editor/   Note and checklist editor: autosave, undo, images, formatting toolbar
├── ui/labels/   Label management
├── ui/settings/ Theme, app lock, backups
└── ui/theme/    Color scheme and note colors
```
