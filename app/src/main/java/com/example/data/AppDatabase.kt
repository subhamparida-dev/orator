package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        StudentProfile::class,
        PracticeSession::class,
        WeakWord::class,
        Presentation::class,
        InterviewRecord::class,
        MemoryFact::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentProfileDao(): StudentProfileDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun weakWordDao(): WeakWordDao
    abstract fun presentationDao(): PresentationDao
    abstract fun interviewRecordDao(): InterviewRecordDao
    abstract fun memoryFactDao(): MemoryFactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "orator_database.db"
                )
                .fallbackToDestructiveMigration()
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            prepopulateData(database)
                        }
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun prepopulateData(db: AppDatabase) {
            // Prepopulate initial student profile
            db.studentProfileDao().saveProfile(
                StudentProfile(
                    id = 1,
                    name = "Alex Rivera",
                    college = "College of Computer Engineering",
                    major = "Computer Science & Engineering",
                    targetRole = "Software Development Engineer (SDE)",
                    preferredCorrectionStyle = "Gentle & Constructive",
                    englishProficiency = "Intermediate",
                    currentStreak = 4,
                    totalSpeakingMinutes = 48
                )
            )

            // Prepopulate weak/practice words
            val initialWords = listOf(
                WeakWord(word = "Architecture", phonetic = "/ˈɑːr.kə.tek.tʃɚ/", category = "FLUENCY", contextSentence = "The microservices architecture ensures high availability.", encounterCount = 3, masteryScore = 55),
                WeakWord(word = "Implementation", phonetic = "/ˌɪm.plə.menˈteɪ.ʃən/", category = "FLUENCY", contextSentence = "Our implementation utilizes asynchronous event listeners.", encounterCount = 4, masteryScore = 40),
                WeakWord(word = "Synchronous", phonetic = "/ˈsɪŋ.krə.nəs/", category = "PRONUNCIATION", contextSentence = "Synchronous calls can create bottlenecks under peak loads.", encounterCount = 2, masteryScore = 60),
                WeakWord(word = "Consensus", phonetic = "/kənˈsen.səs/", category = "VOCABULARY", contextSentence = "The Raft algorithm guarantees distributed consensus.", encounterCount = 2, masteryScore = 70),
                WeakWord(word = "Prerequisite", phonetic = "/priːˈrek.wə.zɪt/", category = "PRONUNCIATION", contextSentence = "Data normalization is a prerequisite for clean schema design.", encounterCount = 3, masteryScore = 35)
            )
            initialWords.forEach { db.weakWordDao().insertOrUpdate(it) }

            // Prepopulate initial sample presentations
            val samplePresentations = listOf(
                Presentation(
                    title = "Cloud Microservices & Resilience",
                    topic = "Distributed Systems Engineering",
                    targetAudience = "Senior Faculty & Technical Evaluation Panel",
                    totalSlides = 4,
                    slidesJson = """
                        [
                          {
                            "slideNumber": 1,
                            "title": "Introduction to Microservices Architecture",
                            "keyIdea": "Explain why modern distributed systems decouple monolithic services into independent domain components.",
                            "points": ["Monolith vs Microservices tradeoffs", "Independent scalability and deployment cycles", "Domain-driven design boundaries"],
                            "transition": "Now let us delve into how individual services talk to one another reliably.",
                            "difficultWords": ["Decoupling", "Microservices", "Scalability"],
                            "teacherQuestion": "Why would a small team avoid microservices initially?"
                          },
                          {
                            "slideNumber": 2,
                            "title": "Inter-Service Communication & API Gateways",
                            "keyIdea": "Differentiate between synchronous REST/gRPC and asynchronous event-driven messaging.",
                            "points": ["Reverse proxy and rate limiting at gateway", "gRPC for low-latency internal RPC", "Message queues for fault buffering"],
                            "transition": "Having established messaging pathways, fault containment becomes our next priority.",
                            "difficultWords": ["Synchronous", "Asynchronous", "Throughput"],
                            "teacherQuestion": "What happens when the message broker queue fills up?"
                          },
                          {
                            "slideNumber": 3,
                            "title": "Fault Tolerance: Circuit Breaker Pattern",
                            "keyIdea": "Prevent cascading system failures by tripping open when downstream endpoints fail.",
                            "points": ["Three states: Closed, Open, Half-Open", "Graceful degradation with cached fallbacks", "Monitoring error thresholds in real-time"],
                            "transition": "Finally, let's examine the empirical results from our stress tests.",
                            "difficultWords": ["Cascading", "Resilience", "Degradation"],
                            "teacherQuestion": "How do you decide the timeout threshold before tripping the breaker?"
                          },
                          {
                            "slideNumber": 4,
                            "title": "Evaluation, Benchmarks & Future Scope",
                            "keyIdea": "Synthesize the 99th percentile latency metrics and propose future work in auto-remediation.",
                            "points": ["p99 latency dropped by 42% under simulated chaos", "Kubernetes cluster auto-scaling response time", "Next steps: Observability and distributed tracing"],
                            "transition": "Thank you for your time. I welcome questions and feedback.",
                            "difficultWords": ["Observability", "Benchmarks", "Remediation"],
                            "teacherQuestion": "What was the biggest bottleneck observed during the chaos engineering tests?"
                          }
                        ]
                    """.trimIndent()
                ),
                Presentation(
                    title = "AI in Diagnostic Medicine: Opportunities & Ethics",
                    topic = "Biomedical Informatics & Ethics",
                    targetAudience = "Interdisciplinary Review Board",
                    totalSlides = 3,
                    slidesJson = """
                        [
                          {
                            "slideNumber": 1,
                            "title": "Problem Statement & Clinical Motivation",
                            "keyIdea": "High diagnostic workload in medical imaging leads to radiologist burnout and diagnostic delay.",
                            "points": ["Global radiologist shortages", "Time-critical detection of early abnormalities", "Computer vision as an assistive co-pilot"],
                            "transition": "Let us explore the deep learning model architecture deployed for classification.",
                            "difficultWords": ["Abnormalities", "Radiologist", "Diagnostic"],
                            "teacherQuestion": "Is the AI meant to replace radiologists or augment them?"
                          },
                          {
                            "slideNumber": 2,
                            "title": "Model Architecture & Validation Pipeline",
                            "keyIdea": "Convolutional vision backbones with attention maps highlight anatomical regions of interest.",
                            "points": ["Transfer learning on curated MRI datasets", "Grad-CAM explainability heatmaps for clinical validation", "Sensitivity vs specificity trade-offs"],
                            "transition": "Before real-world deployment, ethical and regulatory safeguards must be addressed.",
                            "difficultWords": ["Explainability", "Specificity", "Convolutional"],
                            "teacherQuestion": "How do you prevent dataset bias from demographic skews?"
                          },
                          {
                            "slideNumber": 3,
                            "title": "Ethical Implications & Safety Protocols",
                            "keyIdea": "Ensuring patient privacy, clinician oversight, and clear liability boundaries.",
                            "points": ["HIPAA & GDPR data anonymization", "Human-in-the-loop mandatory review", "Conclusion: AI as diagnostic accelerator"],
                            "transition": "I am open to your questions.",
                            "difficultWords": ["Anonymization", "Oversight", "Implications"],
                            "teacherQuestion": "Who bears legal responsibility if the AI suggests an incorrect diagnosis?"
                          }
                        ]
                    """.trimIndent()
                )
            )
            samplePresentations.forEach { db.presentationDao().insertPresentation(it) }

            // Prepopulate initial memory facts
            val initialFacts = listOf(
                MemoryFact(domain = "SPEAKING", factKey = "pacing_trend", factValue = "Pace tends to accelerate from 130 WPM to 165 WPM when discussing technical architectures."),
                MemoryFact(domain = "SPEAKING", factKey = "filler_pattern", factValue = "Uses 'basically' and 'like' primarily during sentence transitions."),
                MemoryFact(domain = "ENGLISH", factKey = "grammar_pattern", factValue = "Occasional subject-verb disagreement with compound subjects; prefers gentle corrections."),
                MemoryFact(domain = "PRESENTATION", factKey = "slide_style", factValue = "Comfortable with bullet points; needs gentle cueing to avoid verbatim reading of slide bullets."),
                MemoryFact(domain = "INTERVIEW", factKey = "weak_topic", factValue = "Database indexing and B+ Tree concurrency need deeper practice; strong in OOP and system design.")
            )
            initialFacts.forEach { db.memoryFactDao().insertFact(it) }

            // Prepopulate a sample practice session to provide immediate realistic progress
            db.practiceSessionDao().insertSession(
                PracticeSession(
                    sessionType = "FLUENCY",
                    title = "Controlled Pauses & Thought Groups",
                    timestamp = System.currentTimeMillis() - 86400000L * 2,
                    durationSeconds = 180,
                    transcript = "In our project we designed a distributed cache. When a request arrives, the server checks the memory first before hitting the database.",
                    wpm = 138,
                    pauseCount = 6,
                    fillerCount = 3,
                    repetitionCount = 1,
                    confidencePre = 3,
                    confidencePost = 4,
                    overallScore = 82,
                    fluencyScore = 85,
                    englishScore = 80,
                    presentationScore = 78,
                    technicalScore = 84,
                    feedback = "Excellent deliberate pauses after key technical terms. Smooth recovery when pausing before 'distributed cache'.",
                    strengths = "Well-regulated speaking tempo, confident pauses, natural sentence stress",
                    weaknesses = "Minor restart before introducing the cache layer",
                    nextAction = "Practice gentle-onset speaking on technical terms starting with plosives",
                    providerUsed = "Gemini",
                    modelUsed = "gemini-3.5-flash"
                )
            )
        }
    }
}
