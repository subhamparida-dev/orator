package com.example.ui.fluency

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AIOrchestrator
import com.example.ai.AIResult
import com.example.ai.CoachingDomain
import com.example.data.PracticeSession
import com.example.data.StudentRepository
import com.example.ui.common.ConfidenceRatingDialog
import com.example.ui.common.SessionReportDialog
import com.example.ui.theme.OratorDesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BreathingPhase(val label: String, val subtitle: String, val durationSeconds: Int) {
    INHALE("Inhale", "Breathe in slowly through your nose", 4),
    HOLD_IN("Hold", "Maintain soft, relaxed vocal cords", 4),
    EXHALE("Exhale", "Release breath gently through your mouth", 4),
    REST("Rest", "Rest calmly before the next breath cycle", 4)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreathingExerciseView(
    exercise: FluencyExercise,
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableStateOf(0) }
    var currentCycleSeconds by remember { mutableStateOf(0) }
    var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
    var completedCycles by remember { mutableStateOf(0) }

    var showPreConfidence by remember { mutableStateOf(true) }
    var showPostConfidence by remember { mutableStateOf(false) }
    var preConfidenceScore by remember { mutableStateOf(3) }
    var postConfidenceScore by remember { mutableStateOf(4) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var sessionReport by remember { mutableStateOf<AIResult?>(null) }

    // Breathing phase progression loop
    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000)
            secondsElapsed++
            currentCycleSeconds++

            val phaseDuration = currentPhase.durationSeconds
            if (currentCycleSeconds >= phaseDuration) {
                currentCycleSeconds = 0
                currentPhase = when (currentPhase) {
                    BreathingPhase.INHALE -> BreathingPhase.HOLD_IN
                    BreathingPhase.HOLD_IN -> BreathingPhase.EXHALE
                    BreathingPhase.EXHALE -> BreathingPhase.REST
                    BreathingPhase.REST -> {
                        completedCycles++
                        BreathingPhase.INHALE
                    }
                }
            }
        }
    }

    // Animation progress (0f to 1f) for the breathing circle
    val targetScale = when (currentPhase) {
        BreathingPhase.INHALE -> 1.0f
        BreathingPhase.HOLD_IN -> 1.0f
        BreathingPhase.EXHALE -> 0.45f
        BreathingPhase.REST -> 0.45f
    }
    val animatedScale by animateFloatAsState(
        targetValue = if (isRunning) targetScale else 0.5f,
        animationSpec = tween(
            durationMillis = if (isRunning) currentPhase.durationSeconds * 1000 else 500,
            easing = LinearEasing
        ),
        label = "breathing_circle_scale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("breathing_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        text = String.format("%02d:%02d", secondsElapsed / 60, secondsElapsed % 60),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Calm Guidance Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Air,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "4-4-4-4 Box Breathing Warm-up",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Calms autonomic nervous system, drops shoulder tension, and prepares vocal folds for gentle easy speech onset.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Visual Breathing Guide Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer subtle glow
                Box(
                    modifier = Modifier
                        .size(240.dp * animatedScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                )
                // Mid pulse
                Box(
                    modifier = Modifier
                        .size(180.dp * animatedScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                )
                // Core circle
                Box(
                    modifier = Modifier
                        .size(130.dp * animatedScale.coerceAtLeast(0.6f))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isRunning) currentPhase.label else "Ready",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        if (isRunning) {
                            Text(
                                text = "${currentPhase.durationSeconds - currentCycleSeconds}s",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Cycle stats & instructions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isRunning) currentPhase.subtitle else "Tap Start Warm-up below to begin diaphragmatic breathing",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Completed cycles: $completedCycles",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Non-medical disclaimer
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Note: Relaxation & breath pacing is speech warm-up training, not medical diagnosis or treatment.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset Button
                FilledTonalIconButton(
                    onClick = {
                        isRunning = false
                        secondsElapsed = 0
                        currentCycleSeconds = 0
                        currentPhase = BreathingPhase.INHALE
                        completedCycles = 0
                    },
                    modifier = Modifier.testTag("breathing_reset_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset")
                }

                // Start / Pause Floating Action Button
                FloatingActionButton(
                    onClick = { isRunning = !isRunning },
                    containerColor = if (isRunning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isRunning) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("breathing_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) "Pause" else "Start",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Complete Button
                Button(
                    onClick = {
                        isRunning = false
                        showPostConfidence = true
                    },
                    enabled = secondsElapsed >= 15 || completedCycles >= 1,
                    modifier = Modifier.testTag("breathing_finish_button")
                ) {
                    Text("Finish")
                }
            }
        }
    }

    // Pre-confidence
    if (showPreConfidence) {
        ConfidenceRatingDialog(
            title = "Before you begin...",
            subtitle = "How relaxed and centered are you feeling right now?",
            onConfirm = { rating ->
                preConfidenceScore = rating
                showPreConfidence = false
            },
            onDismiss = { showPreConfidence = false }
        )
    }

    // Post-confidence
    if (showPostConfidence) {
        ConfidenceRatingDialog(
            title = "Warm-up Complete!",
            subtitle = "How calm and relaxed do your vocal muscles feel now?",
            initialRating = (preConfidenceScore + 1).coerceAtMost(5),
            onConfirm = { rating ->
                postConfidenceScore = rating
                showPostConfidence = false
                isAnalyzing = true

                scope.launch {
                    val result = orchestrator.analyzeSession(
                        domain = CoachingDomain.FLUENCY,
                        prompt = "Warm-up: Box Breathing & Vocal Relaxation. Completed $completedCycles cycles.",
                        transcript = "Completed $completedCycles breath cycles over $secondsElapsed seconds with 4-4-4-4 diaphragmatic rhythm.",
                        wpm = 0,
                        pauseCount = completedCycles * 4,
                        fillerCount = 0,
                        repetitionCount = 0,
                        queryTopic = "Breathing Warm-up"
                    )

                    val sessionRecord = PracticeSession(
                        sessionType = "FLUENCY",
                        title = "Breathing & Vocal Relaxation Warm-up",
                        timestamp = System.currentTimeMillis(),
                        durationSeconds = secondsElapsed.coerceAtLeast(20),
                        transcript = "Box breathing warm-up completed: $completedCycles cycles.",
                        wpm = 0,
                        pauseCount = completedCycles * 4,
                        fillerCount = 0,
                        repetitionCount = 0,
                        confidencePre = preConfidenceScore,
                        confidencePost = postConfidenceScore,
                        overallScore = 90,
                        fluencyScore = 90,
                        englishScore = 80,
                        presentationScore = 85,
                        technicalScore = 80,
                        feedback = "Vocal relaxation and diaphragmatic breathing completed smoothly. Your vocal apparatus is primed for easy speech onset.",
                        strengths = "Calm diaphragmatic pacing, relaxed laryngeal tone, breath control",
                        weaknesses = "None during warm-up",
                        nextAction = "Proceed to Easy Speech or Thought Chunking drill",
                        providerUsed = result.providerUsed,
                        modelUsed = result.modelUsed
                    )
                    repository.saveSession(sessionRecord)

                    isAnalyzing = false
                    sessionReport = result
                }
            },
            onDismiss = { showPostConfidence = false }
        )
    }

    if (isAnalyzing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Saving Warm-up Session...") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Updating student progress and streaks...")
                }
            },
            confirmButton = {}
        )
    }

    sessionReport?.let { report ->
        SessionReportDialog(
            aiResult = report,
            wpm = 0,
            durationSeconds = secondsElapsed,
            fillerCount = 0,
            pauseCount = completedCycles * 4,
            onDismiss = {
                sessionReport = null
                onClose()
            }
        )
    }
}
