package com.maru.expenserecorder

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.maru.expenserecorder.data.Expense
import com.maru.expenserecorder.data.ExpenseRepository
import com.maru.expenserecorder.data.PrefsKeys
import com.maru.expenserecorder.databinding.ActivityExpensesBinding
import com.maru.expenserecorder.databinding.DialogAddExpenseBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
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

        binding.rvIncome.layoutManager = LinearLayoutManager(this)
        binding.rvExpenses.layoutManager = LinearLayoutManager(this)
        binding.rvIncome.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.rvExpenses.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        binding.btnRefresh.setOnClickListener { loadExpenses() }
        binding.fabAdd.setOnClickListener { showAddDialog() }
        loadExpenses()
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddExpenseBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle("Add Entry")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val amount = dialogBinding.etAmount.text.toString().trim().toDoubleOrNull()
                val description = dialogBinding.etDescription.text.toString().trim()
                if (amount == null || description.isBlank()) {
                    Toast.makeText(this, "Enter a valid amount and description", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val type = if (dialogBinding.rbIncome.isChecked) "income" else "expense"
                val now = LocalDateTime.now()
                val expense = Expense(
                    date = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    time = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                    amount = amount,
                    subject = description,
                    type = type
                )
                saveEntry(expense)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveEntry(expense: Expense) {
        val credential = auth.buildCredential() ?: return
        val spreadsheetId = PrefsKeys.prefs(this)
            .getString(PrefsKeys.PREF_SPREADSHEET_ID, PrefsKeys.DEFAULT_SPREADSHEET_ID)!!
        lifecycleScope.launch {
            repository.appendExpense(expense, credential, spreadsheetId)
            loadExpenses()
        }
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
        binding.sectionIncome.visibility = View.GONE
        binding.sectionExpenses.visibility = View.GONE
        binding.sectionDivider.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val all = repository.getExpenses(credential, spreadsheetId, tabName)
            binding.progressBar.visibility = View.GONE

            val incomeItems  = all.filter { it.type == "income" }
            val expenseItems = all.filter { it.type != "income" }

            if (incomeItems.isEmpty() && expenseItems.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
                return@launch
            }

            if (incomeItems.isNotEmpty()) {
                binding.rvIncome.adapter = ExpenseListAdapter(incomeItems)
                val total = incomeItems.sumOf { it.amount }
                binding.tvIncomeTotal.text = "+₪${formatAmount(total)}"
                binding.sectionIncome.visibility = View.VISIBLE
            }

            if (expenseItems.isNotEmpty()) {
                binding.rvExpenses.adapter = ExpenseListAdapter(expenseItems)
                val total = expenseItems.sumOf { it.amount }
                binding.tvExpensesTotal.text = "-₪${formatAmount(total)}"
                binding.sectionExpenses.visibility = View.VISIBLE
            }

            if (incomeItems.isNotEmpty() && expenseItems.isNotEmpty()) {
                binding.sectionDivider.visibility = View.VISIBLE
            }
        }
    }

    private fun formatAmount(v: Double) =
        if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(v)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
