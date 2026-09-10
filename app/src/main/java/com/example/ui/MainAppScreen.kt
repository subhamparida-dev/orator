package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.AIOrchestrator
import com.example.data.AppDatabase
import com.example.data.StudentRepository
import com.example.ui.english.EnglishLabScreen
import com.example.ui.fluency.FluencyGymScreen
import com.example.ui.home.HomeScreen
import com.example.ui.interview.InterviewScreen
import com.example.ui.presentation.PresentationStudioScreen
import com.example.ui.progress.ProgressScreen
import com.example.ui.settings.SettingsScreen

enum class AppDestination(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    FLUENCY("Fluency", Icons.Filled.GraphicEq, Icons.Outlined.GraphicEq),
    ENGLISH("English", Icons.Filled.Translate, Icons.Outlined.Translate),
    STUDIO("Studio", Icons.Filled.Slideshow, Icons.Outlined.Slideshow),
    INTERVIEW("Interview", Icons.Filled.RecordVoiceOver, Icons.Outlined.RecordVoiceOver),
    PROGRESS("Analytics", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun MainAppScreen() {
    val context = LocalContext.current
    val database = remember { AppDatabase.getInstance(context) }
    val repository = remember { StudentRepository(database) }
    val orchestrator = remember { AIOrchestrator(repository) }

    var currentDestination by remember { mutableStateOf(AppDestination.HOME) }

    // Runtime permissions for Audio and Camera
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    Scaffold(
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            ) {
                NavigationBar(
                    modifier = Modifier
                        .testTag("main_bottom_nav")
                        .navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    listOf(
                        AppDestination.HOME,
                        AppDestination.FLUENCY,
                        AppDestination.ENGLISH,
                        AppDestination.STUDIO,
                        AppDestination.INTERVIEW,
                        AppDestination.PROGRESS,
                        AppDestination.SETTINGS
                    ).forEach { destination ->
                        val isSelected = currentDestination == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_item_${destination.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(220)) togetherWith
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(180))
                },
                label = "screen_navigation"
            ) { targetDestination ->
                when (targetDestination) {
                    AppDestination.HOME -> HomeScreen(
                        repository = repository,
                        onNavigateToFluency = { currentDestination = AppDestination.FLUENCY },
                        onNavigateToEnglish = { currentDestination = AppDestination.ENGLISH },
                        onNavigateToPresentation = { currentDestination = AppDestination.STUDIO },
                        onNavigateToInterview = { currentDestination = AppDestination.INTERVIEW },
                        onNavigateToProgress = { currentDestination = AppDestination.PROGRESS }
                    )
                    AppDestination.FLUENCY -> FluencyGymScreen(
                        repository = repository,
                        orchestrator = orchestrator
                    )
                    AppDestination.ENGLISH -> EnglishLabScreen(
                        repository = repository,
                        orchestrator = orchestrator
                    )
                    AppDestination.STUDIO -> PresentationStudioScreen(
                        repository = repository,
                        orchestrator = orchestrator
                    )
                    AppDestination.INTERVIEW -> InterviewScreen(
                        repository = repository,
                        orchestrator = orchestrator
                    )
                    AppDestination.PROGRESS -> ProgressScreen(
                        repository = repository
                    )
                    AppDestination.SETTINGS -> SettingsScreen(
                        repository = repository
                    )
                }
            }
        }
    }
}
