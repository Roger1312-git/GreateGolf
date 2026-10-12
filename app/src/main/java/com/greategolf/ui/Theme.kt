package com.greategolf.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF2E7D32)
private val Light = lightColorScheme(
    primary = Green, onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9), onPrimaryContainer = Color(0xFF0B3D10),
    secondary = Color(0xFF6D4C41), tertiary = Color(0xFFF9A825),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF81C784), onPrimary = Color(0xFF0B3D10),
    primaryContainer = Color(0xFF1B5E20), onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFFBCAAA4), tertiary = Color(0xFFFFCA28),
)

@Composable
fun GolfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
