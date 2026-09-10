package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = OratorDarkPrimary,
    onPrimary = OratorDarkOnPrimary,
    primaryContainer = OratorDarkPrimaryContainer,
    onPrimaryContainer = OratorDarkOnPrimaryContainer,
    secondary = OratorDarkSecondary,
    onSecondary = OratorDarkOnSecondary,
    secondaryContainer = OratorDarkSecondaryContainer,
    onSecondaryContainer = OratorDarkOnSecondaryContainer,
    tertiary = OratorDarkTertiary,
    onTertiary = OratorDarkOnTertiary,
    tertiaryContainer = OratorDarkTertiaryContainer,
    onTertiaryContainer = OratorDarkOnTertiaryContainer,
    background = OratorDarkBackground,
    onBackground = OratorDarkOnBackground,
    surface = OratorDarkSurface,
    onSurface = OratorDarkOnSurface,
    surfaceVariant = OratorDarkSurfaceVariant,
    onSurfaceVariant = OratorDarkOnSurfaceVariant,
    outline = OratorDarkOutline,
    outlineVariant = OratorDarkOutlineVariant,
    error = OratorError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = OratorPrimary,
    onPrimary = OratorOnPrimary,
    primaryContainer = OratorPrimaryContainer,
    onPrimaryContainer = OratorOnPrimaryContainer,
    secondary = OratorSecondary,
    onSecondary = OratorOnSecondary,
    secondaryContainer = OratorSecondaryContainer,
    onSecondaryContainer = OratorOnSecondaryContainer,
    tertiary = OratorTertiary,
    onTertiary = OratorOnTertiary,
    tertiaryContainer = OratorTertiaryContainer,
    onTertiaryContainer = OratorOnTertiaryContainer,
    background = OratorBackground,
    onBackground = OratorOnBackground,
    surface = OratorSurface,
    onSurface = OratorOnSurface,
    surfaceVariant = OratorSurfaceVariant,
    onSurfaceVariant = OratorOnSurfaceVariant,
    outline = OratorOutline,
    outlineVariant = OratorOutlineVariant,
    error = OratorError,
    onError = Color.White
)

val OratorShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// Design Tokens for consistent craftsmanship across all screens
object OratorDesignTokens {
    val CardCornerRadius = 18.dp
    val DialogCornerRadius = 22.dp
    val ButtonCornerRadius = 14.dp
    val InputCornerRadius = 12.dp
    val ChipCornerRadius = 10.dp
    val CardElevation = 0.dp
    val BorderWidth = 1.dp
    val IconBadgeSize = 44.dp
    val TouchTargetMin = 48.dp
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep false to maintain our refined, tailored Orator aesthetic
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = OratorShapes,
        content = content
    )
}

