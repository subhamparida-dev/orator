package com.example.ui.fluency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.ui.common.AudioWaveformVisualizer
import com.example.ui.common.SessionReportDialog
import com.example.ui.theme.OratorDesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DifficultWordsView(
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val weakWords by repository.weakWords.collectAsState(initial = emptyList())

    var selectedWordForPractice by remember { mutableStateOf<WeakWord?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, PRACTICING, MASTERED

    // Seed default practice words if none exist
    LaunchedEffect(Unit) {
        repository.seedInitialPracticeWordsIfEmpty()
    }

    if (selectedWordForPractice != null) {
        WordPracticeDrillView(
            word = selectedWordForPractice!!,
            repository = repository,
            orchestrator = orchestrator,
            onClose = { selectedWordForPractice = null }
        )
        return
    }

    val filteredWords = remember(weakWords, selectedFilter) {
        when (selectedFilter) {
            "PRACTICING" -> weakWords.filter { it.masteryScore < 90 }
            "MASTERED" -> weakWords.filter { it.masteryScore >= 90 }
            else -> weakWords
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Practice Words", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Syllable breakdowns & recurring terms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("words_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_word_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Word")
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
                .padding(horizontal = 16.dp)
        ) {
            // Non-medical info banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Spellcheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Technical terms with complex consonant clusters can trigger hesitation. Deconstructing syllables builds articulatory muscle memory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${weakWords.size})") },
                    modifier = Modifier.testTag("filter_all_words")
                )
                FilterChip(
                    selected = selectedFilter == "PRACTICING",
                    onClick = { selectedFilter = "PRACTICING" },
                    label = { Text("To Practice (${weakWords.count { it.masteryScore < 90 }})") },
                    modifier = Modifier.testTag("filter_practicing_words")
                )
                FilterChip(
                    selected = selectedFilter == "MASTERED",
                    onClick = { selectedFilter = "MASTERED" },
                    label = { Text("Mastered (${weakWords.count { it.masteryScore >= 90 }})") },
                    modifier = Modifier.testTag("filter_mastered_words")
                )
            }

            if (filteredWords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No practice words in this view. Tap '+' to add one!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(filteredWords, key = { it.id }) { wordItem ->
                        WordItemCard(
                            wordItem = wordItem,
                            onPractice = { selectedWordForPractice = wordItem },
                            onMarkMastered = {
                                scope.launch {
                                    val newScore = if (wordItem.masteryScore >= 90) 40 else 100
                                    repository.updateWordMastery(wordItem.id, newScore)
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    repository.deleteWeakWord(wordItem)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Custom Word Dialog
    if (showAddDialog) {
        AddWordDialog(
            onConfirm = { word, phonetic, sentence ->
                scope.launch {
                    repository.addWeakWord(
                        word = word,
                        phonetic = phonetic,
                        contextSentence = sentence,
                        category = "FLUENCY"
                    )
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun WordItemCard(
    wordItem: WeakWord,
    onPractice: () -> Unit,
    onMarkMastered: () -> Unit,
    onDelete: () -> Unit
) {
    val isMastered = wordItem.masteryScore >= 90

    Card(
        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("word_card_${wordItem.word.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = wordItem.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (wordItem.phonetic.isNotBlank()) {
                        Text(
                            text = wordItem.phonetic,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                    color = if (isMastered) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (isMastered) "Mastered" else "${wordItem.masteryScore}% Mastery",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isMastered) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (wordItem.contextSentence.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Sentence: \"${wordItem.contextSentence}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onPractice,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("practice_word_${wordItem.word.lowercase()}")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Practice")
                    }

                    OutlinedButton(
                        onClick = onMarkMastered,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("master_word_${wordItem.word.lowercase()}")
                    ) {
                        Icon(
                            imageVector = if (isMastered) Icons.Default.RotateLeft else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isMastered) "Reset" else "Mastered")
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_word_${wordItem.word.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Remove Word",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WordPracticeDrillView(
    word: WeakWord,
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechHelper = remember { SpeechRecognitionHelper(context) }

    val isListening by speechHelper.isListening.collectAsState()
    val transcript by speechHelper.transcript.collectAsState()
    val rmsDb by speechHelper.rmsDb.collectAsState()

    var secondsElapsed by remember { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var sessionReport by remember { mutableStateOf<AIResult?>(null) }

    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) {
            delay(1000)
            secondsElapsed++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechHelper.stopListening()
            speechHelper.reset()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Word Drill: ${word.word}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("word_drill_back")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Word Syllable Spotlight Card
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = word.word,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = word.phonetic.ifBlank { "Deconstruct into clean syllables" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Practice Sentence Card
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                            text = "1. Pronounce '${word.word}' clearly twice.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (word.contextSentence.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "2. Read in sentence: \"${word.contextSentence}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transcript
                Card(
                    shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Spoken Transcript",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (transcript.isBlank()) "Tap the microphone below and speak..." else transcript,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AudioWaveformVisualizer(
                    isListening = isListening,
                    rmsDb = rmsDb,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            speechHelper.reset()
                            secondsElapsed = 0
                            isTimerRunning = false
                        }
                    ) {
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
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("word_drill_mic")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop" else "Record",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Button(
                        onClick = {
                            speechHelper.stopListening()
                            isTimerRunning = false
                            isAnalyzing = true

                            scope.launch {
                                // Update mastery in DB
                                val updatedMastery = (word.masteryScore + 20).coerceAtMost(100)
                                repository.updateWordMastery(word.id, updatedMastery)

                                val metrics = SpeechMetricsAnalyzer.analyze(transcript, secondsElapsed.coerceAtLeast(5))
                                val result = orchestrator.analyzeSession(
                                    domain = CoachingDomain.FLUENCY,
                                    prompt = "Difficult Word Drill: ${word.word}. Syllable breakdown: ${word.phonetic}",
                                    transcript = transcript.ifBlank { "Practiced articulating ${word.word} with syllable deconstruction." },
                                    wpm = metrics.wpm,
                                    pauseCount = metrics.pauseCount,
                                    fillerCount = metrics.fillerCount,
                                    repetitionCount = metrics.repetitionCount,
                                    queryTopic = "Difficult Word: ${word.word}"
                                )

                                val sessionRecord = PracticeSession(
                                    sessionType = "FLUENCY",
                                    title = "Difficult Word Drill: ${word.word}",
                                    timestamp = System.currentTimeMillis(),
                                    durationSeconds = secondsElapsed.coerceAtLeast(10),
                                    transcript = transcript,
                                    wpm = metrics.wpm,
                                    pauseCount = metrics.pauseCount,
                                    fillerCount = metrics.fillerCount,
                                    repetitionCount = metrics.repetitionCount,
                                    confidencePre = 3,
                                    confidencePost = 4,
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

                                isAnalyzing = false
                                sessionReport = result
                            }
                        },
                        enabled = transcript.isNotBlank() || secondsElapsed >= 5,
                        modifier = Modifier.testTag("finish_word_drill")
                    ) {
                        Text("Finish")
                    }
                }
            }
        }
    }

    if (isAnalyzing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Evaluating Articulation...") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(44.dp))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Updating word mastery and fluency metrics...")
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
            pauseCount = 0,
            onDismiss = {
                sessionReport = null
                onClose()
            }
        )
    }
}

@Composable
private fun AddWordDialog(
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var word by remember { mutableStateOf("") }
    var phonetic by remember { mutableStateOf("") }
    var sentence by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Practice Word", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = word,
                    onValueChange = { word = it },
                    label = { Text("Word (e.g. Asynchronous)") },
                    shape = RoundedCornerShape(OratorDesignTokens.InputCornerRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_word_input")
                )
                OutlinedTextField(
                    value = phonetic,
                    onValueChange = { phonetic = it },
                    label = { Text("Syllable Breakdown (e.g. A-syn-chro-nous)") },
                    shape = RoundedCornerShape(OratorDesignTokens.InputCornerRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_phonetic_input")
                )
                OutlinedTextField(
                    value = sentence,
                    onValueChange = { sentence = it },
                    label = { Text("Example Sentence") },
                    shape = RoundedCornerShape(OratorDesignTokens.InputCornerRadius),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_sentence_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (word.isNotBlank()) {
                        onConfirm(word.trim(), phonetic.trim(), sentence.trim())
                    }
                },
                enabled = word.isNotBlank(),
                modifier = Modifier.testTag("confirm_add_word")
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
