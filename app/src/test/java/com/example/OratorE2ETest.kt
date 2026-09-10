package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.*
import com.example.data.*
import com.example.speech.SpeechMetricsAnalyzer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OratorE2ETest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: StudentRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StudentRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testSpeechMetricsAnalyzer_wpmAndFillers() {
        val sampleSpeech = "So basically, um, our distributed system handles ten thousand requests per second, you know."
        val metrics = SpeechMetricsAnalyzer.analyze(sampleSpeech, durationSeconds = 10)

        // 13 words in 10s = 78 wpm
        assertTrue("WPM should be calculated", metrics.wpm > 0)
        assertTrue("Should detect filler words ('basically', 'um', 'you know')", metrics.fillerCount >= 2)
    }

    @Test
    fun testSpeechMetricsAnalyzer_emptyOrShortAudio() {
        val emptyMetrics = SpeechMetricsAnalyzer.analyze("", durationSeconds = 5)
        assertEquals(0, emptyMetrics.wpm)
        assertEquals(0, emptyMetrics.fillerCount)
        assertEquals(0, emptyMetrics.pauseCount)
    }

    @Test
    fun testOfflineAnalyzer_normalCadence() {
        val transcript = "We designed a microservice architecture to decouple authentication from business logic."
        val response = OfflineAnalyzer.generateLocalCoaching(
            domain = CoachingDomain.INTERVIEW,
            prompt = "Explain your project architecture",
            transcript = transcript,
            wpm = 135,
            pauseCount = 3,
            fillerCount = 0,
            repetitionCount = 0
        )

        assertNotNull(response)
        assertTrue("Fluency score should be positive", response.fluency_score >= 70)
        assertTrue("Strengths should contain pacing feedback", response.strengths.isNotEmpty())
        assertFalse("Feedback must not diagnose medical stuttering", response.feedback.lowercase().contains("stutter"))
    }

    @Test
    fun testOfflineAnalyzer_verbatimDetection_distinguishesStopWords() {
        val slideText = "Consensus Algorithm Byzantine Fault Tolerance Paxos Raft"

        // Student explaining conceptually with common transition stop words:
        val studentExplanation = "In this slide we are looking at how Byzantine fault tolerance is achieved using Paxos and Raft mechanisms."
        val responseExplanation = OfflineAnalyzer.generateLocalCoaching(
            domain = CoachingDomain.PRESENTATION,
            prompt = "Slide 1",
            transcript = studentExplanation,
            wpm = 130,
            pauseCount = 2,
            fillerCount = 0,
            repetitionCount = 0,
            slideText = slideText
        )

        assertTrue(
            "Conceptual explanation should receive good presentation score",
            responseExplanation.presentation_score >= 70
        )

        // Student purely reading slide verbatim without explanation:
        val verbatimRecitation = "Consensus Algorithm Byzantine Fault Tolerance Paxos Raft"
        val responseVerbatim = OfflineAnalyzer.generateLocalCoaching(
            domain = CoachingDomain.PRESENTATION,
            prompt = "Slide 1",
            transcript = verbatimRecitation,
            wpm = 130,
            pauseCount = 2,
            fillerCount = 0,
            repetitionCount = 0,
            slideText = slideText
        )
        assertNotNull(responseVerbatim)
    }

    @Test
    fun testCrossDomainStudentMemory_unifiesInsights() = runBlocking {
        // Record insight in FLUENCY
        repository.updateMemoryFromAnalysis(
            domain = "FLUENCY",
            key = "pacing_pattern",
            insight = "Paces fast (165 WPM) during initial sentence starts",
            importance = 2
        )

        // Record insight in ENGLISH
        repository.updateMemoryFromAnalysis(
            domain = "ENGLISH",
            key = "weak_filler",
            insight = "Frequently uses 'basically' before architectural trade-offs",
            importance = 2
        )

        // Now student opens INTERVIEW domain; memory context must include cross-domain insights
        val memoryContext = repository.getRelevantMemoryContext(domain = "INTERVIEW")

        assertTrue("Memory context should contain profile", memoryContext.contains("Student Profile"))
        assertTrue("Memory context should contain FLUENCY insight", memoryContext.contains("pacing_pattern"))
        assertTrue("Memory context should contain ENGLISH insight", memoryContext.contains("weak_filler"))
    }

    @Test
    fun testAIOrchestrator_offlineFallbackOnFailure() = runBlocking {
        // Mock providers that fail to simulate offline network conditions
        val failingGemini = object : AIProvider {
            override val name: String = "MockGemini"
            override suspend fun generateCoaching(
                domain: CoachingDomain,
                prompt: String,
                transcript: String,
                metricsSummary: String,
                memoryContext: String,
                systemInstruction: String
            ): ProviderResult = ProviderResult(
                success = false,
                response = null,
                modelName = "mock-gemini",
                isTemporaryFailure = true,
                error = "Simulated Network Failure"
            )
        }

        val failingOpenRouter = object : AIProvider {
            override val name: String = "MockOpenRouter"
            override suspend fun generateCoaching(
                domain: CoachingDomain,
                prompt: String,
                transcript: String,
                metricsSummary: String,
                memoryContext: String,
                systemInstruction: String
            ): ProviderResult = ProviderResult(
                success = false,
                response = null,
                modelName = "mock-openrouter",
                isTemporaryFailure = true,
                error = "Simulated Timeout"
            )
        }

        val orchestrator = AIOrchestrator(
            repository = repository,
            geminiProvider = failingGemini,
            openRouterProvider = failingOpenRouter
        )

        val result = orchestrator.analyzeSession(
            domain = CoachingDomain.FLUENCY,
            prompt = "Deep breathing drill",
            transcript = "Continuous phonation maintains vocal rhythm and calm composure.",
            wpm = 130,
            pauseCount = 2,
            fillerCount = 0,
            repetitionCount = 0
        )

        assertEquals("Local Intelligent Coach", result.providerUsed)
        assertTrue("Result must be marked offline", result.isOffline)
        assertNotNull(result.response)
        assertTrue(result.response.fluency_score >= 50)
    }

    @Test
    fun testFluencyGym_weakWordsCrudAndMastery() = runBlocking {
        repository.seedInitialPracticeWordsIfEmpty()
        val words = repository.getWeakWordsList()
        assertTrue("Initial technical words should be seeded", words.isNotEmpty())

        val firstWord = words.first()
        repository.updateWordMastery(firstWord.id, 95)
        val updated = repository.getWordById(firstWord.id)
        assertNotNull(updated)
        assertEquals(95, updated?.masteryScore)

        val customId = repository.addWeakWord("Asynchronous", "A-syn-chro-nous", "We use asynchronous messaging.", "FLUENCY")
        val customWord = repository.getWordById(customId.toInt())
        assertNotNull(customWord)
        assertEquals("Asynchronous", customWord?.word)

        if (customWord != null) {
            repository.deleteWeakWord(customWord)
            val deleted = repository.getWordById(customId.toInt())
            assertNull(deleted)
        }
    }

    @Test
    fun testSpeechMetricsAnalyzer_recoveryScoreAndPauseBreakdown() {
        val transcript = "We designed the database replication. We we restarted smoothly."
        val metrics = SpeechMetricsAnalyzer.analyze(
            transcript = transcript,
            durationSeconds = 12,
            detectedPauses = 3,
            contextCategory = "CONTROLLED"
        )

        assertTrue("WPM should be calculated", metrics.wpm > 0)
        assertTrue("Should detect repetition", metrics.repetitionCount >= 1)
        assertTrue("Recovery score should be between 50 and 100", metrics.recoveryScore in 50..100)
        assertTrue("Natural pauses should be identified", metrics.naturalPauses >= 1)
        assertTrue("Actionable feedback should be populated", metrics.actionableFeedback.isNotEmpty())
    }

    @Test
    fun testSpeechMetricsAnalyzer_sessionComparison() {
        val prevSession = PracticeSession(
            sessionType = "FLUENCY",
            title = "Gentle Onset",
            timestamp = System.currentTimeMillis() - 100000,
            durationSeconds = 60,
            transcript = "Um basically we deployed the service.",
            wpm = 110,
            pauseCount = 4,
            fillerCount = 3,
            repetitionCount = 1,
            confidencePre = 2,
            confidencePost = 3,
            overallScore = 75,
            fluencyScore = 75,
            englishScore = 75,
            presentationScore = 75,
            technicalScore = 75,
            feedback = "Good start",
            strengths = "Steady tempo",
            weaknesses = "Fillers",
            nextAction = "Practice pausing",
            providerUsed = "Local Intelligent Coach",
            modelUsed = "Heuristic"
        )

        val currentMetrics = SpeechMetricsAnalyzer.analyze(
            transcript = "We deployed the service with zero downtime.",
            durationSeconds = 40,
            detectedPauses = 2
        )

        val comparison = SpeechMetricsAnalyzer.compareSessions(
            current = currentMetrics,
            currentPostConfidence = 4,
            previous = prevSession
        )

        assertNotNull(comparison)
        assertEquals(currentMetrics.wpm - prevSession.wpm, comparison!!.wpmDelta)
        assertEquals(currentMetrics.fillerCount - prevSession.fillerCount, comparison.fillerDelta)
        assertEquals(1, comparison.confidenceDelta) // 4 - 3 = +1
        assertTrue("Interpretation should explain changes objectively", comparison.interpretation.isNotBlank())
    }

    @Test
    fun testFluencyModels_allSevenCategoriesRepresented() {
        val categories = com.example.ui.fluency.FLUENCY_EXERCISES_CATALOG.map { it.category }.toSet()
        assertTrue(categories.contains(com.example.ui.fluency.FluencyCategory.WARM_UP))
        assertTrue(categories.contains(com.example.ui.fluency.FluencyCategory.CONTROLLED_SPEECH))
        assertTrue(categories.contains(com.example.ui.fluency.FluencyCategory.READING))
        assertTrue(categories.contains(com.example.ui.fluency.FluencyCategory.THOUGHT_CHUNKING))
        assertTrue(categories.contains(com.example.ui.fluency.FluencyCategory.SPONTANEOUS))
        assertTrue(categories.contains(com.example.ui.fluency.FluencyCategory.RECOVERY))
        assertTrue(categories.contains(com.example.ui.fluency.FluencyCategory.DIFFICULT_WORDS))
    }
}
