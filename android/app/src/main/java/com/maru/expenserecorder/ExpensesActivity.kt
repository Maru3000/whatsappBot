package com.maru.expenserecorder

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.maru.expenserecorder.data.ExpenseRepository
import com.maru.expenserecorder.data.PrefsKeys
import com.maru.expenserecorder.databinding.ActivityExpensesBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExpensesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExpensesBinding
    private val repository = ExpenseRepository()
    private val auth get() = (application as ExpenseApp).authManager
    private lateinit var tabName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tabName = repository.buildTabName(
            LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        )
        supportActionBar?.title = tabName

        binding.rvExpenses.layoutManager = LinearLayoutManager(this)
        binding.rvExpenses.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        binding.btnRefresh.setOnClickListener { loadExpenses() }
        loadExpenses()
    }

    private fun loadExpenses() {
        val credential = auth.buildCredential()
        if (credential == null) {
            Toast.makeText(this, getString(R.string.not_signed_in_record), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val spreadsheetId = PrefsKeys.prefs(this)
            .getString(PrefsKeys.PREF_SPREADSHEET_ID, PrefsKeys.DEFAULT_SPREADSHEET_ID)!!

        binding.progressBar.visibility = View.VISIBLE
        binding.rvExpenses.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val expenses = repository.getExpenses(credential, spreadsheetId, tabName)
            binding.progressBar.visibility = View.GONE
            if (expenses.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
            } else {
                binding.rvExpenses.visibility = View.VISIBLE
                binding.rvExpenses.adapter = ExpenseListAdapter(expenses)
            }
            val income = expenses.filter { it.type == "income" }.sumOf { it.amount }
            val spent  = expenses.filter { it.type != "income" }.sumOf { it.amount }
            val net = income - spent
            val netStr = if (net % 1.0 == 0.0) net.toLong().toString() else "%.2f".format(net)
            val prefix = if (net >= 0) "+" else ""
            binding.tvTotal.text = "$prefix₪$netStr"
            binding.tvTotal.setTextColor(
                if (net >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
