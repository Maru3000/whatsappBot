package com.maru.expenserecorder

import android.content.Context
import com.maru.expenserecorder.database.Expense
import com.maru.expenserecorder.database.ExpenseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class OneDriveSync(private val context: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val graphBase = "https://graph.microsoft.com/v1.0"
    private val xlsxFile = "ExpenseLog.xlsx"
    private val tableName = "ExpenseTable"
    private val sheetName = "Expenses"
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val jsonMime = "application/json; charset=utf-8".toMediaType()
    private val xlsxMime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".toMediaType()

    /** Sync a single expense. Returns true on success. */
    suspend fun syncExpense(token: String, expense: Expense): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val fileId = ensureFile(token)
            appendRow(token, fileId, expense)
            true
        }.getOrDefault(false)
    }

    /** Sync every expense not yet pushed to OneDrive. */
    suspend fun syncUnsynced(token: String) = withContext(Dispatchers.IO) {
        val dao = ExpenseDatabase.get(context).expenseDao()
        val pending = dao.getUnsynced()
        if (pending.isEmpty()) return@withContext
        runCatching {
            val fileId = ensureFile(token)
            for (expense in pending) {
                appendRow(token, fileId, expense)
                dao.markSynced(expense.id)
            }
        }
    }

    // ── file management ─────────────────────────────────────────────────────

    private fun ensureFile(token: String): String {
        val existing = getFileId(token)
        if (existing != null) return existing
        return createFile(token)
    }

    private fun getFileId(token: String): String? {
        val req = Request.Builder()
            .url("$graphBase/me/drive/root:/$xlsxFile")
            .auth(token).get().build()
        return http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) JSONObject(resp.body!!.string()).getString("id") else null
        }
    }

    private fun createFile(token: String): String {
        val bytes = MinimalXlsx.createWithHeaders(listOf("Date", "Time", "Description", "Amount (₪)"))
        val uploadReq = Request.Builder()
            .url("$graphBase/me/drive/root:/$xlsxFile:/content")
            .auth(token)
            .put(bytes.toRequestBody(xlsxMime))
            .build()
        val fileId = http.newCall(uploadReq).execute().use { resp ->
            check(resp.isSuccessful) { "Upload failed: ${resp.code}" }
            JSONObject(resp.body!!.string()).getString("id")
        }
        createTable(token, fileId)
        return fileId
    }

    private fun createTable(token: String, fileId: String) {
        val body = JSONObject().apply {
            put("address", "$sheetName!A1:D1")
            put("hasHeaders", true)
        }
        val req = Request.Builder()
            .url("$graphBase/me/drive/items/$fileId/workbook/worksheets('$sheetName')/tables/add")
            .auth(token).json(body).post(body.toString().toRequestBody(jsonMime)).build()
        http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val tableId = JSONObject(resp.body!!.string()).getString("id")
                renameTable(token, fileId, tableId)
            }
        }
    }

    private fun renameTable(token: String, fileId: String, tableId: String) {
        val body = JSONObject().put("name", tableName).toString()
        val req = Request.Builder()
            .url("$graphBase/me/drive/items/$fileId/workbook/tables('$tableId')")
            .auth(token).json(null)
            .patch(body.toRequestBody(jsonMime)).build()
        http.newCall(req).execute().close()
    }

    // ── row append ───────────────────────────────────────────────────────────

    private fun appendRow(token: String, fileId: String, expense: Expense) {
        val date = dateFmt.format(Date(expense.timestampMs))
        val time = timeFmt.format(Date(expense.timestampMs))
        val rowValues = JSONArray().put(
            JSONArray().apply {
                put(date); put(time); put(expense.description); put(expense.amount)
            }
        )
        val body = JSONObject().put("values", rowValues).toString()
        val req = Request.Builder()
            .url("$graphBase/me/drive/items/$fileId/workbook/tables('$tableName')/rows/add")
            .auth(token).json(null)
            .post(body.toRequestBody(jsonMime)).build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                // Table not found (e.g. user opened an existing file) – fall back to sheet range
                appendRowFallback(token, fileId, date, time, expense)
            }
        }
    }

    private fun appendRowFallback(
        token: String, fileId: String,
        date: String, time: String, expense: Expense
    ) {
        val rangeReq = Request.Builder()
            .url("$graphBase/me/drive/items/$fileId/workbook/worksheets('$sheetName')/usedRange?\$select=address")
            .auth(token).get().build()

        val nextRow = http.newCall(rangeReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val address = JSONObject(resp.body!!.string()).getString("address")
                (address.substringAfterLast(":").filter { it.isDigit() }.toIntOrNull() ?: 1) + 1
            } else 2
        }

        val values = JSONObject().put("values", JSONArray().put(
            JSONArray().apply { put(date); put(time); put(expense.description); put(expense.amount) }
        )).toString()

        val patchReq = Request.Builder()
            .url("$graphBase/me/drive/items/$fileId/workbook/worksheets('$sheetName')/range(address='A$nextRow:D$nextRow')")
            .auth(token).json(null)
            .patch(values.toRequestBody(jsonMime)).build()
        http.newCall(patchReq).execute().close()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun Request.Builder.auth(token: String) = header("Authorization", "Bearer $token")
    private fun Request.Builder.json(@Suppress("UNUSED_PARAMETER") ignored: Any?) =
        header("Content-Type", "application/json")
}
