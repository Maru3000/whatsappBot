package com.maru.expenserecorder

import android.app.Application
import com.maru.expenserecorder.auth.MicrosoftAuthManager

class ExpenseApp : Application() {
    val authManager by lazy { MicrosoftAuthManager(this) }
}
