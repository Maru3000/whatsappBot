package com.maru.expenserecorder

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DeepgramClient {

    fun transcribe(apiKey: String, savedLangs: String, wavBytes: ByteArray): String {
        val langParam = when {
            savedLangs.contains(",") -> "detect_language=true"
            savedLangs.trim() == "he-IL" -> "language=he"
            else -> "language=en-US"
        }
        val url = URL("https://api.deepgram.com/v1/listen?model=nova-2&smart_format=true&$langParam")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Token $apiKey")
            conn.setRequestProperty("Content-Type", "audio/wav")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            conn.outputStream.use { it.write(wavBytes) }

            val code = conn.responseCode
            val body = if (code == 200) conn.inputStream.bufferedReader().readText()
                       else conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"

            if (code != 200) throw RuntimeException("Deepgram error $code: $body")

            return JSONObject(body)
                .getJSONObject("results")
                .getJSONArray("channels")
                .getJSONObject(0)
                .getJSONArray("alternatives")
                .getJSONObject(0)
                .getString("transcript")
        } finally {
            conn.disconnect()
        }
    }
}
