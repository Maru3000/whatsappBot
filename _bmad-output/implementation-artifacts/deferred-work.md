# Deferred Work

## Story 1 Review Deferrals

### PERF-1: `Sheets` service instance created on every `appendExpense` call

**Source:** Blind Hunter review of Story 1
**File:** `android/app/src/main/java/com/maru/expenserecorder/data/ExpenseRepository.kt`
**Issue:** A new `Sheets` service (including `NetHttpTransport` with its connection pool) is constructed on every call to `appendExpense`. This is wasteful and does not reuse HTTP connections, adding latency on every write.
**Recommendation:** Cache the `Sheets` instance keyed by credential/spreadsheetId, or make `ExpenseRepository` hold a lazily-initialized service that is refreshed only when the account changes.
**When to address:** Story 5 (ExpenseRepository implementation refinement) or a future performance story.
