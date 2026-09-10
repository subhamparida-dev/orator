package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class OpenRouterProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : AIProvider {

    override val name: String = "OpenRouter"

    // 8 configurable model IDs supported using ONE OpenRouter API key
    val fallbackModels = listOf(
        "google/gemini-2.5-flash",
        "anthropic/claude-3.5-haiku",
        "meta-llama/llama-3.3-70b-instruct",
        "mistralai/mistral-small-24b-instruct-2501",
        "deepseek/deepseek-chat",
        "qwen/qwen-2.5-72b-instruct",
        "openai/gpt-4o-mini",
        "google/gemini-2.5-pro"
    )

    override suspend fun generateCoaching(
        domain: CoachingDomain,
        prompt: String,
        transcript: String,
        metricsSummary: String,
        memoryContext: String,
        systemInstruction: String
    ): ProviderResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENROUTER_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_OPENROUTER_API_KEY") {
            return@withContext ProviderResult(
                success = false,
                response = null,
                modelName = fallbackModels[0],
                isTemporaryFailure = true,
                error = "OpenRouter API key is not configured in secrets."
            )
        }

        // Try models sequentially: Model 1 -> Model 2 -> ... -> Model 8
        var lastError: String? = null
        for ((index, modelId) in fallbackModels.withIndex()) {
            val result = callSingleModel(
                modelId = modelId,
                apiKey = apiKey,
                domain = domain,
                prompt = prompt,
                transcript = transcript,
                metricsSummary = metricsSummary,
                memoryContext = memoryContext,
                systemInstruction = systemInstruction
            )

            if (result.success) {
                return@withContext result
            }

            lastError = "Model ${index + 1} ($modelId) failed: ${result.error}"
            // If failure is permanent (e.g. invalid key 401), stop fallback cascade
            if (!result.isTemporaryFailure && result.error?.contains("401") == true) {
                return@withContext result
            }
        }

        ProviderResult(
            success = false,
            response = null,
            modelName = "All 8 OpenRouter models exhausted",
            isTemporaryFailure = true,
            error = lastError ?: "All fallback models failed"
        )
    }

    private fun callSingleModel(
        modelId: String,
        apiKey: String,
        domain: CoachingDomain,
        prompt: String,
        transcript: String,
        metricsSummary: String,
        memoryContext: String,
        systemInstruction: String
    ): ProviderResult {
        try {
            val fullPrompt = buildString {
                append("Domain: ${domain.name}\n")
                append("Context: $prompt\n")
                if (transcript.isNotBlank()) append("User Transcript: \"$transcript\"\n")
                if (metricsSummary.isNotBlank()) append("Local Speech Metrics: $metricsSummary\n")
                if (memoryContext.isNotBlank()) append("Memory Profile: $memoryContext\n")
                append("\nOutput ONLY a valid JSON object matching this schema:\n")
                append("{\"feedback\":\"...\",\"strengths\":[\"...\"],\"weaknesses\":[\"...\"],\"next_action\":\"...\",\"confidence_score\":80,\"fluency_score\":80,\"english_score\":80,\"presentation_score\":80,\"technical_score\":80,\"suggested_exercise\":\"...\",\"follow_up_question\":\"...\"}")
            }

            val requestJson = JSONObject().apply {
                put("model", modelId)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", fullPrompt)
                    })
                }
                put("messages", messages)
                put("temperature", 0.6)
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://ai.studio/build")
                .header("X-Title", "Orator Fluency Coach")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val code = response.code
            val responseText = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val isTemp = code == 429 || (code in 500..599) || code == 408
                return ProviderResult(
                    success = false,
                    response = null,
                    modelName = modelId,
                    isTemporaryFailure = isTemp,
                    error = "HTTP $code: $responseText"
                )
            }

            val parsed = parseOpenRouterResponse(responseText)
            return if (parsed != null) {
                ProviderResult(
                    success = true,
                    response = parsed,
                    modelName = modelId
                )
            } else {
                ProviderResult(
                    success = false,
                    response = null,
                    modelName = modelId,
                    isTemporaryFailure = true,
                    error = "Invalid JSON payload in model completion"
                )
            }
        } catch (e: SocketTimeoutException) {
            return ProviderResult(
                success = false,
                response = null,
                modelName = modelId,
                isTemporaryFailure = true,
                error = "Timeout: ${e.message}"
            )
        } catch (e: IOException) {
            return ProviderResult(
                success = false,
                response = null,
                modelName = modelId,
                isTemporaryFailure = true,
                error = "IO error: ${e.message}"
            )
        } catch (e: Exception) {
            return ProviderResult(
                success = false,
                response = null,
                modelName = modelId,
                isTemporaryFailure = false,
                error = "Exception: ${e.message}"
            )
        }
    }

    private fun parseOpenRouterResponse(rawJson: String): CoachingResponse? {
        return try {
            val root = JSONObject(rawJson)
            val choices = root.optJSONArray("choices") ?: return null
            val firstChoice = choices.optJSONObject(0) ?: return null
            val message = firstChoice.optJSONObject("message") ?: return null
            val content = message.optString("content", "")

            // Extract JSON substring if wrapped in markdown ```json ... ```
            val cleanedJson = if (content.contains("```json")) {
                content.substringAfter("```json").substringBefore("```").trim()
            } else if (content.contains("```")) {
                content.substringAfter("```").substringBefore("```").trim()
            } else {
                content.trim()
            }

            val coachingJson = JSONObject(cleanedJson)
            val strengths = mutableListOf<String>()
            val strArray = coachingJson.optJSONArray("strengths")
            if (strArray != null) {
                for (i in 0 until strArray.length()) strengths.add(strArray.getString(i))
            }
            val weaknesses = mutableListOf<String>()
            val weakArray = coachingJson.optJSONArray("weaknesses")
            if (weakArray != null) {
                for (i in 0 until weakArray.length()) weaknesses.add(weakArray.getString(i))
            }

            CoachingResponse(
                feedback = coachingJson.optString("feedback", "Session reviewed with constructive feedback."),
                strengths = strengths,
                weaknesses = weaknesses,
                next_action = coachingJson.optString("next_action", "Practice pacing and clear transitions."),
                confidence_score = coachingJson.optInt("confidence_score", 78),
                fluency_score = coachingJson.optInt("fluency_score", 78),
                english_score = coachingJson.optInt("english_score", 78),
                presentation_score = coachingJson.optInt("presentation_score", 78),
                technical_score = coachingJson.optInt("technical_score", 78),
                suggested_exercise = coachingJson.optString("suggested_exercise", "Controlled Pauses"),
                follow_up_question = if (coachingJson.has("follow_up_question") && !coachingJson.isNull("follow_up_question")) {
                    coachingJson.getString("follow_up_question")
                } else null
            )
        } catch (e: Exception) {
            null
        }
    }
}
