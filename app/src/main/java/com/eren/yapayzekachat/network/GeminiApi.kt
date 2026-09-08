package com.eren.yapayzekachat.network

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiApi {
    data class AttachmentPayload(
        val name: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    data class RequestMessage(
        val role: String,
        val text: String,
        val attachments: List<AttachmentPayload> = emptyList()
    )

    data class Result(
        val text: String = "",
        val statusCode: Int = 0,
        val rawError: String? = null
    ) {
        val isSuccess: Boolean get() = statusCode in 200..299 && text.isNotBlank()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .build()

    fun listModels(apiKey: String): ResultModels {
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models")
            .get()
            .header("x-goog-api-key", apiKey.trim())
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return ResultModels(emptyList(), response.code, body.ifBlank { response.message })
                }
                val modelsJson = JSONObject(body).optJSONArray("models") ?: JSONArray()
                val models = buildList {
                    for (i in 0 until modelsJson.length()) {
                        val model = modelsJson.optJSONObject(i) ?: continue
                        val methods = model.optJSONArray("supportedGenerationMethods") ?: continue
                        var supports = false
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                supports = true
                                break
                            }
                        }
                        if (supports) add(model.optString("name").removePrefix("models/"))
                    }
                }.distinct()
                ResultModels(models, response.code)
            }
        } catch (e: Exception) {
            ResultModels(emptyList(), 0, e.message ?: e.javaClass.simpleName)
        }
    }

    fun generate(
        apiKey: String,
        model: String,
        messages: List<RequestMessage>,
        systemInstruction: String
    ): Result {
        val payload = JSONObject()
        val contents = JSONArray()

        messages.forEach { message ->
            val content = JSONObject().put("role", if (message.role == "assistant") "model" else "user")
            val parts = JSONArray()
            if (message.text.isNotBlank()) {
                parts.put(JSONObject().put("text", message.text))
            }
            message.attachments.forEach { file ->
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", file.mimeType)
                            .put("data", Base64.encodeToString(file.bytes, Base64.NO_WRAP))
                    )
                )
            }
            if (parts.length() == 0) parts.put(JSONObject().put("text", ""))
            content.put("parts", parts)
            contents.put(content)
        }

        payload.put("contents", contents)
        payload.put(
            "systemInstruction",
            JSONObject().put(
                "parts",
                JSONArray().put(JSONObject().put("text", systemInstruction))
            )
        )
        payload.put("generationConfig", JSONObject().put("temperature", 0.7))

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey.trim())
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Result(statusCode = response.code, rawError = extractError(body).ifBlank { response.message })
                } else {
                    val root = JSONObject(body)
                    val candidates = root.optJSONArray("candidates")
                    val first = candidates?.optJSONObject(0)
                    val candidateContent = first?.optJSONObject("content")
                    val parts = candidateContent?.optJSONArray("parts")
                    val text = buildString {
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.optJSONObject(i) ?: continue
                                append(part.optString("text"))
                            }
                        }
                    }.trim()
                    if (text.isBlank()) {
                        Result(statusCode = 502, rawError = "Model boş yanıt döndürdü.")
                    } else {
                        Result(text = text, statusCode = response.code)
                    }
                }
            }
        } catch (e: Exception) {
            Result(statusCode = 0, rawError = e.message ?: e.javaClass.simpleName)
        }
    }

    private fun extractError(body: String): String {
        return try {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message").orEmpty().ifBlank { body }
        } catch (_: Exception) {
            body
        }
    }

    data class ResultModels(
        val models: List<String>,
        val statusCode: Int,
        val error: String? = null
    )

}
