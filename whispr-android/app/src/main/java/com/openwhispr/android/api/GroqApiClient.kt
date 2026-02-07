package com.openwhispr.android.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GroqApiClient(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(
        audioFile: File,
        model: String = "whisper-large-v3-turbo",
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/mp4".toMediaType())
                )
                .addFormDataPart("model", model)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/audio/transcriptions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(Exception("Groq API error ${response.code}: ${parseError(errorBody)}"))
            }

            val json = JSONObject(response.body!!.string())
            Result.success(json.getString("text"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send transcribed text through a Groq LLM to clean up grammar,
     * punctuation, and formatting — mirroring the desktop ReasoningService.
     */
    suspend fun reasonText(
        text: String,
        model: String = DEFAULT_REASONING_MODEL,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", "$USER_PROMPT\n\n$text"))
            }

            val body = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", 0.3)
                put("max_tokens", maxOf(256, text.length * 2).coerceAtMost(4096))
            }

            val request = Request.Builder()
                .url("$BASE_URL/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext Result.failure(Exception("Groq reasoning error ${response.code}: ${parseError(errorBody)}"))
            }

            val json = JSONObject(response.body!!.string())
            val content = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()

            if (content.isBlank()) {
                return@withContext Result.success(text) // fallback to original
            }

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseError(body: String): String = try {
        val json = JSONObject(body)
        json.optJSONObject("error")?.optString("message") ?: body
    } catch (_: Exception) {
        body
    }

    companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1"

        const val DEFAULT_REASONING_MODEL = "llama-3.3-70b-versatile"

        private const val SYSTEM_PROMPT =
            "You are a dictation assistant. Clean up text by fixing grammar " +
            "and punctuation. Output ONLY the cleaned text without any " +
            "explanations, options, or commentary."

        private const val USER_PROMPT =
            "Clean up the following dictated text by fixing grammar, " +
            "punctuation, and formatting. Output ONLY the cleaned text:"

        val AVAILABLE_REASONING_MODELS = listOf(
            "llama-3.3-70b-versatile" to "LLaMA 3.3 70B (best quality)",
            "llama-3.1-8b-instant" to "LLaMA 3.1 8B (fastest)",
            "gemma2-9b-it" to "Gemma 2 9B",
            "mixtral-8x7b-32768" to "Mixtral 8x7B",
        )
    }
}
