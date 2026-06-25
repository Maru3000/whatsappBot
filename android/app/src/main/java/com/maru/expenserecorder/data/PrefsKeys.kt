package com.maru.expenserecorder.data

import android.content.Context
import android.content.SharedPreferences

object PrefsKeys {
    const val PREF_SPREADSHEET_ID = "pref_spreadsheet_id"
    const val DEFAULT_SPREADSHEET_ID = "1RErU26Ln2-uW4FMxezn_OZ5WWkvXVvYU"

    const val PREF_SPEECH_LANGUAGES = "pref_speech_languages"
    const val DEFAULT_SPEECH_LANGUAGES = "he-IL,en-US"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("expense_recorder_prefs", Context.MODE_PRIVATE)
}
