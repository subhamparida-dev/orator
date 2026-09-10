package com.example.ai

interface AIProvider {
    val name: String

    suspend fun generateCoaching(
        domain: CoachingDomain,
        prompt: String,
        transcript: String,
        metricsSummary: String,
        memoryContext: String,
        systemInstruction: String
    ): ProviderResult
}

data class ProviderResult(
    val success: Boolean,
    val response: CoachingResponse?,
    val modelName: String,
    val isTemporaryFailure: Boolean = false,
    val error: String? = null
)
