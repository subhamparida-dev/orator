package com.example.ui.fluency

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.data.PracticeSession
import com.example.data.StudentRepository
import com.example.ui.theme.OratorDesignTokens
import java.util.Calendar

@Composable
fun FluencyGymScreen(
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    modifier: Modifier = Modifier
) {
    var activeExercise by remember { mutableStateOf<FluencyExercise?>(null) }
    var showDifficultWordsView by remember { mutableStateOf(false) }
    var routineStepIndex by remember { mutableStateOf<Int?>(null) }

    // Check if Difficult Words view is active
    if (showDifficultWordsView) {
        DifficultWordsView(
            repository = repository,
            orchestrator = orchestrator,
            onClose = { showDifficultWordsView = false }
        )
        return
    }

    // Check if an active exercise is selected
    if (activeExercise != null) {
        val currentEx = activeExercise!!
        if (currentEx.isBreathing) {
            BreathingExerciseView(
                exercise = currentEx,
                repository = repository,
                orchestrator = orchestrator,
                onClose = {
                    activeExercise = null
                    routineStepIndex = null
                }
            )
        } else if (currentEx.isWordPractice) {
            DifficultWordsView(
                repository = repository,
                orchestrator = orchestrator,
                onClose = {
                    activeExercise = null
                    routineStepIndex = null
                }
            )
        } else {
            ActiveFluencyExerciseView(
                exercise = currentEx,
                repository = repository,
                orchestrator = orchestrator,
                onClose = {
                    activeExercise = null
                    routineStepIndex = null
                },
                onNextExercise = if (routineStepIndex != null && routineStepIndex!! < DEFAULT_DAILY_ROUTINE.size - 1) {
                    {
                        val nextStep = routineStepIndex!! + 1
                        routineStepIndex = nextStep
                        val nextExId = DEFAULT_DAILY_ROUTINE[nextStep].exerciseId
                        activeExercise = FLUENCY_EXERCISES_CATALOG.firstOrNull { it.id == nextExId }
                    }
                } else null
            )
        }
        return
    }

    // Main Fluency Gym Home
    FluencyGymHomeView(
        repository = repository,
        onSelectExercise = { ex ->
            routineStepIndex = null
            activeExercise = ex
        },
        onStartRoutine = {
            routineStepIndex = 0
            val firstStepExId = DEFAULT_DAILY_ROUTINE[0].exerciseId
            activeExercise = FLUENCY_EXERCISES_CATALOG.firstOrNull { it.id == firstStepExId }
        },
        onOpenDifficultWords = { showDifficultWordsView = true },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FluencyGymHomeView(
    repository: StudentRepository,
    onSelectExercise: (FluencyExercise) -> Unit,
    onStartRoutine: () -> Unit,
    onOpenDifficultWords: () -> Unit,
    modifier: Modifier = Modifier
) {
    val studentProfile by repository.studentProfile.collectAsState(initial = null)
    val recentSessions by repository.recentSessions.collectAsState(initial = emptyList())
    val weakWords by repository.weakWords.collectAsState(initial = emptyList())

    var selectedCategory by remember { mutableStateOf<FluencyCategory?>(null) }

    // Calculate today's fluency sessions
    val (todaySessionsCount, todayMinutes) = remember(recentSessions) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis

        val todayFluency = recentSessions.filter { it.sessionType == "FLUENCY" && it.timestamp >= startOfDay }
        val count = todayFluency.size
        val totalSecs = todayFluency.sumOf { it.durationSeconds }
        Pair(count, totalSecs / 60)
    }

    // Compute recent fluency improvements from real session history
    val fluencyImprovementInsight = remember(recentSessions) {
        val fluencyList = recentSessions.filter { it.sessionType == "FLUENCY" }
        if (fluencyList.size >= 2) {
            val latest = fluencyList[0]
            val previous = fluencyList[1]
            val wpmDiff = latest.wpm - previous.wpm
            val fillerDiff = latest.fillerCount - previous.fillerCount
            val confDiff = latest.confidencePost - previous.confidencePost

            val insights = mutableListOf<String>()
            if (fillerDiff < 0) {
                insights.add("Filler words decreased by ${-fillerDiff}")
            }
            if (confDiff > 0) {
                insights.add("Confidence +${confDiff} pts")
            }
            if (latest.wpm in 120..150) {
                insights.add("Pacing in target zone (${latest.wpm} WPM)")
            }
            if (insights.isNotEmpty()) insights.joinToString(" • ")
            else "Pacing steady at ${latest.wpm} WPM across recent rounds"
        } else if (fluencyList.size == 1) {
            "Baseline recorded (${fluencyList[0].wpm} WPM). Complete another drill to track progress."
        } else {
            "Complete your first drill today to establish your speaking baseline."
        }
    }

    // Dynamic Recommended Exercise based on actual history
    val (recommendedExercise, recommendationReason) = remember(recentSessions, weakWords) {
        val fluencyList = recentSessions.filter { it.sessionType == "FLUENCY" }
        val latest = fluencyList.firstOrNull()

        when {
            latest != null && latest.wpm > 165 -> {
                val ex = FLUENCY_EXERCISES_CATALOG.firstOrNull { it.id == 2 } ?: FLUENCY_EXERCISES_CATALOG[1]
                Pair(ex, "Your pace was slightly elevated (${latest.wpm} WPM). Easy onset will help establish controlled pacing.")
            }
            latest != null && latest.fillerCount > 3 -> {
                val ex = FLUENCY_EXERCISES_CATALOG.firstOrNull { it.id == 4 } ?: FLUENCY_EXERCISES_CATALOG[4]
                Pair(ex, "Detected ${latest.fillerCount} fillers in your last round. Thought chunking trains silent rest pauses.")
            }
            weakWords.any { it.masteryScore < 70 } -> {
                val ex = FLUENCY_EXERCISES_CATALOG.firstOrNull { it.id == 13 } ?: FLUENCY_EXERCISES_CATALOG.last()
                Pair(ex, "You have technical practice words ready for syllable deconstruction.")
            }
            todaySessionsCount == 0 -> {
                val ex = FLUENCY_EXERCISES_CATALOG.firstOrNull { it.id == 1 } ?: FLUENCY_EXERCISES_CATALOG[0]
                Pair(ex, "Begin today with vocal cord relaxation and diaphragmatic breathing.")
            }
            else -> {
                val ex = FLUENCY_EXERCISES_CATALOG.firstOrNull { it.id == 10 } ?: FLUENCY_EXERCISES_CATALOG[7]
                Pair(ex, "Ready for spontaneous delivery: apply steady rhythm in a 60-second impromptu pitch.")
            }
        }
    }

    val filteredExercises = remember(selectedCategory) {
        if (selectedCategory == null) FLUENCY_EXERCISES_CATALOG
        else FLUENCY_EXERCISES_CATALOG.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Fluency Gym",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Structured speaking practice & cadence training",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Practice Words Quick Access Action
                    FilledTonalButton(
                        onClick = onOpenDifficultWords,
                        shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("open_words_manager_action")
                    ) {
                        Icon(Icons.Default.Spellcheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("My Words (${weakWords.size})", style = MaterialTheme.typography.labelSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 1. Goal & Streak & Progress Card
            item {
                GoalAndStreakCard(
                    completedCount = todaySessionsCount,
                    targetCount = 3,
                    todayMinutes = todayMinutes,
                    streak = studentProfile?.currentStreak ?: 1,
                    recentImprovement = fluencyImprovementInsight
                )
            }

            // 2. Recommended Exercise Card
            item {
                RecommendedExerciseCard(
                    exercise = recommendedExercise,
                    reason = recommendationReason,
                    onStart = { onSelectExercise(recommendedExercise) }
                )
            }

            // 3. Guided Daily Routine Card
            item {
                GuidedDailyRoutineCard(
                    onStartRoutine = onStartRoutine
                )
            }

            // Non-medical Disclaimer Banner
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Communication & Fluency Training: Orator provides speaking pacing, breath support, and presentation confidence drills. It is not a medical diagnosis or therapy tool.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Category Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Exercise Categories",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == null,
                                onClick = { selectedCategory = null },
                                label = { Text("All (${FLUENCY_EXERCISES_CATALOG.size})") },
                                modifier = Modifier.testTag("filter_category_all")
                            )
                        }
                        items(FluencyCategory.values()) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                                label = { Text(cat.displayName) },
                                modifier = Modifier.testTag("filter_category_${cat.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Exercises List
            items(filteredExercises, key = { it.id }) { exercise ->
                FluencyExerciseCard(
                    exercise = exercise,
                    onClick = { onSelectExercise(exercise) }
                )
            }
        }
    }
}

@Composable
private fun GoalAndStreakCard(
    completedCount: Int,
    targetCount: Int,
    todayMinutes: Int,
    streak: Int,
    recentImprovement: String
) {
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
                Column {
                    Text(
                        text = "Today's Fluency Goal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$completedCount of $targetCount drills completed ($todayMinutes mins spoken)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("fluency_streak_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔥 $streak Day Streak",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar
            val progress = (completedCount.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Improvement insight
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = recentImprovement,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendedExerciseCard(
    exercise: FluencyExercise,
    reason: String,
    onStart: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "⭐ Recommended For You",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "${exercise.durationSeconds / 60} min drill",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = exercise.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStart,
                shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_recommended_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Recommended Drill")
            }
        }
    }
}

@Composable
private fun GuidedDailyRoutineCard(
    onStartRoutine: () -> Unit
) {
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Daily Fluency Routine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "14-min structured progressive circuit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "5 Steps",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Steps overview list
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DEFAULT_DAILY_ROUTINE.forEach { step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "${step.stepIndex}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${step.title} (${step.durationMinutes}m)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedButton(
                onClick = onStartRoutine,
                shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("start_daily_routine_button")
            ) {
                Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Guided 14-Min Routine")
            }
        }
    }
}

@Composable
private fun FluencyExerciseCard(
    exercise: FluencyExercise,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exercise_card_${exercise.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                val icon = when (exercise.category) {
                    FluencyCategory.WARM_UP -> Icons.Default.Air
                    FluencyCategory.CONTROLLED_SPEECH -> Icons.Default.GraphicEq
                    FluencyCategory.READING -> Icons.Default.MenuBook
                    FluencyCategory.THOUGHT_CHUNKING -> Icons.Default.FormatAlignLeft
                    FluencyCategory.SPONTANEOUS -> Icons.Default.MicNone
                    FluencyCategory.RECOVERY -> Icons.Default.SelfImprovement
                    FluencyCategory.DIFFICULT_WORDS -> Icons.Default.Spellcheck
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = exercise.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Objective
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎯 ${exercise.objective}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${exercise.durationSeconds / 60}m ${exercise.durationSeconds % 60}s",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = exercise.difficulty,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Start Drill ➔",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
