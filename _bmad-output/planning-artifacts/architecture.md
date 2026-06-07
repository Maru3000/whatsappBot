---
stepsCompleted: [1, 2]
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
