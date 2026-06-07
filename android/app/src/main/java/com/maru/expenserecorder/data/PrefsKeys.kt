package com.maru.expenserecorder.data

import android.content.Context
import android.content.SharedPreferences

object PrefsKeys {
    const val PREF_SPREADSHEET_ID = "pref_spreadsheet_id"
    const val DEFAULT_SPREADSHEET_ID = "1RErU26Ln2-uW4FMxezn_OZ5WWkvXVvYU"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("expense_recorder_prefs", Context.MODE_PRIVATE)
}
