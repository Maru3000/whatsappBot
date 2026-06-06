package com.maru.expenserecorder

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.maru.expenserecorder.database.ExpenseDatabase
import com.maru.expenserecorder.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = ExpenseAdapter()
    private val db by lazy { ExpenseDatabase.get(this) }
    private val auth get() = (application as ExpenseApp).authManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerExpenses.layoutManager = LinearLayoutManager(this)
        binding.recyclerExpenses.adapter = adapter

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all expenses?")
                .setMessage("This only clears the local log. The OneDrive file is not deleted.")
                .setPositiveButton("Clear") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) { db.expenseDao().deleteAll() }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnOnedrive.setOnClickListener { onOneDriveButtonClick() }

        // Observe expense list
        lifecycleScope.launch {
            db.expenseDao().getAllFlow().collectLatest { expenses ->
                adapter.submitList(expenses)
                val total = expenses.sumOf { it.amount }
                binding.tvTotal.text = getString(R.string.total_label, total)
                binding.recyclerExpenses.visibility = if (expenses.isEmpty()) View.GONE else View.VISIBLE
                binding.tvEmpty.visibility = if (expenses.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // Initialize MSAL and refresh sign-in status
        lifecycleScope.launch {
            auth.initialize()
            refreshOneDriveStatus()
        }
    }

    private suspend fun refreshOneDriveStatus() {
        val name = auth.getCurrentUserName()
        if (name != null) {
            binding.btnOnedrive.text = "OneDrive: $name"
            syncUnsyncedExpenses()
        } else {
            binding.btnOnedrive.text = getString(R.string.connect_onedrive)
        }
    }

    private fun onOneDriveButtonClick() {
        lifecycleScope.launch {
            val name = auth.getCurrentUserName()
            if (name != null) {
                // Already signed in — offer sign-out
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("OneDrive")
                    .setMessage("Signed in as $name\n\nSign out?")
                    .setPositiveButton("Sign out") { _, _ ->
                        lifecycleScope.launch {
                            auth.signOut()
                            binding.btnOnedrive.text = getString(R.string.connect_onedrive)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                binding.btnOnedrive.text = "Connecting…"
                val token = auth.signIn(this@MainActivity)
                if (token != null) {
                    refreshOneDriveStatus()
                } else {
                    binding.btnOnedrive.text = getString(R.string.connect_onedrive)
                }
            }
        }
    }

    private fun syncUnsyncedExpenses() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val token = auth.getTokenSilently() ?: return@launch
                OneDriveSync(applicationContext).syncUnsynced(token)
            }
        }
    }
}
