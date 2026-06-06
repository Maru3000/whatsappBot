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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.maru.expenserecorder.database.Expense
import com.maru.expenserecorder.database.ExpenseDatabase
import com.maru.expenserecorder.databinding.ActivityRecordingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private val db by lazy { ExpenseDatabase.get(applicationContext) }

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
            Toast.makeText(this, getString(R.string.could_not_parse), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        scope.launch {
            val expense = Expense(amount = parsed.amount, description = parsed.description)
            val savedId = db.expenseDao().insert(expense)
            val savedExpense = expense.copy(id = savedId)

            Toast.makeText(
                this@RecordingActivity,
                getString(R.string.saved, parsed.amount, parsed.description),
                Toast.LENGTH_LONG
            ).show()

            // Fire-and-forget OneDrive sync — won't block or crash if offline/not signed in
            syncToOneDrive(savedExpense)

            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1200)
        }
    }

    private fun syncToOneDrive(expense: Expense) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                val token = (application as ExpenseApp).authManager.getTokenSilently()
                    ?: return@launch
                val success = OneDriveSync(applicationContext).syncExpense(token, expense)
                if (success) db.expenseDao().markSynced(expense.id)
            }
        }
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
