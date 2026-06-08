package com.maru.expenserecorder.data

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.UnknownHostException
import java.util.Locale

class ExpenseRepository {

    suspend fun appendExpense(
        expense: Expense,
        credential: GoogleAccountCredential,
        spreadsheetId: String
    ): WriteResult = withContext(Dispatchers.IO) {
        try {
            val service = Sheets.Builder(
                NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("ExpenseRecorder").build()

            val tabName = buildTabName(expense.date)
            ensureTabExists(service, spreadsheetId, tabName)
            appendRow(service, spreadsheetId, tabName, expense)
            WriteResult.Success
        } catch (e: UnknownHostException) {
            WriteResult.NetworkError("No network connection")
        } catch (e: IOException) {
            val msg = e.message ?: "Network error"
            if (msg.contains("401") || msg.contains("403")) WriteResult.AuthError
            else WriteResult.NetworkError(msg)
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            when (e.statusCode) {
                401, 403 -> WriteResult.AuthError
                else -> WriteResult.SheetsError(e.details?.message ?: e.message ?: "Sheets error")
            }
        } catch (e: Exception) {
            WriteResult.SheetsError(e.message ?: "Unknown error")
        }
    }

    internal fun buildTabName(date: String): String {
        // date is DD/MM/YYYY — parse month + year for tab name "Jun 2026"
        val parts = date.split("/")
        val month = parts[1].toInt()
        val year = parts[2]
        val monthName = java.time.Month.of(month).getDisplayName(
            java.time.format.TextStyle.SHORT, Locale.ENGLISH
        )
        return "$monthName $year"
    }

    private fun ensureTabExists(service: Sheets, spreadsheetId: String, tabName: String) {
        val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
        val exists = spreadsheet.sheets?.any { it.properties?.title == tabName } == true
        if (!exists) {
            try {
                val addSheet = AddSheetRequest().setProperties(SheetProperties().setTitle(tabName))
                service.spreadsheets().batchUpdate(
                    spreadsheetId,
                    BatchUpdateSpreadsheetRequest().setRequests(listOf(Request().setAddSheet(addSheet)))
                ).execute()
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                // 400 "already exists" from a concurrent write — treat as success and continue
                if (e.statusCode != 400) throw e
            }
            // Write header row (idempotent: if row 1 is already written, this overwrites with the same values)
            val header = ValueRange().setValues(
                listOf(listOf("Date", "Time", "Amount", "Subject"))
            )
            service.spreadsheets().values()
                .update(spreadsheetId, "$tabName!A1", header)
                .setValueInputOption("RAW")
                .execute()
        }
    }

    private fun appendRow(service: Sheets, spreadsheetId: String, tabName: String, expense: Expense) {
        val row = ValueRange().setValues(
            listOf(listOf(expense.date, expense.time, expense.amount, expense.subject))
        )
        service.spreadsheets().values()
            .append(spreadsheetId, "$tabName!A:D", row)
            .setValueInputOption("RAW")
            .setInsertDataOption("INSERT_ROWS")
            .execute()
    }
}
