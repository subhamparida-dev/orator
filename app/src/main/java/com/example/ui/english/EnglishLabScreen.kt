package com.example.ui.english

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.PracticeSession
import com.example.data.StudentRepository
import com.example.data.WeakWord
import com.example.speech.SpeechMetricsAnalyzer
import com.example.speech.SpeechRecognitionHelper
import com.example.speech.TextToSpeechHelper
import com.example.ui.common.AudioWaveformVisualizer
import com.example.ui.common.MetricBadge
import com.example.ui.common.SessionReportDialog
import com.example.ui.theme.OratorDesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class EnglishDrill(
    val id: String,
    val title: String,
    val level: String, // Beginner, Intermediate, Advanced
    val promptQuestion: String,
    val sampleAnswerGuide: String,
    val focusArea: String
)

val ENGLISH_DRILLS = listOf(
    EnglishDrill(
        id = "eng_1",
        title = "Daily Spoken English: Introducing an Idea",
        level = "Intermediate",
        promptQuestion = "Introduce a software tool or programming framework you learned recently. Why is it useful?",
        sampleAnswerGuide = "Start with: 'Recently, I've been exploring...' Avoid filler words like 'basically'.",
        focusArea = "Natural conversational opening & clear purpose"
    ),
    EnglishDrill(
        id = "eng_2",
        title = "Grammar & Phrasing: Explaining a Trade-off",
        level = "Advanced",
        promptQuestion = "Explain the difference between synchronous and asynchronous code. When would you prefer one over the other?",
        sampleAnswerGuide = "Use contrast words: 'On the one hand... conversely... whereas...'",
        focusArea = "Complex sentence coordination & technical precision"
    ),
    EnglishDrill(
        id = "eng_3",
        title = "Sentence Refinement: The STAR Response",
        level = "Intermediate",
        promptQuestion = "Describe a time when a group project hit a deadline bottleneck. What did you do to help resolve it?",
        sampleAnswerGuide = "Situation -> Task -> Action -> Result in 3-4 structured sentences.",
        focusArea = "Concise narrative flow without run-on sentences"
    ),
    EnglishDrill(
        id = "eng_4",
        title = "Vocabulary Booster: Professional Tech Verbs",
        level = "Beginner",
        promptQuestion = "Describe your daily study routine using active verbs: 'orchestrate', 'consolidate', 'implement', 'debug'.",
        sampleAnswerGuide = "Focus on dynamic action verbs instead of passive phrases.",
        focusArea = "Expanding active professional vocabulary"
    ),
    EnglishDrill(
        id = "eng_5",
        title = "Pronunciation & Weak Words Lab",
        level = "Intermediate",
        promptQuestion = "Read aloud with deliberate syllable stress: 'Our microservices architecture ensures high availability through continuous synchronization.'",
        sampleAnswerGuide = "Pronounce every syllable cleanly; pause slightly at commas.",
        focusArea = "Multi-syllable word clarity & phonation"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishLabScreen(
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    modifier: Modifier = Modifier
) {
    val weakWords by repository.weakWords.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var selectedDrill by remember { mutableStateOf<EnglishDrill?>(null) }
    var selectedWordForPractice by remember { mutableStateOf<WeakWord?>(null) }
    var newWordInput by remember { mutableStateOf("") }
    var showAddWordDialog by remember { mutableStateOf(false) }

    if (selectedDrill != null) {
        ActiveEnglishDrillView(
            drill = selectedDrill!!,
            repository = repository,
            orchestrator = orchestrator,
            onClose = { selectedDrill = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("English Fluency Lab", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Professional spoken English & natural phrasing coach", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                // Section: My Practice Words (Phase 20)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
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
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Spellcheck,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("My Practice Words", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                TextButton(
                                    onClick = { showAddWordDialog = true },
                                    shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Word", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Personalized pronunciation & vocabulary deck. Practice tricky technical terms with repeat phonation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(weakWords) { word ->
                                    Surface(
                                        shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable { selectedWordForPractice = word }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.VolumeUp,
                                                contentDescription = "Listen",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(word.word, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                                                if (word.phonetic.isNotBlank()) {
                                                    Text(word.phonetic, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section: Interactive Drills
                item {
                    Text(
                        text = "Speaking & Sentence Improvement Drills",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                items(ENGLISH_DRILLS) { drill ->
                    Card(
                        onClick = { selectedDrill = drill },
                        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("english_drill_${drill.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = drill.level,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = drill.focusArea,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = drill.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = drill.promptQuestion,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { selectedDrill = drill },
                                    shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Practice", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Add Custom Practice Word
    if (showAddWordDialog) {
        AlertDialog(
            onDismissRequest = { showAddWordDialog = false },
            title = { Text("Add Word to Practice Deck") },
            text = {
                OutlinedTextField(
                    value = newWordInput,
                    onValueChange = { newWordInput = it },
                    label = { Text("Word or Technical Term") },
                    placeholder = { Text("e.g. Asynchronous, Polymorphism") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWordInput.isNotBlank()) {
                            scope.launch {
                                repository.addWeakWord(newWordInput)
                                newWordInput = ""
                                showAddWordDialog = false
                            }
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWordDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Word Practice Modal
    selectedWordForPractice?.let { word ->
        WordPracticeDialog(
            word = word,
            onDismiss = { selectedWordForPractice = null }
        )
    }
}

@Composable
fun WordPracticeDialog(word: WeakWord, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tts = remember { TextToSpeechHelper(context) }
    var practiced by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(word.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { tts.speak(word.word) }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Pronounce", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        text = {
            Column {
                if (word.phonetic.isNotBlank()) {
                    Text("Phonetic: ${word.phonetic}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (word.contextSentence.isNotBlank()) {
                    Text("Sample Sentence:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("\"${word.contextSentence}\"", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Repetition Drill:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Repeat 3 times slowly with easy onset. Syllables: ${word.word.split("(?<=[aeiouy])".toRegex()).joinToString("-")}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                tts.speak(word.word)
                practiced = true
                onDismiss()
            }) {
                Text("Listen & Mastered")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveEnglishDrillView(
    drill: EnglishDrill,
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

    var secondsElapsed by remember { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var sessionReport by remember { mutableStateOf<AIResult?>(null) }
    var typedInput by remember { mutableStateOf("") }
    var isTypingMode by remember { mutableStateOf(false) }

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
                title = { Text(drill.title, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { tts.speak(drill.promptQuestion) }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Listen to question")
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
                // Prompt Card
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "AI Coach Prompt",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = drill.promptQuestion,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Guide: ${drill.sampleAnswerGuide}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transcript Card
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Spoken Response",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { isTypingMode = !isTypingMode }) {
                                Text(if (isTypingMode) "Use Voice" else "Type / Edit")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isTypingMode) {
                            OutlinedTextField(
                                value = typedInput,
                                onValueChange = { typedInput = it },
                                placeholder = { Text("Type your spoken response here...") },
                                shape = RoundedCornerShape(OratorDesignTokens.InputCornerRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        speechHelper.appendManualText(typedInput)
                                        typedInput = ""
                                    }) {
                                        Icon(Icons.Default.Send, contentDescription = "Add")
                                    }
                                }
                            )
                        } else {
                            Text(
                                text = if (transcript.isBlank()) "Tap the microphone and speak your answer naturally. The AI will evaluate your grammar, vocabulary, and phrasing..." else transcript,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Audio Controls
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
                            isAnalyzing = true

                            scope.launch {
                                val metrics = SpeechMetricsAnalyzer.analyze(transcript, secondsElapsed)
                                val result = orchestrator.analyzeSession(
                                    domain = CoachingDomain.ENGLISH,
                                    prompt = "English Practice: ${drill.title}. Prompt: ${drill.promptQuestion}",
                                    transcript = transcript,
                                    wpm = metrics.wpm,
                                    pauseCount = metrics.pauseCount,
                                    fillerCount = metrics.fillerCount,
                                    repetitionCount = metrics.repetitionCount,
                                    queryTopic = drill.title
                                )

                                val record = PracticeSession(
                                    sessionType = "ENGLISH",
                                    title = drill.title,
                                    durationSeconds = secondsElapsed.coerceAtLeast(10),
                                    transcript = transcript,
                                    wpm = metrics.wpm,
                                    pauseCount = metrics.pauseCount,
                                    fillerCount = metrics.fillerCount,
                                    overallScore = result.response.english_score,
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

                                isAnalyzing = false
                                sessionReport = result
                            }
                        },
                        enabled = transcript.isNotBlank()
                    ) {
                        Text("Analyze")
                    }
                }
            }
        }
    }

    if (isAnalyzing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Analyzing English Phrasing...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking natural phrasing, grammatical structure, and vocabulary precision...")
                }
            },
            confirmButton = {}
        )
    }

    sessionReport?.let { report ->
        val metrics = SpeechMetricsAnalyzer.analyze(transcript, secondsElapsed)
        SessionReportDialog(
            aiResult = report,
            wpm = metrics.wpm,
            durationSeconds = secondsElapsed,
            fillerCount = metrics.fillerCount,
            pauseCount = metrics.pauseCount,
            onDismiss = {
                sessionReport = null
                onClose()
            }
        )
    }
}
