package com.openwhispr.android.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
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
                val errorMsg = try {
                    val json = JSONObject(errorBody)
                    json.optJSONObject("error")?.optString("message") ?: errorBody
                } catch (_: Exception) {
                    errorBody
                }
                return@withContext Result.failure(Exception("Groq API error ${response.code}: $errorMsg"))
            }

            val json = JSONObject(response.body!!.string())
            Result.success(json.getString("text"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1"
    }
}
