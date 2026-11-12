package com.amandhakar.appstore

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours the Material scheme has no slot for: "up to date" green and the amber of the pre-release tag and banner. */
@Immutable
class StoreColors(val good: Color, val warnBackground: Color, val warnText: Color)

private val LightStoreColors = StoreColors(good = Color(0xFF1B7A4B), warnBackground = Color(0xFFFDEBD3), warnText = Color(0xFF8A4B00))
private val DarkStoreColors = StoreColors(good = Color(0xFF5FD39A), warnBackground = Color(0xFF3A2A10), warnText = Color(0xFFFFC77A))

val LocalStoreColors = staticCompositionLocalOf { LightStoreColors }

val storeColors: StoreColors
    @Composable get() = LocalStoreColors.current

// One fixed colour scheme, so the store looks the same on every phone instead of taking its colours from the wallpaper.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1D5FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3ECFE),
    onPrimaryContainer = Color(0xFF0E3A8F),
    secondary = Color(0xFF1D5FD6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3ECFE),
    onSecondaryContainer = Color(0xFF0E3A8F),
    background = Color(0xFFF6F5F1),
    onBackground = Color(0xFF15171C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF15171C),
    surfaceVariant = Color(0xFFEFEEE9),
    onSurfaceVariant = Color(0xFF5A606C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEFEEE9),
    outline = Color(0xFF8A909C),
    outlineVariant = Color(0xFFE6E4DD),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EB6FF),
    onPrimary = Color(0xFF0A2A66),
    primaryContainer = Color(0xFF1D2F57),
    onPrimaryContainer = Color(0xFFC9DBFF),
    secondary = Color(0xFF8EB6FF),
    onSecondary = Color(0xFF0A2A66),
    secondaryContainer = Color(0xFF1D2F57),
    onSecondaryContainer = Color(0xFFC9DBFF),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF171A20),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF20242C),
    onSurfaceVariant = Color(0xFFA6ADBA),
    surfaceContainerLowest = Color(0xFF0E1014),
    surfaceContainerLow = Color(0xFF171A20),
    surfaceContainer = Color(0xFF171A20),
    surfaceContainerHigh = Color(0xFF171A20),
    surfaceContainerHighest = Color(0xFF20242C),
    outline = Color(0xFF6E7685),
    outlineVariant = Color(0xFF2B303A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun StoreTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors) {
        CompositionLocalProvider(LocalStoreColors provides if (dark) DarkStoreColors else LightStoreColors, content = content)
    }
}
