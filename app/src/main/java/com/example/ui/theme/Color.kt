package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState

val Black: Color
    @Composable get() = if (com.example.AppPreferences.isDarkTheme.collectAsState().value) Color(0xFF000000) else Color(0xFFF5F5F7)

val DarkSurface: Color
    @Composable get() = if (com.example.AppPreferences.isDarkTheme.collectAsState().value) Color(0xFF0E0E0E) else Color(0xFFFFFFFF)

val LightSurface: Color
    @Composable get() = if (com.example.AppPreferences.isDarkTheme.collectAsState().value) Color(0xFF1A1A1A) else Color(0xFFE0E0E0)

val White: Color
    @Composable get() = if (com.example.AppPreferences.isDarkTheme.collectAsState().value) Color(0xFFFFFFFF) else Color(0xFF000000)

val DimText: Color
    @Composable get() = if (com.example.AppPreferences.isDarkTheme.collectAsState().value) Color(0xFF777777) else Color(0xFF666666)

val ErrorRed = Color(0xFFFF3B30)
val SuccessGreen = Color(0xFF34C759)

