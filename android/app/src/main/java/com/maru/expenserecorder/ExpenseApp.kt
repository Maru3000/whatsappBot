package com.maru.expenserecorder

import android.app.Application
import com.maru.expenserecorder.auth.GoogleAuthManager

class ExpenseApp : Application() {
    val authManager by lazy { GoogleAuthManager(this) }
}
