---
stepsCompleted: [1, 2, 3, 4, 5, 6]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-whatsappBot-2026-06-07/prd.md
notes: 'Storage: Google Sheets API v4. Auth: Google Sign-In OAuth 2.0. Target spreadsheet ID: 1RErU26Ln2-uW4FMxezn_OZ5WWkvXVvYU. MSAL/OneDrive stack dropped.'
workflowType: 'architecture'
project_name: 'whatsappBot'
user_name: 'Root'
date: '2026-06-07'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
18 FRs across 5 groups: Widget (tap-to-launch, no intermediary screen), Voice Capture (SpeechRecognizer, auto-stop on silence ~1.5s, cancel), Parsing (first-number rule, raw subject, ILS fixed, device timestamp), Google Sheets Integration (configurable spreadsheet ID, monthly tabs `MMM YYYY`, 4-column append-only rows, success/error toast), Authentication (Google Sign-In OAuth 2.0, persistent token, settings screen).

**Non-Functional Requirements:**
≤5s tap-to-confirmed; clear error on any network failure (no silent loss); on-device STT only (privacy); `RECORD_AUDIO` + `INTERNET` permissions only; Android 8.0+ (API 26+); no offline support in v1.

**Scale & Complexity:**
- Primary domain: Android mobile (single-device, single-user utility)
- Complexity level: Low
- Integration surface: Google Sign-In SDK + Google Sheets API v4
- Data pattern: append-only, no local queries, no sync engine

### Technical Constraints & Dependencies

- Existing Android project scaffold uses MSAL — must be replaced with Google Sign-In SDK and Sheets API v4 client libraries.
- Android `SpeechRecognizer` requires Google Play Services (standard on any non-AOSP device).
- Widget tap must survive app-process cold start — `VoiceCaptureActivity` must be fully self-contained.
- OQ-2 resolved: Android `SpeechRecognizer` chosen over cloud STT (on-device, no extra API key, meets privacy NFR, handles silence detection natively).

### Cross-Cutting Concerns Identified

1. **Auth token refresh** — must be transparent; user cannot be redirected to a sign-in screen mid-capture flow.
2. **Network state** — check connectivity before attempting Sheets write; surface clear error if offline.
3. **Widget cold-start lifecycle** — `VoiceCaptureActivity` must initialise and open mic in <1s from a dead process.
4. **Runtime permission** — `RECORD_AUDIO` must be requested gracefully; widget tap with permission denied must show a clear prompt, not crash.

## Starter Template Evaluation

### Primary Technology Domain

Native Android — existing Kotlin/Gradle scaffold reused. No new project initialisation needed.

### Foundation Decision: Adapt Existing Scaffold

The existing scaffold (AGP 8.3.2 / Kotlin 1.9.23 / ViewBinding) is retained as-is.

**Dependency Changes:**

| Action | Library |
|--------|---------|
| Remove | `msal:5.3.0` |
| Remove | `okhttp:4.12.0` |
| Remove | `room-runtime`, `room-ktx`, `room-compiler`, `ksp` plugin |
| Add | `play-services-auth:21.2.0` — Google Sign-In OAuth 2.0 |
| Add | `google-api-services-sheets:v4-rev20240422-2.0.0` — Sheets API v4 |
| Add | `google-api-client-android:2.6.0` — Android HTTP transport |
| Add | `google-http-client-jackson2:1.44.2` — JSON serialisation |

### Architectural Pattern: MVVM (manual DI)

| Component | Role |
|-----------|------|
| `ExpenseWidget` | `AppWidgetProvider` — handles widget tap, fires `VoiceCaptureActivity` |
| `VoiceCaptureActivity` | Owns mic lifecycle, drives the full capture flow |
| `SettingsActivity` | Sign-out + spreadsheet ID configuration |
| `ExpenseRepository` | Single source of truth for Google Sheets writes |
| `GoogleAuthManager` | Google Sign-In token acquisition and transparent refresh |
| `ExpenseParser` | Pure Kotlin — first-number parse rule, no dependencies |

**Language & Runtime:** Kotlin, JVM 17, coroutines for all async operations
**Build Tooling:** Gradle Kotlin DSL, `libs.versions.toml` version catalog
**Testing:** Unit tests for `ExpenseParser`; no UI test suite in v1
**Note:** Dependency migration is the first implementation story.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- Activity presentation: dialog/transparent overlay
- Credential persistence: SharedPreferences
- Error state model: sealed `WriteResult` class

**Deferred Decisions (Post-MVP):**
- Play Store release signing
- CI/CD pipeline
- Offline queue / retry persistence

### Data Architecture

- **No local database** — append-only writes direct to Google Sheets; no local cache or queue in v1.
- **Data model:** `data class Expense(val date: String, val time: String, val amount: Double, val subject: String)`
- **Validation:** `ExpenseParser` rejects utterances with no numeric amount; all other validation is implicit (device clock for timestamp, fixed ILS currency).
- **Settings persistence:** `SharedPreferences` — spreadsheet ID stored under a single key; read once at activity start.

### Authentication & Security

- **Google Sign-In OAuth 2.0** — scope: `https://www.googleapis.com/auth/spreadsheets`
- **Token persistence:** managed automatically by Google Sign-In SDK (no manual keystore work).
- **Transparent refresh:** `GoogleAuthManager` calls `GoogleSignIn.getLastSignedInAccount()` before every Sheets operation; if token is stale, triggers silent re-auth before proceeding. User is never interrupted mid-capture.
- **Permissions:** `RECORD_AUDIO` + `INTERNET` only. Requested at runtime on first widget tap if not yet granted.

### API & Communication Patterns

- **Google Sheets API v4** via `google-api-client-android` transport — no custom HTTP client.
- **Error handling:** `ExpenseRepository` returns `WriteResult` (sealed class):
  - `WriteResult.Success`
  - `WriteResult.NetworkError`
  - `WriteResult.AuthError`
  - `WriteResult.SheetsError(message: String)`
- **Single retry** on `NetworkError` before surfacing error toast (per NFR).
- No rate-limiting concern — personal use, ≤ a few writes per day.

### Android UI Architecture

- **`VoiceCaptureActivity`** uses a dialog/transparent overlay theme (`Theme.AppCompat.Dialog` or custom translucent window). Home screen remains visible; mic animation floats over it. Finishes immediately after toast confirmation — no back-stack residue.
- **`SettingsActivity`** — full-screen, standard theme. Launched from app launcher only (not from widget).
- **State:** `VoiceCaptureViewModel` holds a `StateFlow<CaptureState>` (`Idle → Listening → Processing → Done(result)`). Survives orientation change.
- **Navigation:** Intent-based only. No Jetpack Navigation needed.

### Infrastructure & Deployment

- **Distribution:** APK sideload (personal use — no Play Store in v1).
- **Build variants:** `debug` only for v1; release signing deferred.
- **No CI/CD** in v1.
- **Logging:** Android `Logcat` only; no remote logging service.

### Decision Impact Analysis

**Implementation Sequence:**
1. Dependency migration (remove MSAL/Room/OkHttp; add Google Sign-In + Sheets API)
2. `GoogleAuthManager` + sign-in flow + `SettingsActivity`
3. `ExpenseWidget` + `VoiceCaptureActivity` shell (dialog theme, mic permission)
4. `SpeechRecognizer` integration + `ExpenseParser`
5. `ExpenseRepository` + Sheets API write
6. End-to-end wiring + error toasts
7. `ExpenseParser` unit tests

**Cross-Component Dependencies:**
- `VoiceCaptureActivity` depends on `GoogleAuthManager` being initialised (token available) before attempting a write.
- `ExpenseWidget` has no direct dependency on any repository — it only fires an Intent to `VoiceCaptureActivity`.
- `ExpenseRepository` is the only component that touches the network; all others are pure or UI-only.

## Implementation Patterns & Consistency Rules

### Naming Patterns

**Kotlin code (all agents MUST follow):**
- Classes/Objects: `PascalCase` — `ExpenseRepository`, `WriteResult`
- Functions/variables: `camelCase` — `appendExpense()`, `spreadsheetId`
- Constants: `SCREAMING_SNAKE_CASE` in companion object — `DEFAULT_SPREADSHEET_ID`
- Package: `com.maru.expenserecorder.<layer>` — e.g. `com.maru.expenserecorder.data`

**Android resources (`snake_case` — enforced by Android tooling):**
- Layouts: `activity_voice_capture.xml`, `widget_expense.xml`
- Strings: `error_network_unavailable`, `label_recording`
- View IDs: `btn_cancel`, `tv_status`

**SharedPreferences keys:** defined as constants in a `PrefsKeys` object — `PREF_SPREADSHEET_ID`, `PREF_ACCOUNT_NAME`. Never use inline strings.

### Package Structure

```
com.maru.expenserecorder/
  widget/        — ExpenseWidget (AppWidgetProvider)
  ui/
    capture/     — VoiceCaptureActivity, VoiceCaptureViewModel, CaptureState
    settings/    — SettingsActivity
  data/
    ExpenseRepository.kt
    WriteResult.kt
    Expense.kt
  auth/          — GoogleAuthManager
  parser/        — ExpenseParser
```

### Format Patterns

**Date/time (match PRD exactly — no deviations):**
- Date stored in sheet: `DD/MM/YYYY` — use `DateTimeFormatter.ofPattern("dd/MM/yyyy")`
- Time stored in sheet: `HH:MM` — use `DateTimeFormatter.ofPattern("HH:mm")`
- Amount stored: `Double`, no currency symbol in the cell
- Always use `java.time` — never `SimpleDateFormat` (not thread-safe)

**Canonical sealed classes (define once, never redefine):**

```kotlin
sealed class WriteResult {
    object Success : WriteResult()
    object NetworkError : WriteResult()
    object AuthError : WriteResult()
    data class SheetsError(val message: String) : WriteResult()
}

sealed class CaptureState {
    object Idle : CaptureState()
    object Listening : CaptureState()
    object Processing : CaptureState()
    data class Done(val result: WriteResult) : CaptureState()
}
```

### Process Patterns

**Coroutine scopes:**
- `viewModelScope` — all ViewModel-launched coroutines
- `lifecycleScope` — Activity UI collection only
- Repositories expose `suspend fun` only — never launch coroutines internally

**Error handling flow (single path — no raw exceptions crossing layer boundaries):**
```
SpeechRecognizer callback → ViewModel → ExpenseRepository
  (returns WriteResult) → ViewModel updates CaptureState
  → Activity collects and shows toast
```
All Sheets API exceptions are caught inside `ExpenseRepository` and mapped to `WriteResult`. Nothing escapes as a raw exception.

**UI state rules:**
- `CaptureState.Listening` → animated mic indicator
- `CaptureState.Processing` → spinner (mic animation stops)
- `CaptureState.Done(Success)` → green toast, finish Activity
- `CaptureState.Done(Error)` → red toast with retry option, stay open

### Enforcement Guidelines

**All agents MUST:**
- Use `WriteResult` and `CaptureState` exactly as defined — no new result types
- Format date/time with the exact pattern strings above
- Read spreadsheet ID from `PrefsKeys.PREF_SPREADSHEET_ID` — never hardcode
- Launch coroutines from ViewModel (`viewModelScope`) or Activity (`lifecycleScope`) only
- Catch all Sheets API exceptions inside `ExpenseRepository` only

**Anti-patterns to avoid:**
- `SimpleDateFormat` — use `java.time` only
- Inline SharedPreferences key strings — always use `PrefsKeys`
- Throwing exceptions from `ExpenseRepository` — map to `WriteResult`
- Network calls from Activity or ViewModel — only via Repository

## Project Structure & Boundaries

### Complete Project Directory Structure

```
android/
├── gradle/
│   ├── libs.versions.toml                    ← version catalog (updated in story 1)
│   └── wrapper/gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts                      ← dependency declarations
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml           ← widget receiver, activities, permissions
│       │   ├── java/com/maru/expenserecorder/
│       │   │   ├── widget/
│       │   │   │   └── ExpenseWidget.kt      ← AppWidgetProvider (FR-1)
│       │   │   ├── ui/
│       │   │   │   ├── capture/
│       │   │   │   │   ├── VoiceCaptureActivity.kt   ← dialog-theme, mic flow (FR-2)
│       │   │   │   │   ├── VoiceCaptureViewModel.kt  ← StateFlow<CaptureState>
│       │   │   │   │   └── CaptureState.kt           ← sealed class (canonical)
│       │   │   │   └── settings/
│       │   │   │       └── SettingsActivity.kt       ← sign-out + spreadsheet ID (FR-5)
│       │   │   ├── data/
│       │   │   │   ├── Expense.kt                    ← data class (FR-3)
│       │   │   │   ├── WriteResult.kt                ← sealed class (canonical)
│       │   │   │   └── ExpenseRepository.kt          ← Sheets API writes (FR-4)
│       │   │   ├── auth/
│       │   │   │   └── GoogleAuthManager.kt          ← Google Sign-In, token refresh (FR-5)
│       │   │   └── parser/
│       │   │       └── ExpenseParser.kt              ← first-number rule (FR-3)
│       │   └── res/
│       │       ├── layout/
│       │       │   ├── activity_voice_capture.xml
│       │       │   └── activity_settings.xml
│       │       ├── xml/
│       │       │   └── expense_widget_info.xml       ← AppWidgetProviderInfo
│       │       ├── drawable/
│       │       │   └── ic_expense_widget.xml
│       │       └── values/
│       │           ├── strings.xml
│       │           └── themes.xml                    ← includes dialog theme for capture
│       └── test/
│           └── java/com/maru/expenserecorder/
│               └── parser/
│                   └── ExpenseParserTest.kt          ← unit tests for FR-3
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### Architectural Boundaries

**Widget boundary:** `ExpenseWidget` has zero imports from `data/` or `auth/`. It only fires an explicit `Intent` targeting `VoiceCaptureActivity`. No logic.

**Auth boundary:** `GoogleAuthManager` is the single point of contact for Google identity. Both `VoiceCaptureActivity` (permission check on start) and `ExpenseRepository` (token for API calls) depend on it — nothing else does.

**Data boundary:** `ExpenseRepository` is the only file that imports Google Sheets API classes. All callers receive `WriteResult` — they never see API types.

**Parser boundary:** `ExpenseParser` has no Android imports — pure Kotlin. Takes a `String`, returns `Expense?` (null if no number found). Fully unit-testable.

### Requirements to Structure Mapping

| FR Group | Primary Files |
|----------|--------------|
| FR-1 — Widget | `widget/ExpenseWidget.kt`, `res/xml/expense_widget_info.xml`, `AndroidManifest.xml` |
| FR-2 — Voice Capture | `ui/capture/VoiceCaptureActivity.kt`, `VoiceCaptureViewModel.kt` |
| FR-3 — Parsing | `parser/ExpenseParser.kt`, `data/Expense.kt` |
| FR-4 — Sheets Integration | `data/ExpenseRepository.kt`, `data/WriteResult.kt` |
| FR-5 — Auth & Settings | `auth/GoogleAuthManager.kt`, `ui/settings/SettingsActivity.kt` |

### Data Flow

```
Widget tap
  → Intent → VoiceCaptureActivity.onCreate()
    → check RECORD_AUDIO permission
    → start SpeechRecognizer
      → onResults(transcript)
        → VoiceCaptureViewModel.onTranscription(transcript)
          → ExpenseParser.parse(transcript) → Expense?
          → ExpenseRepository.appendExpense(expense)
            → GoogleAuthManager.getToken()
            → Sheets API v4 append row
            → WriteResult
          → CaptureState.Done(result)
        → Activity collects state → toast → finish()
```

### External Integrations

| Service | Entry Point | Scope |
|---------|-------------|-------|
| Google Sign-In SDK | `GoogleAuthManager` | `spreadsheets` OAuth scope |
| Google Sheets API v4 | `ExpenseRepository` | Append rows to monthly tab |
| Android SpeechRecognizer | `VoiceCaptureActivity` | On-device STT, no extra API key |
