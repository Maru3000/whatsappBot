package com.maru.expenserecorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseParserTest {

    // ── happy paths ────────────────────────────────────────────────────────

    @Test fun `integer amount with description`() {
        val r = ExpenseParser.parse("50 taxi")!!
        assertEquals(50.0, r.amount, 0.0)
        assertEquals("Taxi", r.description)
    }

    @Test fun `decimal amount`() {
        val r = ExpenseParser.parse("12.5 water")!!
        assertEquals(12.5, r.amount, 0.001)
        assertEquals("Water", r.description)
    }

    @Test fun `amount only — default description`() {
        val r = ExpenseParser.parse("50")!!
        assertEquals(50.0, r.amount, 0.0)
        assertEquals("Cash expense", r.description)
    }

    @Test fun `amount at end — description before amount`() {
        val r = ExpenseParser.parse("taxi 50")!!
        assertEquals(50.0, r.amount, 0.0)
        assertEquals("Taxi", r.description)
    }

    @Test fun `multi-word description`() {
        val r = ExpenseParser.parse("125 for lunch")!!
        assertEquals(125.0, r.amount, 0.0)
        assertEquals("For lunch", r.description)
    }

    @Test fun `description is first-char capitalised only`() {
        val r = ExpenseParser.parse("50 my morning coffee")!!
        assertEquals("My morning coffee", r.description)
    }

    // ── first-number rule ─────────────────────────────────────────────────

    @Test fun `first-number rule — multiple numbers in utterance`() {
        val r = ExpenseParser.parse("50 for 3 coffees")!!
        assertEquals(50.0, r.amount, 0.0)
        assertEquals("For 3 coffees", r.description)
    }

    @Test fun `first-number rule — leading number wins over trailing`() {
        val r = ExpenseParser.parse("100 bus 2 tickets")!!
        assertEquals(100.0, r.amount, 0.0)
        assertEquals("Bus 2 tickets", r.description)
    }

    // ── English number words ──────────────────────────────────────────────

    @Test fun `english number word — fifty`() {
        val r = ExpenseParser.parse("fifty taxi")!!
        assertEquals(50.0, r.amount, 0.0)
        assertEquals("Taxi", r.description)
    }

    @Test fun `english compound — twenty five`() {
        val r = ExpenseParser.parse("twenty five taxi")!!
        assertEquals(25.0, r.amount, 0.0)
        assertEquals("Taxi", r.description)
    }

    @Test fun `english compound — hundred fifty`() {
        val r = ExpenseParser.parse("hundred fifty lunch")!!
        assertEquals(150.0, r.amount, 0.0)
        assertEquals("Lunch", r.description)
    }

    @Test fun `english thousand`() {
        val r = ExpenseParser.parse("one thousand car repair")!!
        assertEquals(1000.0, r.amount, 0.0)
        assertEquals("Car repair", r.description)
    }

    @Test fun `comma decimal — european format`() {
        val r = ExpenseParser.parse("12,5 water")!!
        assertEquals(12.5, r.amount, 0.001)
        assertEquals("Water", r.description)
    }

    @Test fun `zero amount — efes`() {
        val r = ExpenseParser.parse("efes coffee")!!
        assertEquals(0.0, r.amount, 0.0)
        assertEquals("Coffee", r.description)
    }

    @Test fun `zero amount — digit zero`() {
        val r = ExpenseParser.parse("0 test")!!
        assertEquals(0.0, r.amount, 0.0)
    }

    @Test fun `shekel word plus description but no amount — returns null`() {
        assertNull(ExpenseParser.parse("shekel groceries"))
    }

    // ── Hebrew transliterations ───────────────────────────────────────────

    @Test fun `hebrew echad`() {
        val r = ExpenseParser.parse("echad coffee")!!
        assertEquals(1.0, r.amount, 0.0)
        assertEquals("Coffee", r.description)
    }

    @Test fun `hebrew ahat`() {
        val r = ExpenseParser.parse("ahat coffee")!!
        assertEquals(1.0, r.amount, 0.0)
    }

    @Test fun `hebrew shtaim`() {
        val r = ExpenseParser.parse("shtaim coffees")!!
        assertEquals(2.0, r.amount, 0.0)
        assertEquals("Coffees", r.description)
    }

    @Test fun `hebrew esrim`() {
        val r = ExpenseParser.parse("esrim taxi")!!
        assertEquals(20.0, r.amount, 0.0)
        assertEquals("Taxi", r.description)
    }

    @Test fun `hebrew meah`() {
        val r = ExpenseParser.parse("meah lunch")!!
        assertEquals(100.0, r.amount, 0.0)
        assertEquals("Lunch", r.description)
    }

    @Test fun `hebrew alef`() {
        val r = ExpenseParser.parse("alef groceries")!!
        assertEquals(1000.0, r.amount, 0.0)
        assertEquals("Groceries", r.description)
    }

    @Test fun `hebrew hamishim`() {
        val r = ExpenseParser.parse("hamishim shekels supermarket")!!
        assertEquals(50.0, r.amount, 0.0)
        assertEquals("Supermarket", r.description)
    }

    // ── shekel keywords stripped ──────────────────────────────────────────

    @Test fun `shekel word stripped — digit amount`() {
        val r = ExpenseParser.parse("50 shekel taxi")!!
        assertEquals(50.0, r.amount, 0.0)
        assertEquals("Taxi", r.description)
    }

    @Test fun `shekels word stripped`() {
        val r = ExpenseParser.parse("meah shekels coffee")!!
        assertEquals(100.0, r.amount, 0.0)
        assertEquals("Coffee", r.description)
    }

    @Test fun `nis word stripped`() {
        val r = ExpenseParser.parse("30 nis bus")!!
        assertEquals(30.0, r.amount, 0.0)
        assertEquals("Bus", r.description)
    }

    @Test fun `ils word stripped`() {
        val r = ExpenseParser.parse("45 ils groceries")!!
        assertEquals(45.0, r.amount, 0.0)
        assertEquals("Groceries", r.description)
    }

    // ── no-amount rejection ───────────────────────────────────────────────

    @Test fun `no amount — returns null`() {
        assertNull(ExpenseParser.parse("taxi"))
    }

    @Test fun `empty string — returns null`() {
        assertNull(ExpenseParser.parse(""))
    }

    @Test fun `blank string — returns null`() {
        assertNull(ExpenseParser.parse("   "))
    }

    @Test fun `only shekel keyword — returns null`() {
        assertNull(ExpenseParser.parse("shekel"))
    }
}
