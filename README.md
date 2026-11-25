# Recall (working name)

An offline-first personal memory and capture app for Android. Capture a thought in seconds, organise
later or never, and let **Forgotten Radar** bring back what slipped out of sight.

"Recall" is a placeholder: the name lives in `app/src/main/res/values/strings.xml`, the launcher mark
in `res/drawable/ic_launcher_*.xml`, and the package id in `app/build.gradle.kts`.

## Build and run

Needs Android Studio (its bundled JDK is enough) and SDK platform 37.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

```bash
./gradlew :app:installDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

Debug builds can load sample items (this replaces whatever is in the app):

```bash
adb shell am start -n com.recall.app/.MainActivity --ez seed true
```

Add `--ei remind_in 30` to also get a reminder notification 30 seconds later.

## What is in it

| Area | Where |
| --- | --- |
| Home: capture field, type shortcuts, voice, Forgotten Radar preview, Today, Upcoming | `ui/home` |
| Inbox: search, type/status chips, sort, group, extra filters, swipe to finish or snooze | `ui/inbox` |
| Review: one item at a time, Done / Later / Not important / Edit / Skip | `ui/review` |
| Item detail: everything optional, shown only once set | `ui/detail` |
| Me: name, theme, accent, radar sensitivity, notifications, backup | `ui/settings` |
| Forgotten Radar rules, snooze times, recurrence, date wording | `domain` |
| Room database, repository (every change returns an undo), settings | `data` |
| Reminders and optional nudges (WorkManager) | `notify` |
| Google Drive and file backup / restore | `backup` |
| Home-screen widgets (Glance): a 4×2 capture + Forgotten Radar widget and a 1×1 capture button | `widget` |
| Quick capture over other apps: widget, launcher shortcuts, Share menu | `CaptureActivity` |

Architecture is single-module MVVM: Compose screens, one ViewModel per screen, a repository over Room,
and a small hand-written container (`AppContainer`) instead of a DI framework.

### Forgotten Radar rules

All local, in `domain/ForgottenRadar.kt`. An open, un-snoozed item surfaces when it is:

1. past its due date,
2. postponed 3 or more times,
3. untouched for N days (default 5; 3 / 5 / 7 / 14 in settings), or
4. captured 14+ days ago and not touched in the last 2.

Items with a due date or reminder still ahead of them are left alone, as are notes. "Someday" items
come back after 30 quiet days. Opening or editing an item counts as touching it.

## Google Drive backup: one-time setup

The code is complete, but Google only issues Drive tokens to apps registered in a Google Cloud
project, so this needs doing once per signing key. Until then, "Back up now" reports that Drive is
not configured; file export/import works with no setup.

1. In [Google Cloud Console](https://console.cloud.google.com/), create a project and enable the
   **Google Drive API**.
2. Configure the OAuth consent screen and add the scope `.../auth/drive.appdata`. While the app is
   in "Testing", add your own Google account as a test user.
3. Create an **OAuth client ID** of type **Android** with package name `com.recall.app` and the
   SHA-1 of the signing certificate. For the debug key:

   ```bash
   ./gradlew :app:signingReport
   ```

No client id or secret goes into the app: Google matches the package name and signature.

Backups live in the app's private Drive folder (`appDataFolder`), invisible in the user's Drive and
unreadable by other apps. Each backup is a new timestamped file; the newest 5 are kept. Restoring
asks for confirmation, keeps a copy of what was on the phone, and offers Undo.

## Things worth knowing

- **Reminder timing.** Reminders use WorkManager, so Android may deliver them a few minutes late when
  the phone is dozing. Exact-to-the-minute alarms would need the exact-alarm permission.
- **Font.** Text uses Google Sans where the device has it (Pixels) and the system font elsewhere.
  Bundling a font file would make it identical on every phone.
- **Compose version.** Pinned to BOM `2026.09.00` because newer ones need SDK platform 37.1.
