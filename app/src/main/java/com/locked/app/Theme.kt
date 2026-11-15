package com.locked.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1A5),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF124A3A),
    onPrimaryContainer = Color(0xFFC8F5E6),
    secondary = Color(0xFF8B7CFF),
    onSecondary = Color(0xFF120A3A),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE8EAF0),
    surface = Color(0xFF141923),
    onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF1C2330),
    onSurfaceVariant = Color(0xFF9AA3B5),
    outline = Color(0xFF2A3342),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E8F6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5EA),
    onPrimaryContainer = Color(0xFF003D2C),
    secondary = Color(0xFF5B4BDB),
    onSecondary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF12151C),
    surface = Color.White,
    onSurface = Color(0xFF12151C),
    surfaceVariant = Color(0xFFEDEFF5),
    onSurfaceVariant = Color(0xFF5A6377),
    outline = Color(0xFFDADFEA),
    error = Color(0xFFD93B3B),
)

@Composable
fun LockedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
