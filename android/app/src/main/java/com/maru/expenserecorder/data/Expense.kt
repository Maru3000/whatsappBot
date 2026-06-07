package com.maru.expenserecorder.data

data class Expense(
    val date: String,    // DD/MM/YYYY
    val time: String,    // HH:mm
    val amount: Double,
    val subject: String
)
