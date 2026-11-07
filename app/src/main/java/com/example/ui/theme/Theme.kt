package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ExpressiveDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = DeepNavyBg,
    primaryContainer = VividTeal.copy(alpha = 0.25f),
    onPrimaryContainer = ElectricCyan,
    secondary = PrimaryLightBlue,
    onSecondary = DeepNavyBg,
    secondaryContainer = SurfaceContainerHigh,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentPurple,
    onTertiary = Color.White,
    background = DeepNavyBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceDarkVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = SurfaceContainerHigh,
    error = LiveRed,
    onError = Color.White
)

private val ExpressiveLightColorScheme = lightColorScheme(
    primary = VividTeal,
    onPrimary = Color.White,
    primaryContainer = ElectricCyan.copy(alpha = 0.2f),
    onPrimaryContainer = DeepNavyBg,
    secondary = PrimaryBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F2FD),
    onSecondaryContainer = Color(0xFF0D47A1),
    tertiary = AccentPurple,
    onTertiary = Color.White,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF101418),
    surface = Color.White,
    onSurface = Color(0xFF101418),
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = LiveRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to dark for premium video player vibe
    dynamicColor: Boolean = false, // Keep expressive palette consistent
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressiveDarkColorScheme
        else -> ExpressiveLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
