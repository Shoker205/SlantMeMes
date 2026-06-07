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
      primary = Color(0xFFFFFFFF), 
      secondary = Color(0xFF777777), 
      tertiary = Color(0xFFFF3B30),
      background = Color(0xFF000000),
      surface = Color(0xFF0E0E0E),
      onPrimary = Color(0xFF000000),
      onSecondary = Color(0xFFFFFFFF),
      onTertiary = Color(0xFFFFFFFF),
      onBackground = Color(0xFFFFFFFF),
      onSurface = Color(0xFFFFFFFF)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF000000),
    secondary = Color(0xFF777777),
    tertiary = Color(0xFFFF3B30),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000)
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
