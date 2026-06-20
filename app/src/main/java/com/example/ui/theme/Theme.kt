package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Indigo500,
    secondary = Cyan400,
    tertiary = Emerald400,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = Slate100,
    onSurface = Slate100,
    onPrimary = Color.White
  )

private val LightColorScheme = DarkColorScheme // We only have a dark sleek design

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for the sleek look
  dynamicColor: Boolean = false, // Disable dynamic to preserve design
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
