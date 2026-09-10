package com.example.ai

import com.example.data.StudentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AIOrchestrator(
    private val repository: StudentRepository,
    private val geminiProvider: AIProvider = GeminiProvider(),
    private val openRouterProvider: AIProvider = OpenRouterProvider()
) {

    suspend fun analyzeSession(
        domain: CoachingDomain,
        prompt: String,
        transcript: String,
        wpm: Int,
        pauseCount: Int,
        fillerCount: Int,
        repetitionCount: Int,
        slideText: String? = null,
        queryTopic: String? = null
    ): AIResult = withContext(Dispatchers.IO) {
        // 1. Retrieve relevant memory from the unified student brain
        val memoryContext = repository.getRelevantMemoryContext(domain.name, queryTopic)

        val metricsSummary = "WPM: $wpm, Pauses: $pauseCount, Filler Words: $fillerCount, Repetitions: $repetitionCount"

        val systemInstruction = buildString {
            append("You are an expert AI Speaking, Presentation, and Interview Coach designed for ambitious college students. ")
            append("Help the student build confidence, fluency, and articulate professional presentation skills. ")
            append("IMPORTANT MEDICAL NOTICE: This application is NOT a medical diagnostic tool. Never claim to diagnose, quantify, or cure stuttering. ")
            append("Always use respectful, supportive terminology such as 'speech disruptions', 'repetitions', 'restarts', 'hesitation', 'pacing', 'pauses', and 'smooth recovery'. ")
            append("Provide actionable, warm, and highly constructive feedback.")
        }

        // 2. Primary Route: Gemini
        val geminiResult = geminiProvider.generateCoaching(
            domain = domain,
            prompt = prompt,
            transcript = transcript,
            metricsSummary = metricsSummary,
            memoryContext = memoryContext,
            systemInstruction = systemInstruction
        )

        if (geminiResult.success && geminiResult.response != null) {
            // Update shared memory
            persistInsightsToMemory(domain, geminiResult.response)
            return@withContext AIResult(
                success = true,
                response = geminiResult.response,
                providerUsed = "Gemini",
                modelUsed = geminiResult.modelName,
                isFallback = false
            )
        }

        // 3. Fallback Route: OpenRouter (if failure was temporary e.g. 429, timeout, network)
        if (geminiResult.isTemporaryFailure) {
            val openRouterResult = openRouterProvider.generateCoaching(
                domain = domain,
                prompt = prompt,
                transcript = transcript,
                metricsSummary = metricsSummary,
                memoryContext = memoryContext,
                systemInstruction = systemInstruction
            )

            if (openRouterResult.success && openRouterResult.response != null) {
                persistInsightsToMemory(domain, openRouterResult.response)
                return@withContext AIResult(
                    success = true,
                    response = openRouterResult.response,
                    providerUsed = "OpenRouter",
                    modelUsed = openRouterResult.modelName,
                    isFallback = true
                )
            }
        }

        // 4. Offline / Local Heuristic Fallback: Ensures the user is NEVER left stranded
        val offlineCoaching = OfflineAnalyzer.generateLocalCoaching(
            domain = domain,
            prompt = prompt,
            transcript = transcript,
            wpm = wpm,
            pauseCount = pauseCount,
            fillerCount = fillerCount,
            repetitionCount = repetitionCount,
            slideText = slideText
        )

        AIResult(
            success = true,
            response = offlineCoaching,
            providerUsed = "Local Intelligent Coach",
            modelUsed = "Heuristic-Engine-Offline",
            isFallback = true,
            isOffline = true,
            errorMessage = geminiResult.error
        )
    }

    private suspend fun persistInsightsToMemory(domain: CoachingDomain, response: CoachingResponse) {
        // Extract compact, high-value facts into the shared memory system
        response.weaknesses.firstOrNull()?.let { topWeakness ->
            repository.updateMemoryFromAnalysis(
                domain = domain.name,
                key = "weakness_${domain.name.lowercase()}",
                insight = topWeakness,
                importance = 3
            )
        }
        response.strengths.firstOrNull()?.let { topStrength ->
            repository.updateMemoryFromAnalysis(
                domain = domain.name,
                key = "strength_${domain.name.lowercase()}",
                insight = topStrength,
                importance = 2
            )
        }
    }
}
