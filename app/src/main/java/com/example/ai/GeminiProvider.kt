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

class GeminiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) : AIProvider {

    override val name: String = "Gemini"
    private val model: String = "gemini-3.5-flash"

    override suspend fun generateCoaching(
        domain: CoachingDomain,
        prompt: String,
        transcript: String,
        metricsSummary: String,
        memoryContext: String,
        systemInstruction: String
    ): ProviderResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext ProviderResult(
                success = false,
                response = null,
                modelName = model,
                isTemporaryFailure = true, // allow fallback to OpenRouter or offline
                error = "Gemini API key is not configured in secrets."
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val fullUserPrompt = buildString {
                append("Domain: ${domain.name}\n")
                append("Context & Task: $prompt\n")
                if (transcript.isNotBlank()) append("User Transcript: \"$transcript\"\n")
                if (metricsSummary.isNotBlank()) append("Local Speech Metrics: $metricsSummary\n")
                if (memoryContext.isNotBlank()) append("Student Memory Profile: $memoryContext\n")
                append("\nYou must reply with a valid JSON object matching this schema:\n")
                append("{\n")
                append("  \"feedback\": \"Constructive, non-medical feedback emphasizing strengths and clear coaching\",\n")
                append("  \"strengths\": [\"strength 1\", \"strength 2\"],\n")
                append("  \"weaknesses\": [\"specific area for improvement\"],\n")
                append("  \"next_action\": \"concrete practice suggestion\",\n")
                append("  \"confidence_score\": 80,\n")
                append("  \"fluency_score\": 80,\n")
                append("  \"english_score\": 80,\n")
                append("  \"presentation_score\": 80,\n")
                append("  \"technical_score\": 80,\n")
                append("  \"suggested_exercise\": \"Exercise title to practice\",\n")
                append("  \"follow_up_question\": \"Optional follow-up question if interview or presentation, or null\"\n")
                append("}")
            }

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", fullUserPrompt) })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val systemInstructionObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstruction) })
                    }
                    put("parts", parts)
                }
                put("systemInstruction", systemInstructionObj)

                val genConfig = JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.6)
                }
                put("generationConfig", genConfig)
            }

            val body = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseCode = response.code
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val isTemp = responseCode == 429 || (responseCode in 500..599)
                return@withContext ProviderResult(
                    success = false,
                    response = null,
                    modelName = model,
                    isTemporaryFailure = isTemp,
                    error = "HTTP $responseCode: $responseBody"
                )
            }

            val parsedResponse = parseGeminiResponse(responseBody)
            if (parsedResponse != null) {
                ProviderResult(
                    success = true,
                    response = parsedResponse,
                    modelName = model
                )
            } else {
                ProviderResult(
                    success = false,
                    response = null,
                    modelName = model,
                    isTemporaryFailure = true,
                    error = "Failed to parse JSON response from Gemini"
                )
            }
        } catch (e: SocketTimeoutException) {
            ProviderResult(
                success = false,
                response = null,
                modelName = model,
                isTemporaryFailure = true,
                error = "Timeout contacting Gemini API: ${e.message}"
            )
        } catch (e: IOException) {
            ProviderResult(
                success = false,
                response = null,
                modelName = model,
                isTemporaryFailure = true,
                error = "Network IO error contacting Gemini: ${e.message}"
            )
        } catch (e: Exception) {
            ProviderResult(
                success = false,
                response = null,
                modelName = model,
                isTemporaryFailure = false,
                error = "Gemini provider exception: ${e.message}"
            )
        }
    }

    private fun parseGeminiResponse(rawJson: String): CoachingResponse? {
        return try {
            val root = JSONObject(rawJson)
            val candidates = root.optJSONArray("candidates") ?: return null
            val firstCandidate = candidates.optJSONObject(0) ?: return null
            val content = firstCandidate.optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            val textPart = parts.optJSONObject(0)?.optString("text") ?: return null

            val coachingJson = JSONObject(textPart)
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
                feedback = coachingJson.optString("feedback", "Good effort on this session."),
                strengths = strengths,
                weaknesses = weaknesses,
                next_action = coachingJson.optString("next_action", "Continue daily speaking exercises."),
                confidence_score = coachingJson.optInt("confidence_score", 75),
                fluency_score = coachingJson.optInt("fluency_score", 75),
                english_score = coachingJson.optInt("english_score", 75),
                presentation_score = coachingJson.optInt("presentation_score", 75),
                technical_score = coachingJson.optInt("technical_score", 75),
                suggested_exercise = coachingJson.optString("suggested_exercise", "Paced Speaking Practice"),
                follow_up_question = if (coachingJson.has("follow_up_question") && !coachingJson.isNull("follow_up_question")) {
                    coachingJson.getString("follow_up_question")
                } else null
            )
        } catch (e: Exception) {
            null
        }
    }
}
