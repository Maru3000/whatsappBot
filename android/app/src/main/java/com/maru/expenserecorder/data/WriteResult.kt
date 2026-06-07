package com.maru.expenserecorder.data

sealed class WriteResult {
    object Success : WriteResult()
    data class NetworkError(val msg: String) : WriteResult()
    object AuthError : WriteResult()
    data class SheetsError(val msg: String) : WriteResult()
}
