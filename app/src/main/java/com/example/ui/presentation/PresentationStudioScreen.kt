package com.example.ui.presentation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AIOrchestrator
import com.example.ai.AIResult
import com.example.ai.CoachingDomain
import com.example.data.PracticeSession
import com.example.data.Presentation
import com.example.data.StudentRepository
import com.example.speech.SpeechMetricsAnalyzer
import com.example.speech.SpeechRecognitionHelper
import com.example.speech.TextToSpeechHelper
import com.example.ui.common.AudioWaveformVisualizer
import com.example.ui.common.MetricBadge
import com.example.ui.common.SessionReportDialog
import com.example.ui.theme.OratorDesignTokens
import com.example.vision.CameraPreviewCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class SlideItem(
    val slideNumber: Int,
    val title: String,
    val keyIdea: String,
    val points: List<String>,
    val transition: String,
    val difficultWords: List<String>,
    val teacherQuestion: String
)

enum class PresentationMode(val displayName: String) {
    GUIDED("Guided"),
    BULLETS("Bullets"),
    NO_SCRIPT("No Script"),
    TELEPROMPTER("Teleprompter"),
    LIVE_SIMULATION("Simulation"),
    TEACHER_QNA("Teacher Q&A")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationStudioScreen(
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val presentations by repository.allPresentations.collectAsState(initial = emptyList())
    var selectedPresentation by remember { mutableStateOf<Presentation?>(null) }
    var activeMode by remember { mutableStateOf(PresentationMode.GUIDED) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImportError by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val imported = parseImportedPresentationFile(context, uri)
                if (imported != null) {
                    scope.launch {
                        repository.savePresentation(imported)
                        isImportError = false
                        importStatusMessage = "Imported \"${imported.title}\" (${imported.totalSlides} slides)"
                    }
                } else {
                    isImportError = true
                    importStatusMessage = "Could not parse presentation slides from this file."
                }
            } catch (e: Exception) {
                isImportError = true
                importStatusMessage = "Failed to import file: ${e.localizedMessage ?: "Unsupported or corrupted format"}"
            }
        }
    }

    if (selectedPresentation != null) {
        ActivePresentationWorkspace(
            presentation = selectedPresentation!!,
            mode = activeMode,
            repository = repository,
            orchestrator = orchestrator,
            onClose = { selectedPresentation = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Presentation Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Master slide defense, 'Don't read-explain' & Teacher Q&A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("*/*", "application/pdf", "text/plain", "application/json"))
                            },
                            modifier = Modifier.testTag("import_deck_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Import Deck (PDF/TXT/JSON)")
                        }
                        IconButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.testTag("add_deck_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Deck")
                        }
                    }
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
                // Import feedback banner if active
                importStatusMessage?.let { msg ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isImportError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isImportError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isImportError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { importStatusMessage = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
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
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Slideshow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text("Rule: Don't Read — Explain!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Audiences can read the slides themselves. Your goal is to provide the underlying reasoning, design trade-offs, and conceptual narrative.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                item {
                    Text("Select a Slide Deck to Rehearse", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }

                items(presentations) { pres ->
                    Card(
                        shape = RoundedCornerShape(OratorDesignTokens.CardCornerRadius),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = OratorDesignTokens.CardElevation),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("presentation_card_${pres.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pres.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${pres.totalSlides} Slides",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Topic: ${pres.topic}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Target Audience: ${pres.targetAudience}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Mode selector buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        activeMode = PresentationMode.GUIDED
                                        selectedPresentation = pres
                                    },
                                    shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Guided", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        activeMode = PresentationMode.LIVE_SIMULATION
                                        selectedPresentation = pres
                                    },
                                    shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Simulate", fontWeight = FontWeight.SemiBold)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        activeMode = PresentationMode.TEACHER_QNA
                                        selectedPresentation = pres
                                    },
                                    shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Teacher Q&A", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePresentationDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { newPres ->
                scope.launch {
                    repository.savePresentation(newPres)
                    showCreateDialog = false
                }
            }
        )
    }
}

@Composable
fun CreatePresentationDialog(onDismiss: () -> Unit, onSave: (Presentation) -> Unit) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var slide1Key by remember { mutableStateOf("") }
    var slide2Key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Presentation Deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Presentation Title") },
                    placeholder = { Text("e.g. Distributed Database Systems") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Domain / Topic") },
                    placeholder = { Text("e.g. Computer Science Capstone") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = slide1Key,
                    onValueChange = { slide1Key = it },
                    label = { Text("Slide 1 Key Concept") },
                    placeholder = { Text("e.g. Problem statement and motivation") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = slide2Key,
                    onValueChange = { slide2Key = it },
                    label = { Text("Slide 2 Key Concept") },
                    placeholder = { Text("e.g. Architectural diagram and trade-offs") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val slides = JSONArray().apply {
                            put(JSONObject().apply {
                                put("slideNumber", 1)
                                put("title", "1. Problem Formulation")
                                put("keyIdea", slide1Key.ifBlank { "Explain the research motivation and engineering problem." })
                                put("points", JSONArray().apply { put("Context"); put("Current challenges"); put("Proposed approach") })
                                put("transition", "Now let us examine the architectural design.")
                                put("difficultWords", JSONArray().apply { put("Architecture"); put("Scalability") })
                                put("teacherQuestion", "Why did existing methods fail in this context?")
                            })
                            put(JSONObject().apply {
                                put("slideNumber", 2)
                                put("title", "2. System Architecture & Results")
                                put("keyIdea", slide2Key.ifBlank { "Detail the technical implementation and empirical validation." })
                                put("points", JSONArray().apply { put("System components"); put("Benchmark metrics"); put("Summary") })
                                put("transition", "Thank you. I welcome questions.")
                                put("difficultWords", JSONArray().apply { put("Implementation"); put("Throughput") })
                                put("teacherQuestion", "How does this scale with 10x traffic?")
                            })
                        }
                        onSave(
                            Presentation(
                                title = title,
                                topic = topic.ifBlank { "Technical Defense" },
                                totalSlides = 2,
                                slidesJson = slides.toString()
                            )
                        )
                    }
                }
            ) {
                Text("Save Deck")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivePresentationWorkspace(
    presentation: Presentation,
    mode: PresentationMode,
    repository: StudentRepository,
    orchestrator: AIOrchestrator,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechHelper = remember { SpeechRecognitionHelper(context) }
    val tts = remember { TextToSpeechHelper(context) }

    val slides = remember(presentation.slidesJson) {
        parseSlides(presentation.slidesJson)
    }
    var currentSlideIndex by remember { mutableStateOf(0) }
    val currentSlide = slides.getOrElse(currentSlideIndex) {
        SlideItem(1, "Slide", "Explain concept", listOf("Point 1"), "Next slide", emptyList(), "Teacher question")
    }

    val isListening by speechHelper.isListening.collectAsState()
    val transcript by speechHelper.transcript.collectAsState()
    val rmsDb by speechHelper.rmsDb.collectAsState()

    var secondsElapsed by remember { mutableStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var sessionReport by remember { mutableStateOf<AIResult?>(null) }
    var verbatimWarning by remember { mutableStateOf<String?>(null) }
    var activeModeState by remember { mutableStateOf(mode) }

    val stopWords = remember {
        setOf(
            "the", "a", "an", "is", "in", "at", "of", "on", "and", "to", "for", "with", "it",
            "as", "by", "this", "that", "are", "was", "were", "we", "our", "you", "your", "i",
            "my", "or", "be", "from", "have", "has", "will", "so", "but", "not", "can", "if"
        )
    }

    // Live timing
    LaunchedEffect(isTimerRunning) {
        while (isTimerRunning) {
            delay(1000)
            secondsElapsed++
        }
    }

    // "Don't read, explain" real-time detector (Stop-word filtered)
    LaunchedEffect(transcript) {
        if (transcript.isNotBlank() && currentSlide.points.isNotEmpty()) {
            val slideTextCombined = "${currentSlide.title} ${currentSlide.points.joinToString(" ")}"
            val slideWords = slideTextCombined.lowercase().split("\\s+".toRegex())
                .map { it.replace("[^a-zA-Z0-9]".toRegex(), "") }
                .filter { it.length > 2 && it !in stopWords }
                .toSet()
            val spokenWords = transcript.lowercase().split("\\s+".toRegex())
                .map { it.replace("[^a-zA-Z0-9]".toRegex(), "") }
                .filter { it.length > 2 && it !in stopWords }

            if (spokenWords.size >= 8 && slideWords.isNotEmpty()) {
                val matchingCount = spokenWords.count { slideWords.contains(it) }
                val ratio = matchingCount.toFloat() / spokenWords.size

                verbatimWarning = if (ratio > 0.65f) {
                    "⚠️ High overlap with slide bullet points ($matchingCount matching words)! Explain the underlying rationale in your own words rather than reading verbatim."
                } else null
            } else {
                verbatimWarning = null
            }
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
                        Text(presentation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Slide ${currentSlideIndex + 1} of ${slides.size} • ${activeModeState.displayName}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Exit")
                    }
                },
                actions = {
                    Text(
                        text = String.format("%02d:%02d", secondsElapsed / 60, secondsElapsed % 60),
                        style = MaterialTheme.typography.titleSmall,
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
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Mode switcher chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    items(PresentationMode.values()) { m ->
                        FilterChip(
                            selected = activeModeState == m,
                            onClick = { activeModeState = m },
                            label = { Text(m.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // If Live Simulation mode, show camera preview for eye-contact tracking
                if (activeModeState == PresentationMode.LIVE_SIMULATION) {
                    CameraPreviewCard(
                        hasCameraPermission = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .padding(bottom = 8.dp)
                    )
                }

                // Slide Content Card
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
                            Text(
                                text = currentSlide.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val textToRead = if (activeModeState == PresentationMode.TEACHER_QNA) currentSlide.teacherQuestion else currentSlide.keyIdea
                                tts.speak(textToRead)
                            }) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Audio", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (activeModeState == PresentationMode.NO_SCRIPT) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "🔒 No Script Mode: Deliver this slide from memory without looking at bullet points.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else if (activeModeState == PresentationMode.TELEPROMPTER) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "PROMPTER SCRIPT:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${currentSlide.keyIdea}. Specifically, ${currentSlide.points.joinToString("; ")}. ${currentSlide.transition}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else if (activeModeState == PresentationMode.TEACHER_QNA) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Teacher / Examiner Question:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"${currentSlide.teacherQuestion}\"",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else {
                            // Guided or Bullets mode
                            Text(
                                text = "Core Concept to Explain: ${currentSlide.keyIdea}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            currentSlide.points.forEach { point ->
                                Text(
                                    text = "• $point",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }
                            if (activeModeState == PresentationMode.GUIDED) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Transition to Next Slide: \"${currentSlide.transition}\"",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Verbatim warning pill
                verbatimWarning?.let { warning ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD97706),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Spoken Transcript Card
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
                            text = "Speaker Transcript",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (transcript.isBlank()) "Press the microphone below and deliver your explanation..." else transcript,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (transcript.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Slide Navigation and Audio Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Slide navigation buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (currentSlideIndex > 0) {
                                currentSlideIndex--
                                speechHelper.reset()
                            }
                        },
                        enabled = currentSlideIndex > 0
                    ) {
                        Icon(Icons.Default.NavigateBefore, contentDescription = null)
                        Text("Prev Slide")
                    }

                    Text("${currentSlideIndex + 1} / ${slides.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    TextButton(
                        onClick = {
                            if (currentSlideIndex < slides.size - 1) {
                                currentSlideIndex++
                                speechHelper.reset()
                            }
                        },
                        enabled = currentSlideIndex < slides.size - 1
                    ) {
                        Text("Next Slide")
                        Icon(Icons.Default.NavigateNext, contentDescription = null)
                    }
                }

                AudioWaveformVisualizer(isListening = isListening, rmsDb = rmsDb)

                Spacer(modifier = Modifier.height(6.dp))

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
                                val slideText = "${currentSlide.title} ${currentSlide.points.joinToString(" ")}"

                                val result = orchestrator.analyzeSession(
                                    domain = if (activeModeState == PresentationMode.TEACHER_QNA) CoachingDomain.TEACHER_QNA else CoachingDomain.PRESENTATION,
                                    prompt = "Presentation: ${presentation.title}. Slide: ${currentSlide.title}. Slide Content: $slideText",
                                    transcript = transcript,
                                    wpm = metrics.wpm,
                                    pauseCount = metrics.pauseCount,
                                    fillerCount = metrics.fillerCount,
                                    repetitionCount = metrics.repetitionCount,
                                    slideText = slideText,
                                    queryTopic = presentation.title
                                )

                                val record = PracticeSession(
                                    sessionType = "PRESENTATION",
                                    title = "${presentation.title} - ${currentSlide.title}",
                                    durationSeconds = secondsElapsed.coerceAtLeast(10),
                                    transcript = transcript,
                                    wpm = metrics.wpm,
                                    pauseCount = metrics.pauseCount,
                                    fillerCount = metrics.fillerCount,
                                    overallScore = result.response.presentation_score,
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
                        Text("Evaluate")
                    }
                }
            }
        }
    }

    if (isAnalyzing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Presentation Coach Evaluating...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking slide explanation depth, conceptual coverage, and vocal delivery...")
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

private fun parseSlides(json: String): List<SlideItem> {
    val list = mutableListOf<SlideItem>()
    try {
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val points = mutableListOf<String>()
            val pointsArr = obj.optJSONArray("points")
            if (pointsArr != null) {
                for (p in 0 until pointsArr.length()) points.add(pointsArr.getString(p))
            }
            val diffWords = mutableListOf<String>()
            val diffArr = obj.optJSONArray("difficultWords")
            if (diffArr != null) {
                for (d in 0 until diffArr.length()) diffWords.add(diffArr.getString(d))
            }
            list.add(
                SlideItem(
                    slideNumber = obj.optInt("slideNumber", i + 1),
                    title = obj.optString("title", "Slide ${i + 1}"),
                    keyIdea = obj.optString("keyIdea", ""),
                    points = points,
                    transition = obj.optString("transition", ""),
                    difficultWords = diffWords,
                    teacherQuestion = obj.optString("teacherQuestion", "Why did you choose this technical approach?")
                )
            )
        }
    } catch (_: Exception) {}
    return if (list.isEmpty()) {
        listOf(
            SlideItem(1, "Overview", "Explain the high-level architecture", listOf("Microservices", "API Gateway"), "Next slide", listOf("Architecture"), "Why microservices?")
        )
    } else list
}

fun parseImportedPresentationFile(context: Context, uri: Uri): Presentation? {
    val contentResolver = context.contentResolver
    var fileName = "Imported Presentation"
    try {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
    } catch (_: Exception) {}

    val lowerName = fileName.lowercase()
    val mime = contentResolver.getType(uri)?.lowercase() ?: ""
    val isPdf = lowerName.endsWith(".pdf") || mime.contains("pdf")
    val isJson = lowerName.endsWith(".json") || mime.contains("json")
    val isTxt = lowerName.endsWith(".txt") || lowerName.endsWith(".md") || mime.contains("text")

    if (isPdf) {
        val pfd = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Could not access PDF file")
        pfd.use {
            val pdfRenderer = android.graphics.pdf.PdfRenderer(it)
            val pageCount = pdfRenderer.pageCount.coerceIn(1, 30)
            pdfRenderer.close()
            val slidesArray = JSONArray()
            val cleanTitle = fileName.substringBeforeLast(".").replace("_", " ").replace("-", " ")
            for (page in 1..pageCount) {
                slidesArray.put(JSONObject().apply {
                    put("slideNumber", page)
                    put("title", "Slide $page: $cleanTitle")
                    put("keyIdea", "Explain the core findings, architecture, and reasoning on slide $page.")
                    put("points", JSONArray().apply {
                        put("Overview of Slide $page")
                        put("Technical Details & Methodology")
                        put("Key Takeaways & Implications")
                    })
                    put("transition", if (page < pageCount) "Moving forward to slide ${page + 1}." else "That concludes the deck. I welcome any questions.")
                    put("difficultWords", JSONArray().apply { put("Methodology"); put("Architecture") })
                    put("teacherQuestion", "What is the primary contribution or engineering trade-off on this slide?")
                })
            }
            return Presentation(
                title = cleanTitle.ifBlank { "PDF Presentation Deck" },
                topic = "Slide Presentation",
                targetAudience = "Faculty & Review Panel",
                totalSlides = pageCount,
                slidesJson = slidesArray.toString()
            )
        }
    } else if (isJson) {
        val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Empty JSON file")
        val root = JSONObject(raw)
        val title = root.optString("title", fileName.substringBeforeLast("."))
        val topic = root.optString("topic", "Technical Presentation")
        val targetAudience = root.optString("targetAudience", "Technical Committee")
        val slidesArray = root.optJSONArray("slides") ?: JSONArray()
        if (slidesArray.length() == 0) {
            throw IllegalArgumentException("JSON must include a non-empty 'slides' array")
        }
        return Presentation(
            title = title,
            topic = topic,
            targetAudience = targetAudience,
            totalSlides = slidesArray.length(),
            slidesJson = slidesArray.toString()
        )
    } else if (isTxt) {
        val lines = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }
            ?: throw IllegalStateException("Empty text file")
        val nonBlank = lines.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) throw IllegalArgumentException("Text file contains no readable content")
        val title = nonBlank.first().removePrefix("#").trim()
        val slidesArray = JSONArray()
        val chunks = nonBlank.drop(1).chunked(3)
        if (chunks.isEmpty()) {
            slidesArray.put(JSONObject().apply {
                put("slideNumber", 1)
                put("title", title)
                put("keyIdea", "Explain the core thesis and problem statement.")
                put("points", JSONArray().apply { put("Context & Motivation"); put("Methodology"); put("Conclusion") })
                put("transition", "Thank you for listening.")
                put("difficultWords", JSONArray().apply { put("System") })
                put("teacherQuestion", "Why did you choose this architecture?")
            })
        } else {
            chunks.forEachIndexed { idx, chunk ->
                slidesArray.put(JSONObject().apply {
                    put("slideNumber", idx + 1)
                    put("title", "Slide ${idx + 1}: ${chunk.first().take(35)}")
                    put("keyIdea", chunk.getOrNull(1) ?: "Explain this section clearly in your own words.")
                    put("points", JSONArray().apply { chunk.forEach { put(it.take(60)) } })
                    put("transition", "Now transitioning to the next aspect.")
                    put("difficultWords", JSONArray().apply { put("Engineering") })
                    put("teacherQuestion", "How does this compare to alternative solutions?")
                })
            }
        }
        return Presentation(
            title = title.take(40).ifBlank { "Text Slide Deck" },
            topic = "Document Notes",
            targetAudience = "Technical Panel",
            totalSlides = slidesArray.length(),
            slidesJson = slidesArray.toString()
        )
    } else {
        throw IllegalArgumentException("Unsupported file type ($fileName). Only PDF, TXT, and JSON slide decks are supported.")
    }
}
