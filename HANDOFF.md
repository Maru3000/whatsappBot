# CashLog — Session Handoff

## What is this app

**CashLog** — an Android home-screen widget app that records cash expenses and work income by voice, saves everything to Google Sheets (one tab per month), and shows a color-coded list view.

- Package: `com.maru.cashlog`
- Repo branch: `claude/android-voice-expense-recorder-B4Hyp`
- Android project path: `android/` inside this repo
- Tested on: Xiaomi phone via WiFi debugging (Android Studio)

---

## Current Feature State (all working)

| Feature | Status |
|---|---|
| Home-screen widget tap → voice recording | ✅ |
| Voice stops automatically once amount + subject heard | ✅ |
| Hebrew + English expense/income voice parsing | ✅ |
| Saves to Google Sheets (tab = "Jun 2026" format) | ✅ |
| Auto-creates new tab when month changes | ✅ |
| Expenses list screen — two sections (Work Income / Cash Expenses) | ✅ |
| Work Income section: green header, +₪X total | ✅ |
| Cash Expenses section: red header, -₪X total | ✅ |
| Manual entry via FAB (+ button) with Cash Expense / Work Income radio | ✅ |
| Refresh button at top of list | ✅ |

---

## Key Files

```
android/app/src/main/java/com/maru/expenserecorder/
  ExpenseParser.kt          — voice text → ParsedExpense (amount, description, type)
  RecordingActivity.kt      — speech recognition, auto-stop on valid parse
  ExpensesActivity.kt       — two-section list screen
  ExpenseListAdapter.kt     — RecyclerView adapter (green/red color coding)
  data/
    Expense.kt              — data class (date, time, amount, subject, type)
    ExpenseRepository.kt    — Google Sheets read/write (tab = month name)

android/app/src/main/res/layout/
  activity_expenses.xml     — two-section NestedScrollView layout
  dialog_add_expense.xml    — manual entry dialog
  item_expense.xml          — single row in the list

android/app/build.gradle.kts   — release build config (R8 enabled, signing via env vars)
android/app/proguard-rules.pro — ProGuard rules for Google API + Jackson
```

---

## Google Sheets Data Format

Each monthly tab (e.g. "Jun 2026") has columns:

| A: Date | B: Time | C: Amount | D: Subject | E: Type |
|---|---|---|---|---|
| 16/06/2026 | 14:35 | 50 | Taxi | expense |
| 16/06/2026 | 15:00 | 3500 | Salary | income |

- Type is always lowercase: `"expense"` or `"income"`
- Tab is auto-created with header row if it doesn't exist
- Range read: `A:E`

---

## Hebrew Income Keywords (ExpenseParser.kt)

Voice input in Hebrew is detected as income if any token **contains** one of:

```
הכנסה, הכנסות, משכורת, תשלום, קיבלתי, קיבל, הרווחתי, הרוויחתי, שכר, רווח, פרילנס
```

Also: English (`income`, `salary`, `received`, …) and transliterations (`miskoret`, `kibel`, …).

---

## Known Limitations / Pending Work

1. **Can only view current month** — no previous month navigation in the list screen. Data for all months is in Google Sheets tabs.
2. **OAuth still in Testing mode** — only accounts added in Google Cloud Console can sign in. Needs production OAuth verification for public use.

---

## Play Store Publishing — Status & Remaining Steps

### Done (code-side)
- [x] R8 minification enabled
- [x] ProGuard rules for Google API libraries
- [x] Signing config reads from env vars (no secrets in git)
- [x] `versionCode = 1`, `versionName = "1.0"`
- [x] `applicationId = "com.maru.cashlog"`

### Still needed

#### Step 1 — Create release keystore (developer's machine)
```bash
keytool -genkey -v \
  -keystore cashlog-release.keystore \
  -alias cashlog \
  -keyalg RSA -keysize 2048 -validity 10000
```
Keep this file + passwords safe forever.

#### Step 2 — Google Play Developer account
- Register at play.google.com/console — one-time $25 fee

#### Step 3 — Google Cloud Console: OAuth for production
- console.cloud.google.com → your project → OAuth consent screen
- Switch from **Testing → Production**
- Requires a **Privacy Policy URL**
- Google Sheets scope is sensitive → submit for **OAuth verification** (takes 4–6 weeks)
- Need to provide: privacy policy page, demo video of the app

#### Step 4 — Store assets to create
| Asset | Spec |
|---|---|
| App icon | 512×512 PNG |
| Feature graphic | 1024×500 PNG |
| Phone screenshots | at least 2 |
| Short description | ≤ 80 chars |
| Full description | ≤ 4000 chars |

#### Step 5 — Build signed AAB
Android Studio → Build → Generate Signed Bundle/APK → Android App Bundle → release

#### Step 6 — Upload & publish
Play Console → create app → upload AAB → store listing → content rating → publish

---

## How to Resume Development

1. Open Android Studio with the `android/` project
2. Make sure device is connected via WiFi debugging
3. Pull latest: **Git → Pull** from branch `claude/android-voice-expense-recorder-B4Hyp`
4. Press Run

To continue with Claude: paste this file into the conversation or reference it.
