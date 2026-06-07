---
title: 'Story 1 — Migrate from OneDrive/MSAL/Room to Google Sheets/Google Sign-In'
type: 'refactor'
created: '2026-06-07'
status: 'draft'
context:
  - '_bmad-output/planning-artifacts/architecture.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The app currently writes expenses to OneDrive via MSAL + OkHttp + Room. Storage has been changed to Google Sheets; all Microsoft-stack code must be removed and replaced with Google Sign-In + Sheets API v4.

**Approach:** Delete Microsoft-stack files, swap dependencies in Gradle, create `GoogleAuthManager`, `WriteResult`, `Expense` data class, and `ExpenseRepository`; refactor `ExpenseApp`, `RecordingActivity`, `MainActivity`, and `ExpenseWidget` to use the new stack; register new activities in `AndroidManifest.xml`.

## Boundaries & Constraints

**Always:**
- Keep `ExpenseParser.kt` unchanged — it is pure Kotlin, already correct, handles Hebrew + English number words.
- `ExpenseWidget.kt` tap intent must still reach a recording/capture activity — only the target class name changes.
- `RecordingActivity.kt` SpeechRecognizer logic (he-IL,en-US, silence detection, waveform animation) is reused — only the save path changes.
- Currency is always ILS; no multi-currency.
- `java.time` for date/time (`DateTimeFormatter`); `SimpleDateFormat` is forbidden.
- No local database (Room removed); no offline queue in v1.
- Spreadsheet ID is read from `SharedPreferences` (`PrefsKeys.PREF_SPREADSHEET_ID`); default value `1RErU26Ln2-uW4FMxezn_OZ5WWkvXVvYU`.

**Ask First:**
- If Google Sheets API throws a non-retriable error type not covered by `WriteResult` (e.g. quota exceeded), halt and ask how to surface it.
- If `AndroidManifest.xml` already declares activities that conflict with the new names, halt and ask.

**Never:**
- Do not add Room, MSAL, OkHttp, or any Microsoft-stack dependency.
- Do not create an in-app expense list UI (no RecyclerView, no expense viewer).
- Do not add offline queuing, local DB, or background sync.
- Do not hardcode the spreadsheet ID — it must come from SharedPreferences.
- Do not use `SimpleDateFormat`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Successful write | Signed-in, network available, valid spreadsheet ID, parsed expense | Row appended to correct monthly tab; toast shown | N/A |
| Sheet tab missing | Month tab does not exist yet | App creates tab with header row `Date\|Time\|Amount\|Subject`, then appends row | Surface error toast if creation fails |
| Network error | No connectivity at write time | `WriteResult.NetworkError` returned; error toast + retry option shown | User taps retry → attempt write again |
| Auth expired | Token revoked/expired at write time | `WriteResult.AuthError`; prompt user to re-sign-in via settings | Do not silently drop entry |
| No amount parsed | STT returns text with no number | `ExpenseParser.parse()` returns null; show "Could not parse amount — re-record?" dialog | Cancel or re-record |

</frozen-after-approval>

## Code Map

- `android/gradle/libs.versions.toml` -- add Google deps, remove msal/okhttp/room/ksp versions
- `android/app/build.gradle.kts` -- swap plugins + dependencies, remove MSAL packaging hacks
- `android/app/src/main/java/com/maru/expenserecorder/auth/MicrosoftAuthManager.kt` -- DELETE
- `android/app/src/main/java/com/maru/expenserecorder/OneDriveSync.kt` -- DELETE
- `android/app/src/main/java/com/maru/expenserecorder/MinimalXlsx.kt` -- DELETE
- `android/app/src/main/java/com/maru/expenserecorder/database/` -- DELETE entire directory (Expense.kt, ExpenseDao.kt, ExpenseDatabase.kt)
- `android/app/src/main/java/com/maru/expenserecorder/ExpenseAdapter.kt` -- DELETE
- `android/app/src/main/java/com/maru/expenserecorder/auth/GoogleAuthManager.kt` -- CREATE: wraps Google Sign-In, exposes `getAccessToken(): String?`, `signOut()`
- `android/app/src/main/java/com/maru/expenserecorder/data/WriteResult.kt` -- CREATE: sealed class `WriteResult { Success; NetworkError(msg); AuthError; SheetsError(msg) }`
- `android/app/src/main/java/com/maru/expenserecorder/data/Expense.kt` -- CREATE: simple data class `Expense(date, time, amount, subject)` — no Room annotations
- `android/app/src/main/java/com/maru/expenserecorder/data/ExpenseRepository.kt` -- CREATE: suspend fun `appendExpense(expense, accessToken, spreadsheetId): WriteResult`; creates monthly tab if missing; appends row
- `android/app/src/main/java/com/maru/expenserecorder/ExpenseApp.kt` -- REFACTOR: replace `MicrosoftAuthManager` with `GoogleAuthManager`
- `android/app/src/main/java/com/maru/expenserecorder/RecordingActivity.kt` -- REFACTOR: remove Room save + OneDriveSync; call `ExpenseRepository.appendExpense()`; show success/error toast; keep SpeechRecognizer as-is
- `android/app/src/main/java/com/maru/expenserecorder/MainActivity.kt` -- REFACTOR: remove expense RecyclerView + Room queries; pivot to settings launcher (button → opens SettingsActivity placeholder or shows sign-in status)
- `android/app/src/main/java/com/maru/expenserecorder/ExpenseWidget.kt` -- ADAPT: update pending intent target to `RecordingActivity` (verify class name matches)
- `android/app/src/main/AndroidManifest.xml` -- UPDATE: remove MSAL activity/intent-filter; ensure `RecordingActivity` declared; add Google Sign-In metadata (`com.google.android.gms.version`)

## Tasks & Acceptance

**Execution:**
- [ ] `android/gradle/libs.versions.toml` -- REMOVE versions: `ksp`, `msal`, `okhttp`, `room`; ADD: `play-services-auth = "21.2.0"`, `google-sheets = "v4-rev20240422-2.0.0"`, `google-api-client-android = "2.6.0"`, `google-http-client-jackson2 = "1.44.2"`; ADD library aliases: `play-services-auth`, `google-api-services-sheets`, `google-api-client-android`, `google-http-client-jackson2`
- [ ] `android/app/build.gradle.kts` -- REMOVE: `alias(libs.plugins.ksp)` plugin; REMOVE: room deps, ksp() call, msal dep, okhttp dep, `configurations.all { resolutionStrategy.force(...) }`, MSAL packaging exclusions; ADD: `implementation(libs.play.services.auth)`, `implementation(libs.google.api.services.sheets)`, `implementation(libs.google.api.client.android)`, `implementation(libs.google.http.client.jackson2)`; KEEP: packaging block but with only `META-INF/DEPENDENCIES` exclusion needed for Google API client
- [ ] `android/app/src/main/java/com/maru/expenserecorder/auth/MicrosoftAuthManager.kt` -- DELETE file
- [ ] `android/app/src/main/java/com/maru/expenserecorder/OneDriveSync.kt` -- DELETE file
- [ ] `android/app/src/main/java/com/maru/expenserecorder/MinimalXlsx.kt` -- DELETE file
- [ ] `android/app/src/main/java/com/maru/expenserecorder/database/Expense.kt` -- DELETE file
- [ ] `android/app/src/main/java/com/maru/expenserecorder/database/ExpenseDao.kt` -- DELETE file
- [ ] `android/app/src/main/java/com/maru/expenserecorder/database/ExpenseDatabase.kt` -- DELETE file
- [ ] `android/app/src/main/java/com/maru/expenserecorder/ExpenseAdapter.kt` -- DELETE file
- [ ] `android/app/src/main/java/com/maru/expenserecorder/auth/GoogleAuthManager.kt` -- CREATE: Google Sign-In wrapper; `fun signIn(activity, launcher)`, `suspend fun getAccessToken(activity): String?`, `fun signOut(context)`, `fun isSignedIn(): Boolean`
- [ ] `android/app/src/main/java/com/maru/expenserecorder/data/WriteResult.kt` -- CREATE sealed class
- [ ] `android/app/src/main/java/com/maru/expenserecorder/data/Expense.kt` -- CREATE data class (date: String DD/MM/YYYY, time: String HH:mm, amount: Double, subject: String)
- [ ] `android/app/src/main/java/com/maru/expenserecorder/data/ExpenseRepository.kt` -- CREATE with `suspend fun appendExpense(expense: Expense, accessToken: String, spreadsheetId: String): WriteResult`
- [ ] `android/app/src/main/java/com/maru/expenserecorder/ExpenseApp.kt` -- REFACTOR: swap auth manager
- [ ] `android/app/src/main/java/com/maru/expenserecorder/RecordingActivity.kt` -- REFACTOR: remove DB/OneDrive code; add Sheets write via repository
- [ ] `android/app/src/main/java/com/maru/expenserecorder/MainActivity.kt` -- REFACTOR: remove RecyclerView/Room; show sign-in status + settings navigation
- [ ] `android/app/src/main/java/com/maru/expenserecorder/ExpenseWidget.kt` -- ADAPT: verify intent target class name
- [ ] `android/app/src/main/AndroidManifest.xml` -- UPDATE: remove MSAL entries, add GMS version metadata

**Acceptance Criteria:**
- Given the project is opened in Android Studio, when Gradle sync runs, then it succeeds with no unresolved references to `msal`, `okhttp`, `room`, or `ksp`.
- Given a signed-in user and network connectivity, when a voice expense is recorded and parsed, then a new row appears in the correct monthly tab of the configured Google Sheet within 5 seconds.
- Given the monthly tab does not exist, when an expense is written, then the tab is created with header row `Date | Time | Amount | Subject` and the expense row is appended.
- Given no numeric amount in the transcription, when parsing completes, then the user sees a "Could not parse amount — re-record?" prompt and no row is written.
- Given network unavailable, when a write is attempted, then the user sees an error toast with a retry option.
- Given an expired/revoked Google token, when a write is attempted, then `WriteResult.AuthError` is returned and the user is directed to re-sign-in.

## Design Notes

**`ExpenseRepository.appendExpense` tab-creation logic:**
```
val tabName = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
// Check existing sheets → if tabName not found → batchUpdate addSheet
// Then appendValues to tabName!A:D
```

**`GoogleAuthManager.getAccessToken`:** Use `GoogleSignIn.getLastSignedInAccount(context)` → request server auth code or use `GoogleAccountCredential.usingOAuth2` with `SheetsScopes.SPREADSHEETS` scope for the Sheets API client.

**`RecordingActivity` wiring pattern:**
```kotlin
lifecycleScope.launch {
    val token = (application as ExpenseApp).authManager.getAccessToken(this@RecordingActivity) ?: run {
        showError("Not signed in"); return@launch
    }
    val prefs = PreferenceManager.getDefaultSharedPreferences(this@RecordingActivity)
    val spreadsheetId = prefs.getString(PrefsKeys.PREF_SPREADSHEET_ID, DEFAULT_SPREADSHEET_ID)!!
    when (val result = repository.appendExpense(expense, token, spreadsheetId)) {
        is WriteResult.Success -> showToast("Saved ✓")
        is WriteResult.NetworkError -> showRetryDialog(result.msg)
        is WriteResult.AuthError -> showError("Sign in again in Settings")
        is WriteResult.SheetsError -> showRetryDialog(result.msg)
    }
}
```

## Spec Change Log

## Verification

**Commands:**
- `cd android && ./gradlew assembleDebug` -- expected: BUILD SUCCESSFUL, zero compilation errors, no references to msal/room/okhttp/ksp
