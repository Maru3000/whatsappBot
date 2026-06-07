---
title: Android Voice Expense Recorder
status: final
created: 2026-06-07
updated: 2026-06-07
---

# Android Voice Expense Recorder — PRD

## 1. Problem Statement

Recording cash expenses on the go is too slow. The current workflow — open Google Keep, create or find a note, type the amount and description — takes 6+ steps during a moment when speed matters (paying for a taxi, grabbing lunch). Entries get skipped or forgotten.

The goal is to reduce cash expense capture to a single tap + one spoken sentence, with no further action required.

---

## 2. Goals & Success Metrics

| Goal | Metric |
|------|--------|
| Capture speed | Tap-to-confirmed in ≤ 5 seconds |
| Zero typing | 100% of entries recorded by voice |
| Adoption | Replaces Google Keep as primary cash expense log within first week of use |
| Data integrity | Every spoken entry appears as a correctly parsed row in OneDrive Excel |

**Counter-metric:** If parsing accuracy drops below ~90% (wrong amount or garbled subject), the frictionless promise breaks and users revert to manual entry.

---

## 3. User & Context

**Solo user** (Root) — personal cash expense tracking, Israel, currency ILS (₪).

**Capture context:** On the move — taxi, restaurant, market. One hand often occupied. No time to type. Phone in the other hand.

**Review context:** Desktop or mobile, opening OneDrive. Wants a clean monthly breakdown to understand where cash went.

---

## 4. User Journey

**Named protagonist: Root, paying for a taxi**

1. Gets change from driver. Pulls out phone.
2. Taps the expense widget on the home screen.
3. Microphone opens immediately — no extra taps.
4. Says: *"50 taxi"*
5. App auto-stops after a short silence, transcribes, parses: ₪50 / "taxi" / 2026-06-07 / 14:32.
6. Row is appended to the "June 2026" sheet in `Expenses.xlsx` on OneDrive.
7. A brief success toast appears. Done. Phone back in pocket.

Total time: ~4 seconds. Zero typing.

**Edge: wrong transcription.** Root notices "taxi" became "taxis" when reviewing in Excel. Opens Excel, edits the cell. No in-app correction flow needed.

---

## 5. Functional Requirements

### FR-1 — Home Screen Widget

- **FR-1.1** The app provides a home screen widget (1×1 minimum size, resizable).
- **FR-1.2** A single tap on the widget launches the voice capture flow immediately — no app main screen, no intermediary screen.
- **FR-1.3** The widget displays the app icon and a short label (e.g., "Expense").

### FR-2 — Voice Capture

- **FR-2.1** Microphone activates within 1 second of widget tap.
- **FR-2.2** Recording stops automatically after detecting end-of-speech (silence threshold ~1.5 s). No button press to stop.
- **FR-2.3** A visual recording indicator is shown while the mic is active (e.g., animated waveform or pulsing icon).
- **FR-2.4** The user can cancel the recording with a tap or back gesture before submission.

### FR-3 — Parsing

- **FR-3.1** The transcribed text is parsed for: (a) a numeric amount — the **first** number found, integer or decimal, regardless of other numbers in the utterance (e.g. "50 for 3 coffees" → amount ₪50, subject "for 3 coffees"); (b) a subject — the full remaining text after removing the leading number, stored exactly as transcribed, no normalisation or category mapping.
- **FR-3.2** Currency is always ILS (₪). No currency detection or multi-currency support.
- **FR-3.3** Date and time are captured from the device clock at the moment the recording stops.
- **FR-3.4** If no numeric amount can be extracted, the entry is rejected and the user is shown an error with the option to re-record. [ASSUMPTION: a re-record prompt is sufficient; no manual fallback text input needed.]

### FR-4 — OneDrive Excel Integration

- **FR-4.1** The user can configure the target Excel file path (filename and OneDrive folder) from the settings screen. The default on first run is `Expenses.xlsx` in the OneDrive root. A file picker or manual path entry is provided.
- **FR-4.2** Each calendar month has its own sheet, named `MMM YYYY` (e.g., `Jun 2026`). If the sheet does not exist, the app creates it automatically with a header row.
- **FR-4.3** Each row in the sheet contains four columns in this order: `Date` (DD/MM/YYYY) | `Time` (HH:MM) | `Amount` (numeric, no currency symbol) | `Subject` (text).
- **FR-4.4** New rows are appended at the end of the sheet — no sorting, no overwriting.
- **FR-4.5** A visible success notification (Android toast or snackbar) is shown after the row is written. If the write fails, an error notification is shown with a retry option. [ASSUMPTION: on retry failure, entry is silently dropped — no offline queue in v1.]

### FR-5 — Authentication

- **FR-5.1** The user signs in with a Microsoft account (personal or work) via MSAL on first launch.
- **FR-5.2** The auth token is persisted; the user is not prompted to sign in again unless the token expires or is revoked.
- **FR-5.3** A settings screen (reachable from the app, not the widget) allows the user to: sign out and sign in with a different account; configure the target Excel file path (FR-4.1).

---

## 6. Non-Functional Requirements

| Concern | Requirement |
|---------|-------------|
| **Speed** | Tap → confirmed write ≤ 5 s on a standard LTE connection |
| **Reliability** | On network failure or connectivity loss at any point in the flow, display a clear error notification and offer retry; do not silently lose entries. If the app terminates after STT completes but before the OneDrive write succeeds, this is an acknowledged v1 data-loss risk (no persistent queue). |
| **Privacy** | Voice audio is processed via Android's built-in STT; raw audio is never stored or transmitted to a third-party server |
| **Permissions** | Request only: `RECORD_AUDIO`, `INTERNET`. No location, contacts, or storage beyond OneDrive API calls |
| **Platform** | Android 8.0 (API 26) and above |
| **Offline** | Not supported in v1 — requires network for OneDrive write |

---

## 7. Out of Scope (v1)

- Editing or deleting entries within the app
- Category inference or tagging
- Multi-currency support
- Budget limits or alerts
- Charts or summaries within the app
- iOS version
- Sharing or export beyond OneDrive
- WhatsApp integration

---

## 8. Open Questions

| # | Question | Owner | Revisit |
|---|----------|-------|---------|
| ~~OQ-1~~ | ~~Should `Expenses.xlsx` filename/location be configurable?~~ | ~~Root~~ | **Resolved:** Configurable via settings screen; default is root/`Expenses.xlsx`. |
| OQ-2 | Android built-in SpeechRecognizer vs. cloud STT API — accuracy trade-off to evaluate | Architect | Before FR-3 implementation |
