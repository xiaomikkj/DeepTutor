package com.deeptutor.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Teal,
    background = LightBg,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = IndigoDark,
    secondary = Teal,
    background = DarkBg,
)

@Composable
fun DeepTutorTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, content = content)
}