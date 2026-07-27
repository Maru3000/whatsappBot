---
title: 'Story 2 — Unit tests for ExpenseParser and ExpenseRepository'
type: 'feature'
created: '2026-06-08'
status: 'done'
route: 'one-shot'
---

# Story 2 — Unit tests for ExpenseParser and ExpenseRepository

## Intent

**Problem:** `ExpenseParser` and `ExpenseRepository.buildTabName` had no test coverage; a first-number-rule bug in the parser was undetected.

**Approach:** Add 37 JUnit 4 unit tests for `ExpenseParser` (all parsing paths, Hebrew transliterations, shekel stripping, null-return cases) and `ExpenseRepository.buildTabName` (all 12 months). Fix the first-number-rule bug in `ExpenseParser` exposed during test authoring. Make `buildTabName` `internal` for direct test access.

## Suggested Review Order

**Production bug fix**

- First-number rule: set amount only on first numeric/word-number token; rest → description
  [`ExpenseParser.kt:44`](../../android/app/src/main/java/com/maru/expenserecorder/ExpenseParser.kt#L44)

**Parser tests**

- First-number rule tests verify "50 for 3 coffees" → ₪50 / "For 3 coffees"
  [`ExpenseParserTest.kt:50`](../../android/app/src/test/java/com/maru/expenserecorder/ExpenseParserTest.kt#L50)

- Hebrew transliteration coverage: echad, ahat, shtaim, esrim, meah, alef, hamishim
  [`ExpenseParserTest.kt:83`](../../android/app/src/test/java/com/maru/expenserecorder/ExpenseParserTest.kt#L83)

- Null-return cases: no amount, empty, blank, shekel-only, shekel+description no amount
  [`ExpenseParserTest.kt:151`](../../android/app/src/test/java/com/maru/expenserecorder/ExpenseParserTest.kt#L151)

- Edge cases: zero amount, comma decimal, compound English numbers
  [`ExpenseParserTest.kt:171`](../../android/app/src/test/java/com/maru/expenserecorder/ExpenseParserTest.kt#L171)

**Repository tab-name tests**

- All 12 months verified as English short names, locale-independent
  [`ExpenseRepositoryTest.kt:17`](../../android/app/src/test/java/com/maru/expenserecorder/data/ExpenseRepositoryTest.kt#L17)

**Test infrastructure**

- JUnit 4 + kotlin-test-junit added as testImplementation
  [`build.gradle.kts:61`](../../android/app/build.gradle.kts#L61)
