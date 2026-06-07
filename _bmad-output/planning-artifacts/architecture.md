---
stepsCompleted: [1, 2, 3, 4]
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
