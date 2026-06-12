# CashLog — Session Handoff

## What Was Built

An Android home-screen widget app called **CashLog** that records cash expenses by voice and saves them to Google Sheets.

**Flow:** Tap widget → SpeechRecognizer activates → user speaks (e.g. "fifty taxi") → app parses amount + subject → appends a row to a Google Sheet tab named by month ("Jun 2026").

---

## Current Status: ✅ WORKING ON USER'S PHONE

- App installed on user's Xiaomi phone (sideloaded via APK)
- Google Sign-In working (`mrmarkrudis@gmail.com`)
- Widget on home screen
- Data saves correctly to Google Sheets with monthly tabs

---

## Repository

- **Repo:** `Maru3000/whatsappBot`
- **Branch:** `claude/android-voice-expense-recorder-B4Hyp`
- **PR #1:** open, all code is there
- **Android project root:** `android/` subfolder of the repo
- **User's local clone:** `D:\CashLog\` (Windows)

---

## Key Technical Details

| Detail | Value |
|--------|-------|
| App name | CashLog |
| applicationId | `com.maru.cashlog` |
| namespace (internal) | `com.maru.expenserecorder` |
| Package for Google Cloud | `com.maru.cashlog` |
| Min SDK | 26 |
| Language | Kotlin |
| Build tool | Gradle 8.6 + AGP 8.3.2 |

### Google Cloud Console (project: CashLog)
- Google Sheets API: **enabled**
- OAuth consent screen: **External**, testing mode
- Test user: `mrmarkrudis@gmail.com`
- Android OAuth credential: **CashLog Android**
  - Package: `com.maru.cashlog`
  - SHA-1 (debug key): `F7:A0:46:0C:C0:35:1A:57:54:BC:3F:D1:AF:D4:A4:A9:03:6A:DD:AB`

### Debug keystore (user's machine)
- Path: `C:\Users\mrmar\.android\debug.keystore`
- keytool location: `D:\AndroidStudio\jbr\bin\keytool.exe`

### Default spreadsheet
- User set up their own Google Sheet and saved the ID in the app
- App creates a new tab per month named `MMM yyyy` in English (e.g. "Jun 2026")
- Columns: Date | Time | Amount | Subject

---

## Architecture Summary

```
ExpenseWidget (AppWidgetProvider)
  └── taps → RecordingActivity (Dialog-theme, transparent)
        ├── SpeechRecognizer (he-IL,en-US)
        ├── ExpenseParser.parse(text) → ParsedExpense(amount, description)
        └── ExpenseRepository.appendExpense() → Google Sheets API v4

MainActivity (Settings screen)
  ├── GoogleAuthManager → Google Sign-In
  └── PrefsKeys → SharedPreferences (spreadsheet ID)
```

### Key source files
| File | Purpose |
|------|---------|
| `ExpenseParser.kt` | Parses voice input: digit amounts, English/Hebrew number words, shekel stripping, first-number rule |
| `ExpenseRepository.kt` | Writes to Google Sheets; creates monthly tab if missing |
| `GoogleAuthManager.kt` | Google Sign-In wrapper |
| `RecordingActivity.kt` | Voice capture overlay |
| `MainActivity.kt` | Settings screen |
| `data/Expense.kt` | Data class (date, time, amount, subject) |
| `data/WriteResult.kt` | Sealed class: Success / NetworkError / AuthError / SheetsError |
| `data/PrefsKeys.kt` | SharedPreferences helper |

### Dependencies (libs.versions.toml)
- `play-services-auth:21.2.0`
- `google-api-services-sheets:v4-rev20260213-2.0.0`
- `google-api-client-android:2.6.0`
- `google-http-client-jackson2:1.44.2`

---

## How to Install (for future sessions)

The user cannot use ADB directly (play button greyed out in Android Studio). Workaround:
1. Build APK: run `testReleaseUnitTest` or press ▶ — the APK lands at:
   `D:\CashLog\android\app\build\outputs\apk\debug\app-debug.apk`
2. Transfer to phone via WhatsApp/Telegram self-message
3. Install on phone (unknown sources must be allowed)

---

## What's Left: Play Store Publishing

1. **Create release keystore** — production signing key (not debug)
2. **Update SHA-1** in Google Cloud Console with release keystore fingerprint
3. **Build signed release AAB** (`bundleRelease`)
4. **Complete Google OAuth verification** — required because the app accesses Google Sheets (sensitive scope); submit privacy policy + demo video to Google
5. **Google Play Developer account** — $25 one-time fee at play.google.com/console
6. **Store listing** — app icon, screenshots, description
7. **App icon** — currently uses default Android icon; needs a real CashLog icon

---

## Known Issues / Notes

- App installed as **debug APK** — not production signed; fine for personal use, not for Play Store
- The ADB/USB install via Android Studio doesn't work on user's Xiaomi (play button greyed out) — sideload workaround works fine
- Unit tests: 36 tests, all passing
- The `namespace` in build.gradle.kts is still `com.maru.expenserecorder` (internal only); `applicationId` is `com.maru.cashlog` (what matters externally)
