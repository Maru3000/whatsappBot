package com.maru.expenserecorder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.maru.expenserecorder.data.PrefsKeys
import com.maru.expenserecorder.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth get() = (application as ExpenseApp).authManager

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                refreshSignInStatus()
            } catch (e: ApiException) {
                Toast.makeText(this, "Sign-in failed (${e.statusCode})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = PrefsKeys.prefs(this)
        binding.etSpreadsheetId.setText(
            prefs.getString(PrefsKeys.PREF_SPREADSHEET_ID, PrefsKeys.DEFAULT_SPREADSHEET_ID)
        )

        binding.btnSignIn.setOnClickListener {
            if (auth.isSignedIn()) {
                lifecycleScope.launch {
                    auth.signOut()
                    refreshSignInStatus()
                }
            } else {
                signInLauncher.launch(auth.signInClient.signInIntent)
            }
        }

        binding.btnSaveId.setOnClickListener {
            val id = binding.etSpreadsheetId.text.toString().trim()
            if (id.isBlank()) {
                Toast.makeText(this, getString(R.string.spreadsheet_id_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PrefsKeys.prefs(this).edit().putString(PrefsKeys.PREF_SPREADSHEET_ID, id).apply()
            Toast.makeText(this, getString(R.string.saved_settings), Toast.LENGTH_SHORT).show()
        }

        binding.btnViewExpenses.setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
        }

        refreshSignInStatus()
    }

    private fun refreshSignInStatus() {
        val email = auth.getAccountEmail()
        if (email != null) {
            binding.tvAccount.text = email
            binding.btnSignIn.text = getString(R.string.sign_out)
        } else {
            binding.tvAccount.text = getString(R.string.not_signed_in)
            binding.btnSignIn.text = getString(R.string.sign_in_google)
        }
    }
}
