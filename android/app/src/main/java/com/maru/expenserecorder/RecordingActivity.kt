package com.maru.expenserecorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

class RecordingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingBinding
    private val repository = ExpenseRepository()
    private var recordingJob: Job? = null

    @Volatile private var audioRecord: AudioRecord? = null

    companion object {
        private const val REQ_MIC = 101
        private const val SAMPLE_RATE = 16000
        private const val SILENCE_THRESHOLD_RMS = 600.0
        private const val SILENCE_DURATION_MS = 3000L
        private const val MIN_RECORD_MS = 800L
        private const val MAX_RECORD_MS = 30_000L
    }

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
            startRecording()
        }
    }

    private fun startRecording() {
        val apiKey = PrefsKeys.prefs(this).getString(PrefsKeys.PREF_DEEPGRAM_KEY, "")!!
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Add a Deepgram API key in Settings first", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val savedLangs = PrefsKeys.prefs(this)
            .getString(PrefsKeys.PREF_SPEECH_LANGUAGES, PrefsKeys.DEFAULT_SPEECH_LANGUAGES)!!

        binding.tvStatus.text = getString(R.string.listening)
        binding.tvRecognized.text = ""

        recordingJob?.cancel()
        recordingJob = lifecycleScope.launch(Dispatchers.IO) {
            val bufSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize * 4
            )
            audioRecord = recorder

            val pcm = ByteArrayOutputStream()
            val buf = ShortArray(bufSize / 2)
            var silenceMs = 0L
            var recordedMs = 0L
            var transcript = ""

            try {
                recorder.startRecording()

                while (isActive) {
                    val read = recorder.read(buf, 0, buf.size)
                    if (read <= 0) break

                    val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                    bytes.asShortBuffer().put(buf, 0, read)
                    pcm.write(bytes.array())

                    val chunkMs = read * 1000L / SAMPLE_RATE

                    var sum = 0.0
                    for (i in 0 until read) { val v = buf[i].toDouble(); sum += v * v }
                    val rms = sqrt(sum / read)

                    val scale = (1f + (rms / 8000.0).toFloat()).coerceIn(0.8f, 1.5f)
                    withContext(Dispatchers.Main) {
                        binding.ivMicAnim.scaleX = scale
                        binding.ivMicAnim.scaleY = scale
                    }

                    recordedMs += chunkMs
                    if (recordedMs >= MIN_RECORD_MS) {
                        if (rms < SILENCE_THRESHOLD_RMS) {
                            silenceMs += chunkMs
                            if (silenceMs >= SILENCE_DURATION_MS) break
                        } else {
                            silenceMs = 0
                        }
                    }
                    if (recordedMs >= MAX_RECORD_MS) break
                }

                if (!isActive) return@launch

                withContext(Dispatchers.Main) { binding.tvStatus.text = "Transcribing…" }

                val wavBytes = buildWav(pcm.toByteArray())
                transcript = DeepgramClient.transcribe(apiKey, savedLangs, wavBytes)

            } catch (e: Exception) {
                if (!isActive) return@launch
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@RecordingActivity,
                        "Transcription failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                return@launch
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                try { recorder.release() } catch (_: Exception) {}
                audioRecord = null
            }

            withContext(Dispatchers.Main) {
                binding.tvRecognized.text = transcript
                processExpense(transcript)
            }
        }
    }

    private fun buildWav(pcm: ByteArray): ByteArray {
        val wav = ByteArray(44 + pcm.size)
        val bb = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + pcm.size)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1)                   // PCM
        bb.putShort(1)                   // mono
        bb.putInt(SAMPLE_RATE)
        bb.putInt(SAMPLE_RATE * 2)       // byte rate
        bb.putShort(2)                   // block align
        bb.putShort(16)                  // bits per sample
        bb.put("data".toByteArray())
        bb.putInt(pcm.size)
        bb.put(pcm)
        return wav
    }

    private fun processExpense(text: String) {
        val parsed = ExpenseParser.parse(text)
        if (parsed == null) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.could_not_parse_title))
                .setMessage("Heard: \"$text\"\n\n${getString(R.string.could_not_parse_msg)}")
                .setPositiveButton(getString(R.string.re_record)) { _, _ -> startRecording() }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> finish() }
                .show()
            return
        }

        val now = LocalDateTime.now()
        val expense = Expense(
            date = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
            time = now.format(DateTimeFormatter.ofPattern("HH:mm")),
            amount = parsed.amount,
            subject = parsed.description,
            type = parsed.type
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
            startRecording()
        } else {
            Toast.makeText(this, getString(R.string.permission_needed), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onDestroy() {
        recordingJob?.cancel()
        val rec = audioRecord
        audioRecord = null
        try { rec?.stop() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
