package com.example.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class StudentRepository(private val db: AppDatabase) {
    val studentProfile: Flow<StudentProfile?> = db.studentProfileDao().getProfileFlow()
    val allSessions: Flow<List<PracticeSession>> = db.practiceSessionDao().getAllSessions()
    val recentSessions: Flow<List<PracticeSession>> = db.practiceSessionDao().getRecentSessions(10)
    val weakWords: Flow<List<WeakWord>> = db.weakWordDao().getAllWeakWords()
    val allPresentations: Flow<List<Presentation>> = db.presentationDao().getAllPresentations()
    val allInterviews: Flow<List<InterviewRecord>> = db.interviewRecordDao().getAllInterviews()
    val totalSessionsCount: Flow<Int> = db.practiceSessionDao().getTotalSessionsCount()
    val totalSpeakingDuration: Flow<Int?> = db.practiceSessionDao().getTotalDurationSeconds()

    suspend fun getProfileDirect(): StudentProfile {
        return db.studentProfileDao().getProfile() ?: StudentProfile()
    }

    suspend fun saveProfile(profile: StudentProfile) {
        db.studentProfileDao().saveProfile(profile)
    }

    suspend fun saveSession(session: PracticeSession): Long {
        val id = db.practiceSessionDao().insertSession(session)
        // Update profile speaking minutes and streak
        val currentProfile = getProfileDirect()
        val addedMinutes = (session.durationSeconds / 60).coerceAtLeast(1)
        val updatedStreak = if (System.currentTimeMillis() - currentProfile.lastActiveDate < 86400000L * 2) {
            currentProfile.currentStreak + 1
        } else {
            1
        }
        db.studentProfileDao().saveProfile(
            currentProfile.copy(
                totalSpeakingMinutes = currentProfile.totalSpeakingMinutes + addedMinutes,
                currentStreak = updatedStreak,
                lastActiveDate = System.currentTimeMillis()
            )
        )
        return id
    }

    suspend fun addWeakWord(word: String, phonetic: String = "", contextSentence: String = "", category: String = "FLUENCY") {
        val cleanWord = word.trim().lowercase().replaceFirstChar { it.uppercase() }
        val existing = db.weakWordDao().findWord(cleanWord)
        if (existing != null) {
            db.weakWordDao().insertOrUpdate(
                existing.copy(
                    encounterCount = existing.encounterCount + 1,
                    lastPracticed = System.currentTimeMillis()
                )
            )
        } else {
            db.weakWordDao().insertOrUpdate(
                WeakWord(
                    word = cleanWord,
                    phonetic = phonetic,
                    category = category,
                    contextSentence = contextSentence,
                    encounterCount = 1,
                    masteryScore = 35,
                    lastPracticed = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateWordMastery(wordId: Long, newMasteryScore: Int) {
        val existing = db.weakWordDao().getWordById(wordId)
        if (existing != null) {
            db.weakWordDao().insertOrUpdate(
                existing.copy(
                    masteryScore = newMasteryScore.coerceIn(0, 100),
                    lastPracticed = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun updateWeakWord(word: WeakWord) {
        db.weakWordDao().insertOrUpdate(word)
    }

    suspend fun deleteWeakWord(word: WeakWord) {
        db.weakWordDao().deleteWord(word)
    }

    suspend fun seedInitialPracticeWordsIfEmpty() {
        val initialWords = listOf(
            Triple("Architecture", "/ˈɑːrkɪtɛktʃər/ • Arch-i-tec-ture", "We designed a modular microservices architecture to ensure high throughput and fault tolerance."),
            Triple("Synchronization", "/ˌsɪŋkrənɪˈzeɪʃən/ • Syn-chro-ni-za-tion", "Multi-threaded synchronization prevents race conditions across distributed worker nodes."),
            Triple("Implementation", "/ˌɪmplɪmɛnˈteɪʃən/ • Im-ple-men-ta-tion", "Our database implementation prioritizes zero-copy serialization and fast lookup indexes."),
            Triple("Authentication", "/ɔːˌθɛntɪˈkeɪʃən/ • Au-then-ti-ca-tion", "Federated authentication secures student identities across internal service endpoints."),
            Triple("Vulnerability", "/ˌvʌlnərəˈbɪlɪti/ • Vul-ner-a-bil-i-ty", "Automated static analysis uncovered zero critical security vulnerabilities."),
            Triple("Observability", "/əbˌzɜːrvəˈbɪlɪti/ • Ob-serv-a-bil-i-ty", "Full-stack observability combines structured distributed tracing with real-time health telemetry.")
        )
        for ((word, phonetic, sentence) in initialWords) {
            if (db.weakWordDao().findWord(word) == null) {
                db.weakWordDao().insertOrUpdate(
                    WeakWord(
                        word = word,
                        phonetic = phonetic,
                        category = "FLUENCY",
                        contextSentence = sentence,
                        encounterCount = 1,
                        masteryScore = 40,
                        lastPracticed = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    suspend fun savePresentation(presentation: Presentation): Long {
        return db.presentationDao().insertPresentation(presentation)
    }

    suspend fun getPresentationById(id: Long): Presentation? {
        return db.presentationDao().getPresentationById(id)
    }

    suspend fun recordInterview(record: InterviewRecord): Long {
        return db.interviewRecordDao().insertInterview(record)
    }

    suspend fun getRelevantMemoryContext(domain: String, queryTopic: String? = null): String {
        val domainFacts = db.memoryFactDao().getFactsByDomain(domain, limit = 4)
        val allRecentFacts = db.memoryFactDao().getRecentFacts()
        val combinedFacts = (domainFacts + allRecentFacts).distinctBy { it.factKey }.take(6)
        val profile = getProfileDirect()
        val builder = StringBuilder()
        builder.append("Student Profile: ${profile.name}, Role Target: ${profile.targetRole}, Preferred Feedback: ${profile.preferredCorrectionStyle}.\n")
        if (combinedFacts.isNotEmpty()) {
            builder.append("Known Student Patterns & History across practice sessions:\n")
            combinedFacts.forEach { fact ->
                builder.append("- [${fact.domain}] (${fact.factKey}): ${fact.factValue}\n")
            }
        }
        if (!queryTopic.isNullOrBlank()) {
            builder.append("Current Focus Area: $queryTopic\n")
        }
        return builder.toString()
    }

    suspend fun updateMemoryFromAnalysis(domain: String, key: String, insight: String, importance: Int = 2) {
        db.memoryFactDao().insertFact(
            MemoryFact(
                domain = domain,
                factKey = key,
                factValue = insight,
                importance = importance,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun exportUserDataJson(): String {
        val profile = getProfileDirect()
        val facts = db.memoryFactDao().getRecentFacts()
        val root = JSONObject()
        val profileObj = JSONObject().apply {
            put("name", profile.name)
            put("college", profile.college)
            put("targetRole", profile.targetRole)
            put("streak", profile.currentStreak)
            put("totalMinutes", profile.totalSpeakingMinutes)
        }
        root.put("profile", profileObj)
        val factsArray = JSONArray()
        facts.forEach { f ->
            val fo = JSONObject().apply {
                put("domain", f.domain)
                put("key", f.factKey)
                put("value", f.factValue)
            }
            factsArray.put(fo)
        }
        root.put("memoryFacts", factsArray)
        return root.toString(2)
    }

    suspend fun resetUserData() {
        db.practiceSessionDao().clearAll()
        db.memoryFactDao().clearAll()
        db.studentProfileDao().saveProfile(
            StudentProfile(
                id = 1,
                name = "Alex Rivera",
                college = "College of Computer Engineering",
                currentStreak = 1,
                totalSpeakingMinutes = 0
            )
        )
    }
}
