package com.maru.expenserecorder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.maru.expenserecorder.data.Expense
import com.maru.expenserecorder.data.ExpenseRepository
import com.maru.expenserecorder.data.PrefsKeys
import com.maru.expenserecorder.data.WriteResult
import com.maru.expenserecorder.databinding.ActivityRecordingBinding
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private val repository = ExpenseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setOnClickListener { finish() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    binding.tvStatus.text = getString(R.string.listening)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {
                    val s = (1f + rms / 10f).coerceIn(0.8f, 1.4f)
                    binding.ivMicAnim.scaleX = s
                    binding.ivMicAnim.scaleY = s
                }
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { binding.tvStatus.text = "Processing…" }
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        else -> "Recognition error ($error)"
                    }
                    Toast.makeText(this@RecordingActivity, msg, Toast.LENGTH_SHORT).show()
                    finish()
                }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                    binding.tvRecognized.text = text
                    processExpense(text)
                }
                override fun onPartialResults(partial: Bundle?) {
                    binding.tvRecognized.text = partial
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: ""
                }
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "he-IL,en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun processExpense(text: String) {
        val parsed = ExpenseParser.parse(text)
        if (parsed == null) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.could_not_parse_title))
                .setMessage(getString(R.string.could_not_parse_msg))
                .setPositiveButton(getString(R.string.re_record)) { _, _ -> startListening() }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
                .show()
            return
        }

        val now = LocalDateTime.now()
        val expense = Expense(
            date = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            time = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            amount = parsed.amount,
            subject = parsed.description
        )
        writeExpense(expense)
    }

    private fun writeExpense(expense: Expense) {
        val credential = (application as ExpenseApp).authManager.buildCredential()
        if (credential == null) {
            Toast.makeText(this, getString(R.string.not_signed_in_record), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val spreadsheetId = PrefsKeys.prefs(this)
            .getString(PrefsKeys.PREF_SPREADSHEET_ID, PrefsKeys.DEFAULT_SPREADSHEET_ID)!!

        binding.tvStatus.text = "Saving…"

        lifecycleScope.launch {
            when (val result = repository.appendExpense(expense, credential, spreadsheetId)) {
                is WriteResult.Success -> {
                    Toast.makeText(
                        this@RecordingActivity,
                        getString(R.string.saved, expense.amount, expense.subject),
                        Toast.LENGTH_LONG
                    ).show()
                    Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1200)
                }
                is WriteResult.NetworkError -> showRetryDialog(expense, result.msg)
                is WriteResult.AuthError -> {
                    Toast.makeText(this@RecordingActivity, getString(R.string.auth_error_record), Toast.LENGTH_LONG).show()
                    finish()
                }
                is WriteResult.SheetsError -> showRetryDialog(expense, result.msg)
            }
        }
    }

    private fun showRetryDialog(expense: Expense, errorMsg: String) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.save_failed))
            .setMessage(errorMsg)
            .setPositiveButton(getString(R.string.retry)) { _, _ -> writeExpense(expense) }
            .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val REQ_MIC = 101
    }
}
