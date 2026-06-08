package com.maru.expenserecorder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseRepositoryTest {

    private val repo = ExpenseRepository()

    // ── buildTabName — tab-name derivation ────────────────────────────────

    @Test fun `june 2026 tab name`() {
        assertEquals("Jun 2026", repo.buildTabName("07/06/2026"))
    }

    @Test fun `january — always English, not locale-dependent`() {
        assertEquals("Jan 2026", repo.buildTabName("01/01/2026"))
    }

    @Test fun `december year boundary`() {
        assertEquals("Dec 2025", repo.buildTabName("31/12/2025"))
    }

    @Test fun `march`() {
        assertEquals("Mar 2024", repo.buildTabName("15/03/2024"))
    }

    @Test fun `all twelve months produce correct short names`() {
        val expected = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        expected.forEachIndexed { idx, monthName ->
            val mm = (idx + 1).toString().padStart(2, '0')
            assertEquals("$monthName 2026", repo.buildTabName("01/$mm/2026"))
        }
    }
}
