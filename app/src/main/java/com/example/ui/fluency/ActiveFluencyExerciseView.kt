package com.example.ui.fluency

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.core.content.ContextCompat
import com.example.ai.AIOrchestrator
import com.example.ai.AIResult
import com.example.ai.CoachingDomain
import com.example.data.PracticeSession
import com.example.data.StudentRepository
import com.example.speech.LiveSpeechMetrics
import com.example.speech.SessionComparison
import com.example.speech.SpeechMetricsAnalyzer
import com.example.speech.SpeechRecognitionHelper
import com.example.ui.common.AudioWaveformVisualizer
import com.example.ui.common.ConfidenceRatingDialog
import com.example.ui.common.MetricBadge
import com.example.ui.theme.OratorDesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveFluencyExerciseView(
    exercise: FluencyExercise,
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    onClose: () -> Unit,
    onNextExercise: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechHelper = remember { SpeechRecognitionHelper(context) }

    val isListening by speechHelper.isListening.collectAsState()
    val transcript by speechHelper.transcript.collectAsState()
    val rmsDb by speechHelper.rmsDb.collectAsState()
    val errorMsg by speechHelper.errorMessage.collectAsState()

    var secondsElapsed by remember { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var detectedPauses by remember { mutableStateOf(0) }

    // Controlled Speech phrase stepper
    var currentPhraseIndex by remember { mutableStateOf(0) }

    // Spontaneous Speaking custom duration & prompts
    var spontaneousDuration by remember { mutableIntStateOf(exercise.durationSeconds) }
    var spontaneousPromptIndex by remember { mutableIntStateOf(0) }

    // Confidence dialogs
    var showPreConfidence by remember { mutableStateOf(true) }
    var showPostConfidence by remember { mutableStateOf(false) }
    var preConfidenceScore by remember { mutableIntStateOf(3) }
    var postConfidenceScore by remember { mutableIntStateOf(4) }

    // Analysis & reporting
    var isAnalyzing by remember { mutableStateOf(false) }
    var sessionReport by remember { mutableStateOf<AIResult?>(null) }
    var sessionComparison by remember { mutableStateOf<SessionComparison?>(null) }
    var finalLiveMetrics by remember { mutableStateOf<LiveSpeechMetrics?>(null) }

    // Manual typing fallback
    var manualInputText by remember { mutableStateOf("") }
    var showManualInput by remember { mutableStateOf(false) }

    // Permission launcher
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        if (isGranted) {
            speechHelper.startListening()
            isTimerRunning = true
        }
    }

    // Timer loop with pause heuristics
    LaunchedEffect(isTimerRunning) {
        var lastWordCount = 0
        var silentTicks = 0
        while (isTimerRunning) {
            delay(1000)
            secondsElapsed++
            val currentWords = transcript.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
            if (currentWords == lastWordCount && currentWords > 0) {
                silentTicks++
                if (silentTicks >= 3 && silentTicks % 3 == 0) {
                    detectedPauses++
                }
            } else {
                silentTicks = 0
                lastWordCount = currentWords
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechHelper.stopListening()
            speechHelper.reset()
        }
    }

    val liveMetrics = remember(transcript, secondsElapsed, detectedPauses) {
        val categoryContext = when (exercise.category) {
            FluencyCategory.CONTROLLED_SPEECH -> "CONTROLLED"
            FluencyCategory.READING -> "READING"
            FluencyCategory.THOUGHT_CHUNKING -> "THOUGHT_CHUNKING"
            FluencyCategory.SPONTANEOUS -> "SPONTANEOUS"
            FluencyCategory.RECOVERY -> "RECOVERY"
            else -> "CASUAL"
        }
        SpeechMetricsAnalyzer.analyze(
            transcript = transcript,
            durationSeconds = secondsElapsed,
            detectedPauses = detectedPauses,
            contextCategory = categoryContext
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(exercise.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${exercise.category.displayName} • ${exercise.difficulty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("active_exercise_back")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val targetSeconds = if (exercise.category == FluencyCategory.SPONTANEOUS) spontaneousDuration else exercise.durationSeconds
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = String.format("%02d:%02d / %02d:%02d", secondsElapsed / 60, secondsElapsed % 60, targetSeconds / 60, targetSeconds % 60),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Exercise Coaching & Prompt Card
                when (exercise.category) {
                    FluencyCategory.CONTROLLED_SPEECH -> {
                        ControlledSpeechCard(
                            phrases = exercise.chunkedPhrases.ifEmpty { listOf(exercise.samplePrompt) },
                            currentIndex = currentPhraseIndex,
                            onNext = {
                                val total = exercise.chunkedPhrases.size
                                if (total > 0) currentPhraseIndex = (currentPhraseIndex + 1) % total
                            },
                            onPrev = {
                                val total = exercise.chunkedPhrases.size
                                if (total > 0) currentPhraseIndex = (currentPhraseIndex - 1 + total) % total
                            }
                        )
                    }
                    FluencyCategory.THOUGHT_CHUNKING -> {
                        ThoughtChunkingCard(
                            samplePrompt = exercise.samplePrompt,
                            chunkedPhrases = exercise.chunkedPhrases
                        )
                    }
                    FluencyCategory.READING -> {
                        ReadingAloudCard(
                            title = exercise.title,
                            text = exercise.samplePrompt,
                            coachingTip = exercise.coachingTip
                        )
                    }
                    FluencyCategory.SPONTANEOUS -> {
                        SpontaneousDrillCard(
                            duration = spontaneousDuration,
                            onSelectDuration = { spontaneousDuration = it },
                            prompt = SPONTANEOUS_PROMPT_LIBRARY.getOrElse(spontaneousPromptIndex) { exercise.samplePrompt },
                            onShuffle = {
                                spontaneousPromptIndex = (spontaneousPromptIndex + 1) % SPONTANEOUS_PROMPT_LIBRARY.size
                            }
                        )
                    }
                    FluencyCategory.RECOVERY -> {
                        RecoveryPracticeCard(
                            samplePrompt = exercise.samplePrompt,
                            coachingTip = exercise.coachingTip
                        )
                    }
                    else -> {
                        StandardPromptCard(exercise = exercise)
                    }
                }

                // Real-time Metrics Card with Pacing, Pauses, Fillers & Restarts
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricBadge(
                                icon = Icons.Default.Speed,
                                label = "Pacing",
                                value = "${liveMetrics.wpm} WPM",
                                modifier = Modifier.weight(1f)
                            )
                            MetricBadge(
                                icon = Icons.Default.PauseCircle,
                                label = "Pauses",
                                value = "${liveMetrics.pauseCount}",
                                modifier = Modifier.weight(1f)
                            )
                            MetricBadge(
                                icon = Icons.Default.RecordVoiceOver,
                                label = "Fillers",
                                value = "${liveMetrics.fillerCount}",
                                modifier = Modifier.weight(1f)
                            )
                            MetricBadge(
                                icon = Icons.Default.Repeat,
                                label = "Restarts",
                                value = "${liveMetrics.repetitionCount}",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = liveMetrics.pacingPraise,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Recovery: ${liveMetrics.recoveryScore}/100",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Live Spoken Transcript & Manual Input
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Notes,
                                    contentDescription = null,
                                    tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Spoken Transcript",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(onClick = { showManualInput = !showManualInput }) {
                                Text(if (showManualInput) "Hide Keyboard" else "Manual Type")
                            }
                        }

                        if (showManualInput) {
                            OutlinedTextField(
                                value = manualInputText,
                                onValueChange = { manualInputText = it },
                                placeholder = { Text("Type transcript if microphone is unavailable...") },
                                shape = RoundedCornerShape(OratorDesignTokens.InputCornerRadius),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (manualInputText.isNotBlank()) {
                                            speechHelper.appendManualText(manualInputText)
                                            manualInputText = ""
                                        }
                                    }) {
                                        Icon(Icons.Default.Send, contentDescription = "Add text")
                                    }
                                }
                            )
                        } else {
                            Text(
                                text = if (transcript.isBlank()) {
                                    if (isListening) "Listening... speak comfortably at a steady, measured pace."
                                    else "Tap the microphone below to start speaking."
                                } else transcript,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (errorMsg != null) {
                            Text(
                                text = errorMsg ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Audio Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AudioWaveformVisualizer(
                    isListening = isListening,
                    rmsDb = rmsDb,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reset Button
                    FilledTonalIconButton(
                        onClick = {
                            speechHelper.reset()
                            secondsElapsed = 0
                            detectedPauses = 0
                            isTimerRunning = false
                        },
                        modifier = Modifier.testTag("reset_exercise_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }

                    // Main Mic / Start Button
                    FloatingActionButton(
                        onClick = {
                            if (isListening) {
                                speechHelper.stopListening()
                                isTimerRunning = false
                            } else {
                                if (!hasMicPermission) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    speechHelper.startListening()
                                    isTimerRunning = true
                                }
                            }
                        },
                        containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("toggle_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Speaking" else "Start Speaking",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Complete & Evaluate Button
                    Button(
                        onClick = {
                            speechHelper.stopListening()
                            isTimerRunning = false
                            showPostConfidence = true
                        },
                        enabled = transcript.isNotBlank() || secondsElapsed >= 5,
                        modifier = Modifier.testTag("complete_exercise_button")
                    ) {
                        Text("Finish")
                    }
                }
            }
        }
    }

    // Pre-session confidence dialog
    if (showPreConfidence) {
        ConfidenceRatingDialog(
            title = "Before you speak...",
            subtitle = "How calm and confident do you feel right now?",
            initialRating = preConfidenceScore,
            onConfirm = { rating ->
                preConfidenceScore = rating
                showPreConfidence = false
            },
            onDismiss = { showPreConfidence = false }
        )
    }

    // Post-session confidence check and evaluation
    if (showPostConfidence) {
        ConfidenceRatingDialog(
            title = "Exercise Completed!",
            subtitle = "How did you feel while speaking?",
            initialRating = preConfidenceScore,
            onConfirm = { rating ->
                postConfidenceScore = rating
                showPostConfidence = false
                isAnalyzing = true

                scope.launch {
                    val finalMetrics = liveMetrics
                    finalLiveMetrics = finalMetrics

                    // Retrieve previous session for comparison
                    val recentList = repository.recentSessions.first()
                    val prevSession = recentList.firstOrNull { it.sessionType == "FLUENCY" && it.title == exercise.title }
                        ?: recentList.firstOrNull { it.sessionType == "FLUENCY" }

                    val comparison = SpeechMetricsAnalyzer.compareSessions(
                        current = finalMetrics,
                        currentPostConfidence = postConfidenceScore,
                        previous = prevSession
                    )
                    sessionComparison = comparison

                    val result = orchestrator.analyzeSession(
                        domain = CoachingDomain.FLUENCY,
                        prompt = "Fluency Drill: ${exercise.title}. Objective: ${exercise.objective}. Tip: ${exercise.coachingTip}",
                        transcript = transcript.ifBlank { "Completed speaking drill at ${finalMetrics.wpm} WPM with calm recovery." },
                        wpm = finalMetrics.wpm,
                        pauseCount = finalMetrics.pauseCount,
                        fillerCount = finalMetrics.fillerCount,
                        repetitionCount = finalMetrics.repetitionCount,
                        queryTopic = exercise.title
                    )

                    val sessionRecord = PracticeSession(
                        sessionType = "FLUENCY",
                        title = exercise.title,
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = secondsElapsed.coerceAtLeast(10),
                        transcript = transcript,
                        wpm = finalMetrics.wpm,
                        pauseCount = finalMetrics.pauseCount,
                        fillerCount = finalMetrics.fillerCount,
                        repetitionCount = finalMetrics.repetitionCount,
                        confidencePre = preConfidenceScore,
                        confidencePost = postConfidenceScore,
                        overallScore = result.response.fluency_score,
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
                    repository.saveSession(sessionRecord)

                    // Promote insight into shared student memory
                    if (result.response.weaknesses.isNotEmpty()) {
                        repository.updateMemoryFromAnalysis(
                            domain = "FLUENCY",
                            key = "fluency_focus_${exercise.id}",
                            insight = "Area to practice: ${result.response.weaknesses.first()}",
                            importance = 2
                        )
                    }

                    isAnalyzing = false
                    sessionReport = result
                }
            },
            onDismiss = { showPostConfidence = false }
        )
    }

    // Loading overlay
    if (isAnalyzing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Analyzing Delivery...") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Evaluating pace, pause placement, fillers, and confidence...")
                }
            },
            confirmButton = {}
        )
    }

    // Post-session Coaching Report
    sessionReport?.let { report ->
        FluencySessionReportDialog(
            aiResult = report,
            metrics = finalLiveMetrics ?: liveMetrics,
            durationSeconds = secondsElapsed,
            preConfidence = preConfidenceScore,
            postConfidence = postConfidenceScore,
            comparison = sessionComparison,
            onRetry = {
                sessionReport = null
                speechHelper.reset()
                secondsElapsed = 0
                detectedPauses = 0
            },
            onNextExercise = onNextExercise,
            onDismiss = {
                sessionReport = null
                onClose()
            }
        )
    }
}

@Composable
private fun ControlledSpeechCard(
    phrases: List<String>,
    currentIndex: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gentle Speech & Easy Onset",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Phrase ${currentIndex + 1} of ${phrases.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = phrases.getOrElse(currentIndex) { "" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onPrev,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("prev_phrase_button")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Prev")
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Tip: Release breath softly before sound",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                OutlinedButton(
                    onClick = onNext,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("next_phrase_button")
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ThoughtChunkingCard(
    samplePrompt: String,
    chunkedPhrases: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Thought Chunking Practice",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Speak each cluster in one smooth breath, then pause silently.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (chunkedPhrases.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunkedPhrases.forEachIndexed { idx, phrase ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = phrase,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = samplePrompt,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ReadingAloudCard(
    title: String,
    text: String,
    coachingTip: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Reading Text",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Coach Tip: $coachingTip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun SpontaneousDrillCard(
    duration: Int,
    onSelectDuration: (Int) -> Unit,
    prompt: String,
    onShuffle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spontaneous Speaking Prompt",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onShuffle,
                    modifier = Modifier.size(28.dp).testTag("shuffle_spontaneous_prompt")
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle topic", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = prompt,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Duration Presets Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(30 to "30s", 60 to "60s", 120 to "2m", 300 to "5m").forEach { (secs, label) ->
                    FilterChip(
                        selected = duration == secs,
                        onClick = { onSelectDuration(secs) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("duration_$label")
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoveryPracticeCard(
    samplePrompt: String,
    coachingTip: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "4-Step Calm Speech Recovery",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Step boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("1. Pause", "2. Breathe", "3. Restart", "4. Continue").forEach { step ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = step,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Practice Prompt (Never force speech):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = samplePrompt,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StandardPromptCard(exercise: FluencyExercise) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Practice Prompt",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = exercise.samplePrompt,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Coach Tip: ${exercise.coachingTip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
