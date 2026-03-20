package com.vizoguard.vpn.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFFF6B2B)
val Teal = Color(0xFF00E5A0)
val Surface = Color(0xFF111111)
val Background = Color(0xFF000000)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF999999)
val Red = Color(0xFFFF3B3B)
val Border = Color(0xFF222222)

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    secondary = Teal,
    background = Background,
    surface = Surface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = Red,
)

@Composable
fun VizoguardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
