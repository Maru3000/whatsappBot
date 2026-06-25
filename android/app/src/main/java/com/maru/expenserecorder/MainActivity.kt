package com.maru.expenserecorder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

    private val availableLanguages = listOf(
        "English" to "en-US",
        "Hebrew / עברית" to "he-IL",
        "Russian / Русский" to "ru-RU",
        "Arabic / عربي" to "ar-SA",
        "Spanish / Español" to "es-ES",
        "French / Français" to "fr-FR"
    )

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

        binding.btnLanguagePicker.setOnClickListener { showLanguageDialog() }

        binding.btnViewExpenses.setOnClickListener {
            startActivity(Intent(this, ExpensesActivity::class.java))
        }

        refreshSignInStatus()
        refreshLanguageButton()
    }

    private fun showLanguageDialog() {
        val prefs = PrefsKeys.prefs(this)
        val saved = prefs.getString(PrefsKeys.PREF_SPEECH_LANGUAGES, PrefsKeys.DEFAULT_SPEECH_LANGUAGES)!!
        val savedCodes = saved.split(",").map { it.trim() }
        val selected = savedCodes.toMutableList()

        val names = availableLanguages.map { it.first }.toTypedArray()
        val checked = BooleanArray(availableLanguages.size) { availableLanguages[it].second in savedCodes }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Speech Language (max 2)")
            .setMultiChoiceItems(names, checked) { dlg, which, isChecked ->
                val code = availableLanguages[which].second
                if (isChecked) {
                    if (selected.size >= 2) {
                        (dlg as AlertDialog).listView.setItemChecked(which, false)
                        Toast.makeText(this, "Max 2 languages — uncheck one first", Toast.LENGTH_SHORT).show()
                    } else {
                        selected.add(code)
                    }
                } else {
                    selected.remove(code)
                }
            }
            .setPositiveButton("Save") { _, _ ->
                if (selected.isEmpty()) {
                    Toast.makeText(this, "Select at least 1 language", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val value = selected.joinToString(",")
                prefs.edit().putString(PrefsKeys.PREF_SPEECH_LANGUAGES, value).apply()
                refreshLanguageButton()
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
    }

    private fun refreshLanguageButton() {
        val saved = PrefsKeys.prefs(this)
            .getString(PrefsKeys.PREF_SPEECH_LANGUAGES, PrefsKeys.DEFAULT_SPEECH_LANGUAGES)!!
        val codes = saved.split(",").map { it.trim() }
        val names = codes.mapNotNull { code ->
            availableLanguages.find { it.second == code }?.first
        }
        binding.btnLanguagePicker.text = if (names.isNotEmpty()) names.joinToString(", ") else "Tap to select"
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
