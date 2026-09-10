package com.example.ui.interview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AIOrchestrator
import com.example.ai.AIResult
import com.example.ai.CoachingDomain
import com.example.data.InterviewRecord
import com.example.data.PracticeSession
import com.example.data.StudentRepository
import com.example.speech.SpeechMetricsAnalyzer
import com.example.speech.SpeechRecognitionHelper
import com.example.speech.TextToSpeechHelper
import com.example.ui.common.AudioWaveformVisualizer
import com.example.ui.common.MetricBadge
import com.example.ui.common.SessionReportDialog
import com.example.ui.theme.OratorDesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class InterviewConfig(
    val category: String, // HR, TECHNICAL, DSA, CORE_CS, PROJECT, BEHAVIORAL, SYSTEM_DESIGN, RESUME, JOB_DESCRIPTION
    val company: String = "Amazon",
    val role: String = "Software Development Engineer (SDE)",
    val round: String = "Technical Round 1",
    val difficulty: String = "Medium",
    val customContext: String = "" // Resume text or JD text
)

val INTERVIEW_CATEGORIES = listOf(
    "Technical Round" to "Algorithms, data structures, and system concepts",
    "Core CS" to "Operating Systems, DBMS, Networks, and OOP",
    "System Design" to "Scalability, caching, load balancers, and databases",
    "Behavioral (STAR)" to "Conflict resolution, leadership, and teamwork",
    "HR Round" to "Career motivation, strengths, and cultural fit",
    "Project Deep-Dive" to "Architectural defense of your college projects",
    "Resume-Based" to "Questions extracted directly from your resume",
    "Job Description" to "Tailored drills based on specific company JD"
)

val COMPANIES = listOf("Amazon", "Google", "Microsoft", "TCS", "Infosys", "Fast-growth Startup")
val ROLES = listOf("Software Development Engineer (SDE)", "Frontend Engineer", "Backend / Cloud Engineer", "Data & AI Engineer")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewScreen(
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    modifier: Modifier = Modifier
) {
    var activeConfig by remember { mutableStateOf<InterviewConfig?>(null) }
    var selectedCategory by remember { mutableStateOf("Technical Round") }
    var selectedCompany by remember { mutableStateOf("Amazon") }
    var selectedRole by remember { mutableStateOf("Software Development Engineer (SDE)") }
    var selectedDifficulty by remember { mutableStateOf("Medium") }
    var resumeOrJdText by remember { mutableStateOf("") }
    var showConfigModal by remember { mutableStateOf(false) }

    if (activeConfig != null) {
        ActiveMockInterviewView(
            config = activeConfig!!,
            repository = repository,
            orchestrator = orchestrator,
            onClose = { activeConfig = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Mock Interview Engine", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Company, Role & Resume-based voice interviews", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            modifier = modifier
        ) { padding ->
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Quick Launch Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Adaptive AI Interviewer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "The AI asks conversational technical and behavioral questions, listens to your answers, and dynamically generates follow-ups based on your explanation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Company and Role Selector
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Target Company & Role Practice", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val nextIdx = (COMPANIES.indexOf(selectedCompany) + 1) % COMPANIES.size
                                        selectedCompany = COMPANIES[nextIdx]
                                    },
                                    shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🏢 $selectedCompany", fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        selectedDifficulty = when (selectedDifficulty) {
                                            "Easy" -> "Medium"
                                            "Medium" -> "Hard"
                                            else -> "Easy"
                                        }
                                    },
                                    shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("⚡ $selectedDifficulty", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    val nextIdx = (ROLES.indexOf(selectedRole) + 1) % ROLES.size
                                    selectedRole = ROLES[nextIdx]
                                },
                                shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🎯 $selectedRole", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                item {
                    Text("Select Interview Round", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                items(INTERVIEW_CATEGORIES) { (cat, desc) ->
                    Card(
                        onClick = {
                            selectedCategory = cat
                            if (cat.contains("Resume") || cat.contains("Job Description")) {
                                showConfigModal = true
                            } else {
                                activeConfig = InterviewConfig(
                                    category = cat,
                                    company = selectedCompany,
                                    role = selectedRole,
                                    round = cat,
                                    difficulty = selectedDifficulty
                                )
                            }
                        },
                        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("interview_cat_$cat")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (cat.contains("Resume")) Icons.Default.Description else if (cat.contains("Technical")) Icons.Default.Code else Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // Modal for Resume / Job Description Text input
    if (showConfigModal) {
        AlertDialog(
            onDismissRequest = { showConfigModal = false },
            title = { Text("Paste $selectedCategory Details") },
            text = {
                Column {
                    Text(
                        text = if (selectedCategory.contains("Resume")) "Paste your resume summary, tech stack, and key project details:" else "Paste the target Job Description (JD):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resumeOrJdText,
                        onValueChange = { resumeOrJdText = it },
                        placeholder = {
                            Text(
                                if (selectedCategory.contains("Resume"))
                                    "e.g. Skills: Kotlin, Python, Docker. Project: Real-time distributed chat system using WebSockets and Redis..."
                                else
                                    "e.g. Seeking SDE with strong CS fundamentals, distributed systems, REST APIs, and PostgreSQL..."
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeConfig = InterviewConfig(
                            category = selectedCategory,
                            company = selectedCompany,
                            role = selectedRole,
                            round = selectedCategory,
                            difficulty = selectedDifficulty,
                            customContext = resumeOrJdText.ifBlank { "Full stack software engineer with focus on scalable web architecture." }
                        )
                        showConfigModal = false
                    }
                ) {
                    Text("Start Interview")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfigModal = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveMockInterviewView(
    config: InterviewConfig,
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechHelper = remember { SpeechRecognitionHelper(context) }
    val tts = remember { TextToSpeechHelper(context) }

    val isListening by speechHelper.isListening.collectAsState()
    val transcript by speechHelper.transcript.collectAsState()
    val rmsDb by speechHelper.rmsDb.collectAsState()

    var questionNumber by remember { mutableStateOf(1) }
    var currentQuestion by remember {
        mutableStateOf(
            generateInitialQuestion(config)
        )
    }

    var secondsElapsed by remember { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var isEvaluating by remember { mutableStateOf(false) }
    var sessionReport by remember { mutableStateOf<AIResult?>(null) }
    var followUpPrompt by remember { mutableStateOf<String?>(null) }

    // TTS auto-speak question
    LaunchedEffect(currentQuestion) {
        tts.speak(currentQuestion)
    }

    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) {
            delay(1000)
            secondsElapsed++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechHelper.stopListening()
            tts.shutdown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${config.company} • ${config.category}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Question $questionNumber of 3 • ${config.difficulty}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Exit")
                    }
                },
                actions = {
                    IconButton(onClick = { tts.speak(currentQuestion) }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Read Question")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Interviewer Question Card
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${config.company} • ${config.round}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = config.role,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = config.difficulty,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "\"$currentQuestion\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (config.category.contains("Behavioral") || config.category.contains("HR"))
                                        "STAR Guide: Situation → Task → Action taken → Measurable Result"
                                    else
                                        "Structure Guide: State direct answer → Explain trade-offs → Share concrete example",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Candidate Spoken Answer Card
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Your Spoken Answer:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (transcript.isBlank()) "Tap the microphone below and answer clearly. The AI will evaluate both your technical content and communication clarity..." else transcript,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Audio & Next Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AudioWaveformVisualizer(isListening = isListening, rmsDb = rmsDb)

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = {
                        speechHelper.reset()
                        secondsElapsed = 0
                        isTimerRunning = false
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }

                    FloatingActionButton(
                        onClick = {
                            if (isListening) {
                                speechHelper.stopListening()
                                isTimerRunning = false
                            } else {
                                speechHelper.startListening()
                                isTimerRunning = true
                            }
                        },
                        containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(68.dp)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Mic",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Button(
                        onClick = {
                            speechHelper.stopListening()
                            isTimerRunning = false
                            isEvaluating = true

                            scope.launch {
                                val metrics = SpeechMetricsAnalyzer.analyze(transcript, secondsElapsed)
                                val result = orchestrator.analyzeSession(
                                    domain = CoachingDomain.INTERVIEW,
                                    prompt = "Mock Interview: ${config.company} ${config.role} (${config.category}). Question: $currentQuestion. Context: ${config.customContext}",
                                    transcript = transcript,
                                    wpm = metrics.wpm,
                                    pauseCount = metrics.pauseCount,
                                    fillerCount = metrics.fillerCount,
                                    repetitionCount = metrics.repetitionCount,
                                    queryTopic = config.company
                                )

                                val record = PracticeSession(
                                    sessionType = "INTERVIEW",
                                    title = "${config.company} - ${config.category} (Q$questionNumber)",
                                    durationSeconds = secondsElapsed.coerceAtLeast(10),
                                    transcript = transcript,
                                    wpm = metrics.wpm,
                                    pauseCount = metrics.pauseCount,
                                    fillerCount = metrics.fillerCount,
                                    overallScore = result.response.technical_score,
                                    fluencyScore = result.response.fluency_score,
                                    englishScore = result.response.english_score,
                                    presentationScore = result.response.presentation_score,
                                    technicalScore = result.response.technical_score,
                                    feedback = result.response.feedback,
                                    strengths = result.response.strengths.joinToString(", "),
                                    weaknesses = result.response.weaknesses.joinToString(", "),
                                    nextAction = result.response.next_action,
                                    providerUsed = result.providerUsed,
                                    modelUsed = result.modelUsed
                                )
                                repository.saveSession(record)
                                repository.recordInterview(
                                    InterviewRecord(
                                        category = config.category,
                                        company = config.company,
                                        role = config.role,
                                        round = config.round,
                                        difficulty = config.difficulty,
                                        questionCount = questionNumber,
                                        averageScore = result.response.technical_score
                                    )
                                )

                                isEvaluating = false
                                // If follow-up exists and question < 3, offer follow up
                                if (result.response.follow_up_question != null && questionNumber < 3) {
                                    followUpPrompt = result.response.follow_up_question
                                }
                                sessionReport = result
                            }
                        },
                        enabled = transcript.isNotBlank()
                    ) {
                        Text(if (questionNumber < 3) "Evaluate" else "Complete")
                    }
                }
            }
        }
    }

    if (isEvaluating) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Interviewer Evaluating Answer...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analyzing technical correctness, communication structure, and reasoning...")
                }
            },
            confirmButton = {}
        )
    }

    sessionReport?.let { report ->
        val metrics = SpeechMetricsAnalyzer.analyze(transcript, secondsElapsed)
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Interview Feedback (Q$questionNumber)") },
            text = {
                Column {
                    Text("Technical Score: ${report.response.technical_score}/100", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(report.response.feedback, style = MaterialTheme.typography.bodyMedium)
                    if (report.response.weaknesses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Area to Improve: ${report.response.weaknesses.first()}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFF59E0B))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sessionReport = null
                        if (followUpPrompt != null && questionNumber < 3) {
                            currentQuestion = followUpPrompt!!
                            questionNumber++
                            speechHelper.reset()
                            secondsElapsed = 0
                            followUpPrompt = null
                        } else {
                            onClose()
                        }
                    }
                ) {
                    Text(if (followUpPrompt != null && questionNumber < 3) "Next Follow-up Question" else "Done")
                }
            }
        )
    }
}

private fun generateInitialQuestion(config: InterviewConfig): String {
    return when (config.category) {
        "Technical Round" -> "Explain how HashMap handles hash collisions in memory. What are the time complexities for lookup and insertion?"
        "Core CS" -> "What is the difference between a process and a thread? How does the operating system handle context switching between them?"
        "System Design" -> "How would you design a URL shortener like TinyURL that handles 100 million write requests per day?"
        "Behavioral (STAR)" -> "Tell me about a time you had a technical disagreement with a teammate. How did you handle it and what was the outcome?"
        "HR Round" -> "Why are you interested in joining ${config.company} as a ${config.role}? What drives your engineering passions?"
        "Project Deep-Dive" -> "Walk me through the architecture of your most challenging technical project. What was the toughest technical bottleneck you solved?"
        "Resume-Based" -> "Based on your background: How did you select the tech stack for your primary project, and how would you optimize it for 10x traffic?"
        "Job Description" -> "For this ${config.role} position: How do you ensure high reliability and minimal downtime when deploying backend services?"
        else -> "Can you introduce yourself and highlight two core engineering accomplishments?"
    }
}
