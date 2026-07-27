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

        // Spreadsheet ID
        binding.etSpreadsheetId.setText(
            prefs.getString(PrefsKeys.PREF_SPREADSHEET_ID, PrefsKeys.DEFAULT_SPREADSHEET_ID)
        )
        binding.btnSaveId.setOnClickListener {
            val id = binding.etSpreadsheetId.text.toString().trim()
            if (id.isBlank()) {
                Toast.makeText(this, getString(R.string.spreadsheet_id_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString(PrefsKeys.PREF_SPREADSHEET_ID, id).apply()
            Toast.makeText(this, getString(R.string.saved_settings), Toast.LENGTH_SHORT).show()
        }

        // Sign in
        binding.btnSignIn.setOnClickListener {
            if (auth.isSignedIn()) {
                lifecycleScope.launch { auth.signOut(); refreshSignInStatus() }
            } else {
                signInLauncher.launch(auth.signInClient.signInIntent)
            }
        }

        // Deepgram API key
        val savedKey = prefs.getString(PrefsKeys.PREF_DEEPGRAM_KEY, "")!!
        if (savedKey.isNotBlank()) binding.etDeepgramKey.setText(savedKey)
        binding.btnSaveDeepgramKey.setOnClickListener {
            val key = binding.etDeepgramKey.text.toString().trim()
            if (key.isBlank()) {
                Toast.makeText(this, "Key cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString(PrefsKeys.PREF_DEEPGRAM_KEY, key).apply()
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
        }

        // Language radio — restore saved value
        val savedLang = prefs.getString(PrefsKeys.PREF_SPEECH_LANGUAGES, PrefsKeys.DEFAULT_SPEECH_LANGUAGES)!!
        when (savedLang) {
            "he-IL"       -> binding.rbLangHebrew.isChecked = true
            "en-US"       -> binding.rbLangEnglish.isChecked = true
            else          -> binding.rbLangBoth.isChecked = true
        }
        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.rb_lang_hebrew  -> "he-IL"
                R.id.rb_lang_english -> "en-US"
                else                 -> "he-IL,en-US"
            }
            prefs.edit().putString(PrefsKeys.PREF_SPEECH_LANGUAGES, value).apply()
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
