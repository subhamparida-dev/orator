package com.example.ui.fluency

enum class FluencyCategory(val displayName: String, val description: String) {
    WARM_UP("Warm-up", "Relax vocal tract muscles before speaking"),
    CONTROLLED_SPEECH("Controlled Speech", "Easy onset and gentle articulatory contact"),
    READING("Reading Aloud", "Paced prose, technical explanations, and presentation excerpts"),
    THOUGHT_CHUNKING("Thought Chunking", "Short meaningful clusters with deliberate pauses"),
    SPONTANEOUS("Spontaneous Speaking", "Timed drills without pre-written scripts"),
    RECOVERY("Recovery Practice", "Calm, unforced resumption after speech disruptions"),
    DIFFICULT_WORDS("Difficult Words", "Syllable deconstruction and recurring practice words")
}

data class FluencyExercise(
    val id: Int,
    val title: String,
    val category: FluencyCategory,
    val durationSeconds: Int,
    val difficulty: String, // Beginner, Intermediate, Advanced
    val objective: String,
    val description: String,
    val samplePrompt: String,
    val chunkedPhrases: List<String> = emptyList(),
    val coachingTip: String,
    val isBreathing: Boolean = false,
    val isWordPractice: Boolean = false
)

data class RoutineStep(
    val stepIndex: Int,
    val title: String,
    val durationMinutes: Int,
    val exerciseId: Int,
    val reason: String
)

val DEFAULT_DAILY_ROUTINE = listOf(
    RoutineStep(
        stepIndex = 1,
        title = "Breathing & Vocal Relaxation",
        durationMinutes = 2,
        exerciseId = 1,
        reason = "De-tense vocal folds and establish diaphragmatic breath control"
    ),
    RoutineStep(
        stepIndex = 2,
        title = "Easy Speech & Gentle Onset",
        durationMinutes = 3,
        exerciseId = 2,
        reason = "Initiate initial vowels and consonants with light vocal contact"
    ),
    RoutineStep(
        stepIndex = 3,
        title = "Thought Chunking Practice",
        durationMinutes = 3,
        exerciseId = 4,
        reason = "Cluster ideas into 4-6 word groups separated by calm pauses"
    ),
    RoutineStep(
        stepIndex = 4,
        title = "Reading Aloud with Rhythm",
        durationMinutes = 3,
        exerciseId = 6,
        reason = "Reinforce smooth continuous phonation with technical prose"
    ),
    RoutineStep(
        stepIndex = 5,
        title = "Spontaneous Speaking Drill",
        durationMinutes = 3,
        exerciseId = 10,
        reason = "Apply fluency strategies in unscripted impromptu delivery"
    )
)

val FLUENCY_EXERCISES_CATALOG = listOf(
    // Category A: Warm-up
    FluencyExercise(
        id = 1,
        title = "Breathing & Vocal Relaxation Warm-up",
        category = FluencyCategory.WARM_UP,
        durationSeconds = 120,
        difficulty = "Beginner",
        objective = "Relieve shoulder, neck, and laryngeal tension prior to speaking.",
        description = "Guided 4-4-4-4 box breathing pacing: Inhale calmly, hold, exhale smoothly, rest. Non-medical warm-up.",
        samplePrompt = "Follow the visual breathing guide. Inhale through your nose for 4 seconds, hold gently for 4, exhale softly for 4, and rest for 4.",
        coachingTip = "Keep your shoulders low and jaw relaxed. This promotes vocal fold flexibility.",
        isBreathing = true
    ),

    // Category B: Controlled Speech
    FluencyExercise(
        id = 2,
        title = "Easy Speech & Gentle Onset",
        category = FluencyCategory.CONTROLLED_SPEECH,
        durationSeconds = 180,
        difficulty = "Beginner",
        objective = "Practice gentle vocal fold initiation on initial vowels and continuant sounds.",
        description = "Start phrases with soft, gradual airflow rather than sudden, hard glottal stops.",
        samplePrompt = "Practice gently: 'Always open to creative architecture.' Repeat phrase with slow intentional start.",
        chunkedPhrases = listOf(
            "Always open to creative architecture.",
            "Every engineer evaluates design trade-offs.",
            "Moving smoothly through distributed systems.",
            "Harmonious flow ensures clear communication.",
            "Working together to build reliable software."
        ),
        coachingTip = "Let the air begin moving split-seconds before the voice starts. Never force."
    ),
    FluencyExercise(
        id = 3,
        title = "Light Articulatory Touch",
        category = FluencyCategory.CONTROLLED_SPEECH,
        durationSeconds = 180,
        difficulty = "Intermediate",
        objective = "Produce consonants with minimal tongue and lip tension.",
        description = "Glide lightly through plosive consonants like 'p', 'b', 't', 'd', and 'k'.",
        samplePrompt = "Read with light contact: 'Continuous delivery pipelines deploy dependable updates without disruption.'",
        chunkedPhrases = listOf(
            "Continuous delivery pipelines deploy updates.",
            "Predictable performance prevents platform bottlenecks.",
            "Database partitions guarantee balanced throughput."
        ),
        coachingTip = "Touch your lips and tongue to the roof of your mouth as lightly as a feather."
    ),

    // Category C: Reading
    FluencyExercise(
        id = 6,
        title = "Reading Aloud — Technical Explanations",
        category = FluencyCategory.READING,
        durationSeconds = 180,
        difficulty = "Intermediate",
        objective = "Maintain a steady 130-145 WPM rhythm while reading technical prose aloud.",
        description = "Showcases technical paragraphs with clear punctuation and cadence markers.",
        samplePrompt = "Read aloud: 'In modern cloud architectures, microservices communicate over lightweight asynchronous message brokers. By isolating domain boundaries, engineering teams can deploy independently without triggering cascading outages.'",
        coachingTip = "Use commas and periods as natural rest stops to replenish your breath."
    ),
    FluencyExercise(
        id = 7,
        title = "Reading Aloud — Presentation Keynote",
        category = FluencyCategory.READING,
        durationSeconds = 180,
        difficulty = "Advanced",
        objective = "Project keynote confidence, vocal variety, and crisp emphasis.",
        description = "Read a keynote speech excerpt with dynamic emphasis and deliberate pauses.",
        samplePrompt = "Read aloud: 'Today we present our next-generation observability pipeline. We achieved sub-millisecond query latency, reduced storage overhead by forty percent, and empowered engineers with real-time distributed traces.'",
        coachingTip = "Emphasize key nouns and milestone metrics with vocal inflection."
    ),

    // Category D: Thought Chunking
    FluencyExercise(
        id = 4,
        title = "Thought Chunking & Natural Pauses",
        category = FluencyCategory.THOUGHT_CHUNKING,
        durationSeconds = 180,
        difficulty = "Intermediate",
        objective = "Break long explanations into 4-6 word meaningful clusters with calm pauses.",
        description = "Eliminates breathless rush by teaching intentional thought group boundaries.",
        samplePrompt = "I want to explain our application. [Pause] First, I will explain the architecture. [Pause] Then, I will explain why we selected it.",
        chunkedPhrases = listOf(
            "I want to explain our application.",
            "First, I will explain the architecture.",
            "Then, I will explain why we selected it.",
            "Our primary database replicates across three regions.",
            "This ensures zero downtime during regional outages."
        ),
        coachingTip = "A pause is not an interruption; it is a sign of confident technical composure."
    ),
    FluencyExercise(
        id = 5,
        title = "Short Sentence Rhythm Drill",
        category = FluencyCategory.THOUGHT_CHUNKING,
        durationSeconds = 150,
        difficulty = "Beginner",
        objective = "Construct concise sentences to prevent run-on phrasing and vocal fatigue.",
        description = "Practice speaking in punchy, clear declarative statements.",
        samplePrompt = "State clearly: 'Our database replicates data. Failover happens in milliseconds. System integrity is guaranteed.'",
        chunkedPhrases = listOf(
            "Our database replicates data.",
            "Failover happens in milliseconds.",
            "System integrity is guaranteed.",
            "Latency remains consistently low."
        ),
        coachingTip = "Complete one thought, breathe quietly, and begin the next."
    ),

    // Category E: Spontaneous Speaking
    FluencyExercise(
        id = 9,
        title = "30-Second Quick Impromptu",
        category = FluencyCategory.SPONTANEOUS,
        durationSeconds = 30,
        difficulty = "Beginner",
        objective = "Practice instant speech initiation without overthinking or hesitation.",
        description = "Quick 30-second drill on relatable daily topics.",
        samplePrompt = "Topic: Introduce yourself and mention one engineering skill you are excited to master this semester.",
        coachingTip = "Speak at an easy, deliberate pace. Do not race the timer."
    ),
    FluencyExercise(
        id = 10,
        title = "60-Second Elevator Pitch",
        category = FluencyCategory.SPONTANEOUS,
        durationSeconds = 60,
        difficulty = "Intermediate",
        objective = "Deliver a structured one-minute technical summary without reading notes.",
        description = "Structure your thoughts: Problem -> Engineering Approach -> Key Result.",
        samplePrompt = "Topic: Explain your college project: What problem did it solve, what architecture did you use, and what was the outcome?",
        coachingTip = "Focus on steady momentum rather than rapid speech."
    ),
    FluencyExercise(
        id = 11,
        title = "2-Minute Technical Concept Drill",
        category = FluencyCategory.SPONTANEOUS,
        durationSeconds = 120,
        difficulty = "Intermediate",
        objective = "Explain a technical topic to a colleague for 2 minutes with steady cadence.",
        description = "Sustained unscripted fluency drill with real-time pacing feedback.",
        samplePrompt = "Topic: Explain a technology you know well (e.g., Git branching, REST vs GraphQL, or Cloud Containers) simply and clearly.",
        coachingTip = "Use transitional signposts: 'First', 'Next', 'Consequently', 'In summary'."
    ),
    FluencyExercise(
        id = 12,
        title = "5-Minute Keynote Simulation",
        category = FluencyCategory.SPONTANEOUS,
        durationSeconds = 300,
        difficulty = "Advanced",
        objective = "Master long-form speaking stamina, breath replenishment, and composure.",
        description = "Comprehensive speaking drill evaluating sustained pacing and recovery.",
        samplePrompt = "Topic: The future of artificial intelligence in software engineering: opportunities, technical trade-offs, and ethical implications.",
        coachingTip = "Take steady breaths between main sections. Stay grounded and comfortable."
    ),

    // Category F: Recovery Practice
    FluencyExercise(
        id = 8,
        title = "Calm Speech Recovery Drill",
        category = FluencyCategory.RECOVERY,
        durationSeconds = 150,
        difficulty = "Intermediate",
        objective = "Learn to pause, release tension, and restart calmly when encountering hesitation.",
        description = "Practices the 4-step recovery technique: Pause -> Take your time -> Restart calmly -> Continue.",
        samplePrompt = "Repeat with calm composure: 'Scalability, reliability, observability, and fault tolerance across asynchronous microservices.'",
        chunkedPhrases = listOf(
            "Scalability, reliability, observability, and fault tolerance.",
            "Particularly prioritizing multi-regional database replication.",
            "Asynchronous event-driven messaging decouples production bottlenecks."
        ),
        coachingTip = "Never force through a disrupted syllable. Pause silently, exhale, and resume gently."
    ),

    // Category G: Difficult-Word Practice
    FluencyExercise(
        id = 13,
        title = "My Practice Words & Syllable Deconstruction",
        category = FluencyCategory.DIFFICULT_WORDS,
        durationSeconds = 120,
        difficulty = "Intermediate",
        objective = "Deconstruct multisyllabic technical terms into rhythmic, stress-accurate syllables.",
        description = "Interactive word manager with pronunciation breakdowns, sample sentences, and retry drills.",
        samplePrompt = "Select from 'My Practice Words' to deconstruct syllables and practice in technical context.",
        coachingTip = "Elongate the stressed vowel and glide into the remaining syllables.",
        isWordPractice = true
    )
)

val SPONTANEOUS_PROMPT_LIBRARY = listOf(
    "Introduce yourself, your college major, and your career aspirations.",
    "Explain your college project: What problem did it solve and what was your technical role?",
    "Explain a technology you know well (such as Git, Docker, or Databases) to a junior colleague.",
    "Describe your daily routine and how you maintain focus when debugging complex code.",
    "Explain a difficult engineering concept simply, as if explaining it to a high school student.",
    "Describe a challenging bug you diagnosed and what steps you took to resolve it.",
    "Why is clean code architecture important in long-term software projects?",
    "Explain how the Internet works from the moment a user types a URL into a browser."
)
