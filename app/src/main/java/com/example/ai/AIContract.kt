package com.example.ai

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CoachingResponse(
    val feedback: String = "",
    val strengths: List<String> = emptyList(),
    val weaknesses: List<String> = emptyList(),
    val next_action: String = "",
    val confidence_score: Int = 75,
    val fluency_score: Int = 75,
    val english_score: Int = 75,
    val presentation_score: Int = 75,
    val technical_score: Int = 75,
    val suggested_exercise: String = "Controlled pauses and thought groups",
    val follow_up_question: String? = null
)

data class AIResult(
    val success: Boolean,
    val response: CoachingResponse,
    val providerUsed: String,
    val modelUsed: String,
    val isFallback: Boolean = false,
    val isOffline: Boolean = false,
    val errorMessage: String? = null
)

enum class CoachingDomain {
    FLUENCY,
    ENGLISH,
    PRESENTATION,
    INTERVIEW,
    TEACHER_QNA
}
