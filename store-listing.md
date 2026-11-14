# Play Console submission: Simple Note Pro

Copy-paste material for the store listing, plus suggested answers to the declarations Play
requires. Everything here matches what version 1.5.0 actually does. Check it still does
before you submit a later version.

---

## App details

| Field | Value |
| --- | --- |
| App name (30 char max) | `Simple Note Pro` |
| Package | `com.holymanzion.simplenotepro` |
| Version | 1.5.0 (versionCode 10) |
| Upload file | `SimpleNotePro-v1.5.0.aab` |
| Category | Productivity |
| Tags | Notes, To-do list, Productivity |
| Contact email | holymanzion@gmail.com |
| Website | https://holymanzion.github.io/simple-note-pro/ |
| Privacy policy URL | https://holymanzion.github.io/simple-note-pro/privacy_policy.html |

---

## Short description (80 characters max)

```
Notes, checklists, reminders and photos. No account, kept on your phone.
```

*(72 characters.)*

---

## Full description (4000 characters max)

```
Simple Note Pro is a fast, private place for everything you want to remember: notes, checklists, reminders and photos. There's no account to create, and your notes are stored on your phone, not on someone else's server.

WRITE IT DOWN, YOUR WAY
• Text notes with simple formatting: headings, bold, italic, strikethrough, bulleted and numbered lists
• Lists continue when you press Enter, and links become tappable
• Checklists you can drag to reorder; ticked items move out of the way
• Undo and redo while you write
• Reading mode: notes open ready to read, with tappable links and no keyboard
• Everything saves automatically as you type

PHOTOS, WITH SEARCHABLE TEXT
• Take a photo or add pictures from your gallery
• Text in your pictures (receipts, documents, whiteboards, screenshots) is read on your phone, so search finds it
• Copy that text, or add it to the note with one tap

NEVER FORGET
• Reminders for any date and time, or quick picks like "tomorrow morning"
• Repeat daily, weekly, monthly or yearly
• Snooze for an hour or mark done right from the notification
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
• Single-note widget: tick off checklist items right on your home screen
• "New note" tile in Quick Settings
• App shortcuts for a new note or checklist
• Share text or a picture from any app to save it as a note

YOUR NOTES STAY YOURS
• No account, no sign-in
• Your notes and photos are never shared with advertisers
• Optional app lock with fingerprint, face or screen lock, which also hides notes from screenshots and recent apps
• Automatic backups to a folder you choose (for example one synced by Google Drive), and ZIP export and import
• Optional backup password: backups are encrypted with AES-256

Light and dark themes, Material You colours on Android 12 and newer.

Free to use, with a small banner ad on the notes list and while reading a note.
```

---

## Release notes

### 1.5.0

```
<en-US>
New: reading mode. Notes now open ready to read, with formatting shown, links you can tap and no keyboard in the way. Tap Edit to make changes, or turn it off in Settings. A small banner ad shows below a note while you read; never while you type.
</en-US>
```

### 1.4.0

```
<en-US>
Simple Note Pro now shows a small banner ad on the notes list, which helps keep the app free. Ads never appear while you write, and your notes and photos stay on your phone.
</en-US>
```

### 1.3.0

```
<en-US>
New in this update:
• Snooze or mark done right from a reminder notification
• Single-note widget: tick off checklist items on your home screen
• Backup password: encrypt your backups with AES-256
</en-US>
```

### 1.2.0 (first release)

```
<en-US>
Welcome to Simple Note Pro!
• Notes and checklists with formatting, undo and drag-to-reorder
• Reminders that can repeat daily, weekly, monthly or yearly
• Photos in notes, with the text inside them searchable
• Labels, colours, pins, archive, and trash with undo
• App lock with fingerprint or screen lock
• Automatic backups to a folder you choose
• Home-screen widget and Quick Settings tile
No account, no ads. Your notes stay on your phone.
</en-US>
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

Notes, pictures and their text never leave the phone. Two Google libraries use the network:
**Google Mobile Ads** (banner ads, from 1.4.0), described in
[Google's AdMob data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure),
and **Google ML Kit** (text in photos), described in
[Google's ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure).
Google says you, the developer, are responsible for the answers. These are the cautious ones:

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **Yes** (ads and ML Kit diagnostics) |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (both use HTTPS) |
| Do you provide a way for users to request that their data is deleted? | **No**. The developer holds no user data. Notes are deleted in the app or by uninstalling it. |

Data types to declare:

| Data type | Collected | Shared | Ephemeral | Required | Purpose |
| --- | --- | --- | --- | --- | --- |
| Location → **Approximate location** (from IP address) | Yes | Yes | No | Yes | Advertising or marketing, Analytics, Fraud prevention |
| App activity → **App interactions** | Yes | Yes | No | Yes | Advertising or marketing, Analytics, Fraud prevention |
| App info and performance → **Diagnostics** | Yes | Yes | No | Yes | Advertising or marketing, Analytics, Fraud prevention |
| Device or other IDs → **Device or other IDs** | Yes | Yes | No | Yes | Advertising or marketing, Analytics, Fraud prevention |

Everything else is **not collected**: personal info, precise location, messages, photos and
videos, files and docs, contacts, calendar, web browsing, financial and health info.
Photos are processed on the device and never sent, so they are not "collected" in Play's sense.

---

## Other declarations

| Section | Answer |
| --- | --- |
| Ads | **Yes**, the app contains ads (banner on the notes list and in reading mode) |
| Advertising ID | **Yes**, used for **Analytics**, **Advertising or marketing** and **Fraud prevention** (added by Google Mobile Ads) |
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
