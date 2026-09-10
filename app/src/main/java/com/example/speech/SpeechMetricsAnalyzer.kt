package com.example.speech

data class LiveSpeechMetrics(
    val wpm: Int = 0,
    val wordCount: Int = 0,
    val pauseCount: Int = 0,
    val naturalPauses: Int = 0,
    val longSilences: Int = 0,
    val fillerCount: Int = 0,
    val repetitionCount: Int = 0,
    val recoveryScore: Int = 100, // 0-100 non-medical communication metric
    val fillerWordsFound: List<String> = emptyList(),
    val speakingDurationSeconds: Int = 0,
    val pacingPraise: String = "Pacing Steady",
    val actionableFeedback: List<String> = emptyList()
)

data class SessionComparison(
    val previousWpm: Int,
    val currentWpm: Int,
    val wpmDelta: Int,
    val previousFillers: Int,
    val currentFillers: Int,
    val fillerDelta: Int,
    val previousConfidence: Int,
    val currentConfidence: Int,
    val confidenceDelta: Int,
    val previousRecoveryScore: Int,
    val currentRecoveryScore: Int,
    val recoveryDelta: Int,
    val interpretation: String
)

object SpeechMetricsAnalyzer {

    val DEFAULT_FILLER_WORDS = listOf(
        "um", "uh", "like", "you know", "basically",
        "actually", "sort of", "kind of", "i mean", "so yeah"
    )

    fun analyze(
        transcript: String,
        durationSeconds: Int,
        detectedPauses: Int = 0,
        contextCategory: String = "CASUAL",
        customFillers: List<String> = emptyList()
    ): LiveSpeechMetrics {
        if (transcript.isBlank() || durationSeconds <= 0) {
            return LiveSpeechMetrics()
        }

        val words = transcript.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        val wordCount = words.size

        // WPM calculation: (words / durationSeconds) * 60
        val wpm = if (durationSeconds > 0) {
            ((wordCount.toFloat() / durationSeconds.toFloat()) * 60f).toInt()
        } else 0

        // Filler word detection with configurable list
        val activeFillers = if (customFillers.isNotEmpty()) customFillers else DEFAULT_FILLER_WORDS
        val fillerPatterns = activeFillers.map { "\\b${Regex.escape(it)}\\b".toRegex(RegexOption.IGNORE_CASE) }
        val fillersFound = mutableListOf<String>()
        fillerPatterns.forEach { regex ->
            val matches = regex.findAll(transcript)
            matches.forEach { fillersFound.add(it.value.lowercase()) }
        }

        // Repetition / Restart detection (e.g., "I I think", "we we need")
        var repetitions = 0
        for (i in 0 until words.size - 1) {
            val curr = words[i].lowercase().replace(Regex("[^a-z]"), "")
            val next = words[i + 1].lowercase().replace(Regex("[^a-z]"), "")
            if (curr.isNotBlank() && curr == next) {
                repetitions++
            }
        }

        // Categorize pauses conceptually:
        // Natural pauses: expected at clause boundaries (~1 per 8-15 words or ~3-6 per min)
        // Long silences: pauses beyond normal thought phrasing
        val naturalPauses = detectedPauses.coerceAtMost((durationSeconds / 8).coerceAtLeast(1))
        val longSilences = (detectedPauses - naturalPauses).coerceAtLeast(0)

        // Speech Recovery Score: Non-medical communication metric representing
        // how smoothly the speaker resumes speaking after detected pauses/restarts.
        var recoveryScoreCalc = 100
        recoveryScoreCalc -= (repetitions * 7).coerceAtMost(30)
        recoveryScoreCalc -= (longSilences * 5).coerceAtMost(20)
        if (wpm in 115..155) {
            recoveryScoreCalc = (recoveryScoreCalc + 5).coerceAtMost(100)
        } else if (wpm > 175 || (wpm < 75 && wordCount > 5)) {
            recoveryScoreCalc = (recoveryScoreCalc - 5).coerceAtLeast(50)
        }
        val recoveryScore = recoveryScoreCalc.coerceIn(50, 100)

        val pacingFeedback = when (contextCategory.uppercase()) {
            "CONTROLLED", "EASY_ONSET" -> when {
                wpm in 85..125 -> "Ideal Controlled Pacing (85-125 WPM)"
                wpm > 130 -> "Brisk for Easy Onset — Slow Down Intentionally"
                else -> "Gentle, Deliberate Pace"
            }
            "READING" -> when {
                wpm in 120..150 -> "Fluent Reading Rhythm (120-150 WPM)"
                wpm > 160 -> "Reading Fast — Insert Thought Chunk Pauses"
                else -> "Measured Reading Pace"
            }
            "PRESENTATION" -> when {
                wpm in 125..155 -> "Great Keynote Cadence (125-155 WPM)"
                wpm > 165 -> "High Energy — Pause for Key Ideas to Land"
                else -> "Deliberate Technical Pacing"
            }
            else -> when {
                wpm in 120..155 -> "Great Pacing (120-155 WPM)"
                wpm in 90..119 -> "Controlled & Measured (< 120 WPM)"
                wpm > 165 -> "Brisk Pace (> 165 WPM - Try Pausing)"
                else -> "Steady Pace"
            }
        }

        val feedbackList = mutableListOf<String>()
        if (wpm in 120..155) {
            feedbackList.add("Cadence remained steady and articulate throughout.")
        } else if (wpm > 165) {
            feedbackList.add("Pace accelerated slightly; try inserting a calm 1-second pause before key points.")
        } else if (wpm < 95 && wordCount > 10) {
            feedbackList.add("Deliberate, careful delivery. You can gradually build forward momentum.")
        }

        if (fillersFound.isNotEmpty()) {
            val topFiller = fillersFound.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            feedbackList.add("Most frequent filler was '$topFiller'. Practice replacing it with a quiet pause.")
        }

        if (repetitions > 0) {
            feedbackList.add("Detected $repetitions restart(s). Remember: pause, breathe, and calmly restart without pushing.")
        } else if (wordCount > 15) {
            feedbackList.add("Smooth continuous delivery with clean syllable transitions.")
        }

        if (naturalPauses > 0) {
            feedbackList.add("Good thought group rhythm with $naturalPauses natural pause points.")
        }

        return LiveSpeechMetrics(
            wpm = wpm,
            wordCount = wordCount,
            pauseCount = detectedPauses,
            naturalPauses = naturalPauses,
            longSilences = longSilences,
            fillerCount = fillersFound.size,
            repetitionCount = repetitions,
            recoveryScore = recoveryScore,
            fillerWordsFound = fillersFound,
            speakingDurationSeconds = durationSeconds,
            pacingPraise = pacingFeedback,
            actionableFeedback = feedbackList
        )
    }

    fun compareSessions(
        current: LiveSpeechMetrics,
        currentPostConfidence: Int,
        previous: com.example.data.PracticeSession?
    ): SessionComparison? {
        if (previous == null) return null

        val wpmDelta = current.wpm - previous.wpm
        val fillerDelta = current.fillerCount - previous.fillerCount
        val confDelta = currentPostConfidence - previous.confidencePost
        val prevRecovery = (100 - (previous.repetitionCount * 7).coerceAtMost(30)).coerceIn(50, 100)
        val recDelta = current.recoveryScore - prevRecovery

        val interpretations = mutableListOf<String>()
        if (fillerDelta < 0) {
            interpretations.add("Filler words decreased by ${-fillerDelta} compared to your previous session.")
        } else if (fillerDelta > 0) {
            interpretations.add("Filler count was +$fillerDelta higher; focus on silent pause replacement.")
        } else {
            interpretations.add("Filler word frequency remained consistent.")
        }

        if (Math.abs(current.wpm - 135) < Math.abs(previous.wpm - 135)) {
            interpretations.add("Pace moved closer to the balanced 135 WPM benchmark.")
        } else if (Math.abs(wpmDelta) > 20) {
            interpretations.add("Noticeable pace shift (${if (wpmDelta > 0) "+" else ""}$wpmDelta WPM).")
        }

        if (confDelta > 0) {
            interpretations.add("Confidence improved (+${confDelta} pts).")
        } else if (confDelta < 0) {
            interpretations.add("Keep practicing; comfort grows with steady exposure.")
        }

        val finalSummary = if (interpretations.isNotEmpty()) interpretations.joinToString(" ") else "Good effort completing this practice round."

        return SessionComparison(
            previousWpm = previous.wpm,
            currentWpm = current.wpm,
            wpmDelta = wpmDelta,
            previousFillers = previous.fillerCount,
            currentFillers = current.fillerCount,
            fillerDelta = fillerDelta,
            previousConfidence = previous.confidencePost,
            currentConfidence = currentPostConfidence,
            confidenceDelta = confDelta,
            previousRecoveryScore = prevRecovery,
            currentRecoveryScore = current.recoveryScore,
            recoveryDelta = recDelta,
            interpretation = finalSummary
        )
    }
}
