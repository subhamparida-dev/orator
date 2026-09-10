package com.example.ai

object OfflineAnalyzer {

    fun generateLocalCoaching(
        domain: CoachingDomain,
        prompt: String,
        transcript: String,
        wpm: Int,
        pauseCount: Int,
        fillerCount: Int,
        repetitionCount: Int,
        slideText: String? = null
    ): CoachingResponse {
        val wordCount = transcript.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
        val strengths = mutableListOf<String>()
        val weaknesses = mutableListOf<String>()

        // 1. Pacing & Fluency Analysis
        var fluencyScore = 80
        if (wpm in 120..155) {
            strengths.add("Optimal speaking tempo ($wpm WPM) maintains clarity and listener engagement.")
            fluencyScore += 8
        } else if (wpm > 165) {
            weaknesses.add("Speaking pace was slightly elevated ($wpm WPM). Practice intentional pauses between thoughts.")
            fluencyScore -= 12
        } else if (wpm in 80..115) {
            strengths.add("Deliberate, measured cadence ($wpm WPM) aids technical articulation.")
            fluencyScore += 2
        } else if (wpm < 80 && wordCount > 5) {
            weaknesses.add("Pace was notably slow ($wpm WPM); work on continuous phonation across word boundaries.")
            fluencyScore -= 15
        }

        // 2. Pause & Hesitation Analysis
        if (pauseCount in 2..7) {
            strengths.add("Effective use of thought-group pauses ($pauseCount pauses) to punctuate key ideas.")
            fluencyScore += 5
        } else if (pauseCount > 10 && wordCount < 60) {
            weaknesses.add("Noticeable frequency of restarts and pauses ($pauseCount). Try chunking sentences into 4-6 word units.")
            fluencyScore -= 10
        }

        // 3. Filler Word Analysis
        if (fillerCount == 0 && wordCount > 15) {
            strengths.add("Crisp speech delivery free of common fillers ('um', 'like', 'basically').")
            fluencyScore += 7
        } else if (fillerCount in 1..3) {
            strengths.add("Minimal filler word presence ($fillerCount occurrences); smooth recovery observed.")
        } else if (fillerCount > 3) {
            weaknesses.add("Detected $fillerCount filler words. Try replacing fillers with a silent breath pause.")
            fluencyScore -= 10
        }

        // 4. English & Vocabulary Quality
        var englishScore = 78
        if (wordCount > 30) {
            strengths.add("Substantive spoken response ($wordCount words) with coherent sentence structure.")
            englishScore += 8
        } else if (wordCount < 10 && wordCount > 0) {
            weaknesses.add("Brief explanation. Strive to expand using the 'Point-Reason-Example' framework.")
            englishScore -= 10
        }

        // Check for academic/technical vocabulary
        val techWords = listOf("architecture", "scalability", "implementation", "process", "framework", "database", "latency", "algorithm", "design", "efficient", "resilience")
        val foundTech = techWords.filter { transcript.lowercase().contains(it) }
        if (foundTech.isNotEmpty()) {
            strengths.add("Good integration of professional vocabulary: ${foundTech.take(3).joinToString(", ")}.")
            englishScore += 5
        }

        // 5. Presentation / "Don't read, explain" detection
        var presentationScore = 76
        if (!slideText.isNullOrBlank() && wordCount > 10) {
            val stopWords = setOf(
                "the", "a", "an", "is", "in", "at", "of", "on", "and", "to", "for", "with", "it",
                "as", "by", "this", "that", "are", "was", "were", "we", "our", "you", "your", "i",
                "my", "or", "be", "from", "have", "has", "will", "so", "but", "not", "can", "if"
            )
            val slideWords = slideText.lowercase().split("\\s+".toRegex())
                .map { it.replace("[^a-zA-Z0-9]".toRegex(), "") }
                .filter { it.length > 2 && it !in stopWords }
                .toSet()
            val spokenWords = transcript.lowercase().split("\\s+".toRegex())
                .map { it.replace("[^a-zA-Z0-9]".toRegex(), "") }
                .filter { it.length > 2 && it !in stopWords }

            if (spokenWords.size >= 8 && slideWords.isNotEmpty()) {
                val verbatimCount = spokenWords.count { slideWords.contains(it) }
                val verbatimRatio = verbatimCount.toFloat() / spokenWords.size

                if (verbatimRatio > 0.65f) {
                    weaknesses.add("High overlap with slide bullet points ($verbatimCount matching content words). Focus on explaining why this matters rather than reading text verbatim.")
                    presentationScore -= 15
                } else {
                    strengths.add("Effective conceptual delivery: explained the ideas in your own words rather than reciting slide bullets verbatim.")
                    presentationScore += 10
                }
            }
        }

        val technicalScore = if (foundTech.size >= 2) 85 else 75
        val confidenceScore = ((fluencyScore + englishScore + presentationScore) / 3).coerceIn(60, 95)
        fluencyScore = fluencyScore.coerceIn(50, 98)
        englishScore = englishScore.coerceIn(50, 98)
        presentationScore = presentationScore.coerceIn(50, 98)

        if (strengths.isEmpty()) {
            strengths.add("Consistent effort and clear attempt at vocal projection.")
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("Maintain this steady rhythm while gradually expanding your vocabulary range.")
        }

        val feedback = buildString {
            append("Solid practice attempt. ")
            if (fluencyScore >= 80) {
                append("Your pacing and speech rhythm showed good control. ")
            } else {
                append("Focus on easy-onset vocal starts and deliberate pauses. ")
            }
            if (weaknesses.isNotEmpty()) {
                append(weaknesses.first())
            }
        }

        val suggestedExercise = when (domain) {
            CoachingDomain.FLUENCY -> if (fillerCount > 3) "Controlled Pauses & Breathing" else "Gentle Speech & Continuous Phonation"
            CoachingDomain.ENGLISH -> "Daily Spoken Thought Chunking"
            CoachingDomain.PRESENTATION -> "Slide Explanation without Reading"
            CoachingDomain.INTERVIEW -> "STAR Method Technical Delivery"
            CoachingDomain.TEACHER_QNA -> "Handling Tough Technical Follow-ups"
        }

        val followUp = when (domain) {
            CoachingDomain.INTERVIEW -> "How would you address performance bottlenecks if traffic scaled by 10x?"
            CoachingDomain.TEACHER_QNA -> "What is the primary trade-off in the architecture you selected?"
            else -> null
        }

        return CoachingResponse(
            feedback = feedback,
            strengths = strengths,
            weaknesses = weaknesses,
            next_action = "Practice with focus on: ${weaknesses.firstOrNull() ?: "steady tempo"}",
            confidence_score = confidenceScore,
            fluency_score = fluencyScore,
            english_score = englishScore,
            presentation_score = presentationScore,
            technical_score = technicalScore,
            suggested_exercise = suggestedExercise,
            follow_up_question = followUp
        )
    }
}
