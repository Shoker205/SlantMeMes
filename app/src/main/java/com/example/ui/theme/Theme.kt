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

private val DarkColorScheme =
  darkColorScheme(
      primary = White, 
      secondary = DimText, 
      tertiary = ErrorRed,
      background = Black,
      surface = DarkSurface,
      onPrimary = Black,
      onSecondary = White,
      onTertiary = White,
      onBackground = White,
      onSurface = White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Black,
    secondary = DimText,
    tertiary = ErrorRed,
    background = White,
    surface = Color(0xFFF5F5F5),
    onPrimary = White,
    onSecondary = Black,
    onTertiary = White,
    onBackground = Black,
    onSurface = Black
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
