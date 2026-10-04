# Play Console submission: Simple Note Pro

Copy-paste material for the store listing, plus suggested answers to the declarations Play
requires. Everything here matches what version 1.2.0 actually does. Check it still does
before you submit a later version.

---

## App details

| Field | Value |
| --- | --- |
| App name (30 char max) | `Simple Note Pro` |
| Package | `com.holymanzion.simplenotepro` |
| Version | 1.2.0 (versionCode 7) |
| Upload file | `SimpleNotePro-v1.2.0.aab` |
| Category | Productivity |
| Tags | Notes, To-do list, Productivity |
| Contact email | holymanzion@gmail.com |
| Website | https://holymanzion.github.io/simple-note-pro/ |
| Privacy policy URL | https://holymanzion.github.io/simple-note-pro/privacy_policy.html |

---

## Short description (80 characters max)

```
Notes, checklists, reminders and photos. No account, no ads, kept on your phone.
```

*(80 characters.)*

---

## Full description (4000 characters max)

```
Simple Note Pro is a fast, private place for everything you want to remember: notes, checklists, reminders and photos. There's no account to create and no ads, and your notes are stored on your phone, not on someone else's server.

WRITE IT DOWN, YOUR WAY
• Text notes with simple formatting: headings, bold, italic, strikethrough, bulleted and numbered lists
• Lists continue when you press Enter, and links become tappable
• Checklists you can drag to reorder; ticked items move out of the way
• Undo and redo while you write
• Everything saves automatically as you type

PHOTOS, WITH SEARCHABLE TEXT
• Take a photo or add pictures from your gallery
• Text in your pictures (receipts, documents, whiteboards, screenshots) is read on your phone, so search finds it
• Copy that text, or add it to the note with one tap

NEVER FORGET
• Reminders for any date and time, or quick picks like "tomorrow morning"
• Repeat daily, weekly, monthly or yearly
• Reminders survive restarts, and the app warns you if notifications are turned off

STAY ORGANISED
• Pin important notes to the top
• 12 note colours and your own labels
• Grid or list view, sorted by date or title
• Search across titles, notes, checklists, labels and text in photos
• Swipe to archive or delete, with undo
• Trash keeps deleted notes for 30 days

CAPTURE IN A SECOND
• Home-screen widget with your pinned or recent notes
• "New note" tile in Quick Settings
• App shortcuts for a new note or checklist
• Share text or a picture from any app to save it as a note

PRIVATE BY DESIGN
• No account, no sign-in, no ads
• Optional app lock with fingerprint, face or screen lock, which also hides notes from screenshots and recent apps
• Automatic backups to a folder you choose (for example one synced by Google Drive), and ZIP export and import

Light and dark themes, Material You colours on Android 12 and newer.
```

---

## Graphics

All in `store-assets/`:

| Asset | File | Spec |
| --- | --- | --- |
| App icon | `icon-512.png` | 512 × 512 PNG, generated from the launcher icon by `tools/StoreGraphics.java` |
| Feature graphic | `feature-graphic.png` | 1024 × 500 PNG, same tool |
| Phone screenshots | `screenshots/*.png` | 1080 × 1920 PNG, generated on a device by `StoreAssetsTest` |

To regenerate: `java tools/StoreGraphics.java` for the icon and feature graphic. For the
screenshots, install the debug and test APKs and run
`adb shell am instrument -w -e class com.holymanzion.simplenotepro.StoreAssetsTest com.holymanzion.simplenotepro.debug.test/androidx.test.runner.AndroidJUnitRunner`,
then pull `files/store` from the debug app.

---

## Data safety (suggested answers)

The app itself sends nothing. Notes, pictures and their text never leave the phone. The one
network user is **Google ML Kit** (text in photos), which sends Google anonymous usage
statistics, as described in
[Google's ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure).
Google says you, the developer, are responsible for the answers. These are the cautious ones:

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** (ML Kit diagnostics) |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (ML Kit uses HTTPS) |
| Do you provide a way for users to request that their data is deleted? | **No**. The developer holds no user data. Notes are deleted in the app or by uninstalling it. |

Data types to declare:

| Data type | Collected | Shared | Ephemeral | Required | Purpose |
| --- | --- | --- | --- | --- | --- |
| App info and performance → **Diagnostics** | Yes | No | No | Yes | Analytics |
| Device or other IDs → **Device or other IDs** | Yes | No | No | Yes | Analytics |

Everything else is **not collected**: personal info, location, messages, photos and videos,
files and docs, contacts, calendar, app activity, web browsing, financial and health info.
Photos are processed on the device and never sent, so they are not "collected" in Play's sense.

---

## Other declarations

| Section | Answer |
| --- | --- |
| Ads | **No**, the app contains no ads |
| App access | **All functionality is available without special access** (no login; the app lock is optional) |
| Content rating | Fill in the questionnaire as a productivity / utility app: no violence, sexual content, gambling, drugs or user-to-user communication. Expect **Everyone / PEGI 3** |
| Target audience | **13 and over** is the simplest choice. Choosing under 13 brings in the Families policy, which the app isn't set up for |
| News app | No |
| COVID-19 contact tracing / status | No |
| Government app | No |
| Financial features | None |
| Health | No |

Permissions Play may ask about:

- **Exact alarms (`SCHEDULE_EXACT_ALARM`)**: used so reminders go off on time. This is the
  user-grantable permission, not the restricted `USE_EXACT_ALARM`, so no special declaration is
  needed. Reminders fall back to inexact timing if it isn't granted.
- **Foreground service**: added by Android's WorkManager library (used for automatic backups).
  The app never starts a foreground service itself. If Console asks, say it isn't used.
