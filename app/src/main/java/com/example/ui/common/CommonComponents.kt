package com.example.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AIResult
import com.example.ui.theme.OratorDesignTokens

@Composable
fun ConfidenceRatingDialog(
    title: String = "How confident do you feel?",
    subtitle: String = "Self-reported speaking confidence check",
    initialRating: Int = 3,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRating by remember { mutableStateOf(initialRating) }
    val labels = listOf("1 - Very Anxious", "2 - Hesitant", "3 - Neutral / Steady", "4 - Confident", "5 - Fully In Command")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(OratorDesignTokens.DialogCornerRadius),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { selectedRating = star },
                            modifier = Modifier.size(OratorDesignTokens.TouchTargetMin)
                        ) {
                            Icon(
                                imageVector = if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Rate $star",
                                tint = if (star <= selectedRating) Color(0xFFF59E0B) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = labels.getOrElse(selectedRating - 1) { "" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedRating) },
                shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                modifier = Modifier.testTag("confirm_confidence_button")
            ) {
                Text("Confirm", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius)
            ) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun AudioWaveformVisualizer(
    isListening: Boolean,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val barsCount = 18
        val infiniteTransition = rememberInfiniteTransition(label = "waveform")

        for (i in 0 until barsCount) {
            val animFraction by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 350 + (i * 35), easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            val barHeight = if (isListening) {
                val dbFactor = (rmsDb / 10f).coerceIn(0.15f, 1.0f)
                (10.dp + (34.dp * animFraction * dbFactor))
            } else {
                6.dp
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(4.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(
                        if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
fun MetricBadge(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(OratorDesignTokens.ChipCornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SessionReportDialog(
    aiResult: AIResult,
    wpm: Int,
    durationSeconds: Int,
    fillerCount: Int,
    pauseCount: Int,
    onDismiss: () -> Unit
) {
    val resp = aiResult.response

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(OratorDesignTokens.DialogCornerRadius),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Session Coaching Report",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Provider Badge & Score
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (aiResult.isOffline) Icons.Default.CloudOff else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Brain: ${aiResult.providerUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Score: ${resp.fluency_score}/100",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBadge(
                        icon = Icons.Default.Speed,
                        label = "Speed",
                        value = "$wpm WPM",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBadge(
                        icon = Icons.Default.Timer,
                        label = "Duration",
                        value = "${durationSeconds}s",
                        modifier = Modifier.weight(1f)
                    )
                    MetricBadge(
                        icon = Icons.Default.RecordVoiceOver,
                        label = "Fillers",
                        value = "$fillerCount",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Feedback summary
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "AI Coach Feedback",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = resp.feedback,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Strengths
                if (resp.strengths.isNotEmpty()) {
                    Text(
                        text = "Observed Strengths:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    resp.strengths.take(2).forEach { s ->
                        Text(
                            text = "• $s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Weaknesses / Growth areas
                if (resp.weaknesses.isNotEmpty()) {
                    Text(
                        text = "Focus for Improvement:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    resp.weaknesses.take(2).forEach { w ->
                        Text(
                            text = "• $w",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Recommended next exercise
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Next Drill: ${resp.suggested_exercise}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(OratorDesignTokens.ButtonCornerRadius),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_dismiss_button")
            ) {
                Text("Done & Save", fontWeight = FontWeight.Bold)
            }
        }
    )
}

