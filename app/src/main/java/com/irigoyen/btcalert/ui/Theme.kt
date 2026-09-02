package com.irigoyen.btcalert.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Always-dark, near-monochrome palette with a single Bitcoin-orange accent. */
object Ink {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF121316)
    val SurfaceHigh = Color(0xFF1C1D21)
    val Outline = Color(0xFF2A2B30)
    val White = Color(0xFFFFFFFF)
    val Muted = Color(0xFF8B8F98)
    val Faint = Color(0xFF55585F)
    val Accent = Color(0xFFF7931A)
    val Up = Color(0xFF17D18B)
    val Down = Color(0xFFFF5A5F)
}

private val scheme = darkColorScheme(
    primary = Ink.White,
    onPrimary = Ink.Black,
    primaryContainer = Ink.SurfaceHigh,
    onPrimaryContainer = Ink.White,
    secondary = Ink.Accent,
    onSecondary = Ink.Black,
    secondaryContainer = Ink.SurfaceHigh,
    onSecondaryContainer = Ink.White,
    tertiary = Ink.Accent,
    background = Ink.Black,
    onBackground = Ink.White,
    surface = Ink.Black,
    onSurface = Ink.White,
    surfaceVariant = Ink.Surface,
    onSurfaceVariant = Ink.Muted,
    surfaceContainer = Ink.Surface,
    surfaceContainerHigh = Ink.SurfaceHigh,
    surfaceContainerHighest = Ink.SurfaceHigh,
    surfaceContainerLow = Ink.Surface,
    surfaceContainerLowest = Ink.Black,
    outline = Ink.Outline,
    outlineVariant = Ink.Outline,
    error = Ink.Down,
    onError = Ink.Black,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val type = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-2).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp),
)

@Composable
fun BtcAlertTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, shapes = shapes, typography = type, content = content)
}
