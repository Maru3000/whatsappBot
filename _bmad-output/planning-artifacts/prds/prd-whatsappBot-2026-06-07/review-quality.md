# PRD Quality Review — Android Voice Expense Recorder
**Reviewed:** 2026-06-07  
**Verdict:** PASS-WITH-NOTES

---

## Rubric Assessment

### 1. Problem clearly stated, user identified? — PASS
The problem is concrete, the user (Root, solo, Israel, ILS) is well-defined, and the capture context (taxi, restaurant, market, one hand occupied) grounds the product in real behavior. The 6-step baseline gives a credible "from" state.

### 2. Success metrics measurable and have counter-metrics? — PASS-WITH-NOTES
| Metric | Assessment |
|--------|------------|
| Tap-to-confirmed ≤ 5s | Measurable; measurement method not specified (stopwatch? instrumentation?) |
| 100% zero-typing | Binary and testable |
| Replaces Google Keep within 1 week | Measurable but relies on self-reported behavior; no tracking mechanism stated |
| Every spoken entry correctly parsed in OneDrive | Testable |
| Counter-metric: <90% parsing accuracy → regression | Good; threshold stated |

**Gap:** No baseline for current Google Keep usage frequency is given, making the "replaces within one week" metric hard to validate objectively.

### 3. FRs complete, unambiguous, implementation-free? — PASS-WITH-NOTES
FRs are well-scoped and largely implementation-free. Issues:
- **FR-2 (Voice capture):** "auto-stop on silence" — silence threshold/duration not specified. Architect will need to guess or go back to the PM. Recommend stating a target (e.g., "stops after ~1.5s of silence").
- **FR-3 (Parsing):** "rest = subject raw" is clear, but behavior on multiple numbers in the utterance (e.g., "50 for 3 coffees") is undefined. Recommend a tie-breaking rule (first number = amount is stated, but "3 coffees" still has a second number; clarify whether "3" is stripped or kept in subject).
- **FR-4 (OneDrive):** "monthly sheets" — naming convention for sheets not specified (e.g., "June 2026", "2026-06"). Omission will cause inconsistency if user switches devices or reinstalls.
- **FR-5 (Auth):** "configurable path" — no constraint on path format or validation rules; error behavior for a bad path is unspecified (toast? silent fail?).

### 4. NFRs cover the real risks of this product? — FAIL (minor)
Stated NFRs (speed, no external audio, Android 8+, no offline, 2 permissions) address the basics. Missing:
- **Reliability on poor connectivity:** The "no offline" stance is acknowledged, but there is no NFR for graceful degradation messaging. A user in a tunnel who taps the widget needs to know it failed — not silently drop the entry.
- **OneDrive API rate limits / quota:** No NFR addresses what happens if OneDrive write fails (quota full, token expired mid-session). FR-4 mentions "error toast" but this is a functional behavior, not a reliability target.
- **Battery / background behavior:** Widget launch on Android involves foreground service or activity; no NFR guards against excessive battery drain (relevant for a daily-use widget).
- **Data loss prevention:** No NFR states that a parsed entry must not be silently lost (e.g., if the app crashes after STT but before the OneDrive write).

### 5. Scope boundary clear — what's in and what's out? — PASS
Out-of-scope list is explicit and well-chosen. "WhatsApp" in the out-of-scope list is slightly confusing given the repo name (whatsappBot), but presumably intentional. No ambiguity about v1 boundaries.

### 6. Gaps, contradictions, or missing requirements? — PASS-WITH-NOTES
- **Gap — Error recovery flow:** The user journey edge case ("wrong transcription → edits in Excel directly") is noted but no in-app correction path is explicitly excluded vs. forgotten. FR-3 only handles rejection (no number found). What happens to a near-miss (amount parsed wrong)? User has no way to know without opening OneDrive.
- **Gap — Notification/confirmation persistence:** "Success toast" is ephemeral. If the user's attention is split (paying, getting change), they may miss it. No requirement for a persistent notification or history log is specified, nor is this explicitly out-of-scope.
- **Gap — Concurrent entries:** No requirement addresses rapid sequential entries (user records two expenses quickly). Append-only model likely handles this, but race conditions on the Excel file are unaddressed.
- **No contradiction found** between sections.

---

## Summary of Top Findings

| # | Finding | Severity |
|---|---------|----------|
| 1 | NFR gap: no graceful degradation requirement for offline/connectivity failure — silent data loss is possible | Medium |
| 2 | FR-2: silence threshold duration unspecified — implementation will require a guess | Low-Medium |
| 3 | FR-3: behavior on utterances with multiple numbers (e.g., "50 for 3 coffees") is undefined | Low-Medium |
| 4 | NFR gap: no data-loss prevention guarantee between STT completion and OneDrive write | Medium |
| 5 | FR-4: monthly sheet naming convention unspecified — risk of inconsistency across installs | Low |

---

## Recommendation
Address findings #1 and #4 (data integrity NFRs) before handing to architect — these are the highest-risk gaps. Findings #2, #3, and #5 can be resolved in architect/dev handoff notes. PRD is otherwise solid for a single-user v1 product.
