package com.example.ui.theme

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val VixFlixColorScheme = darkColorScheme(
    primary = NetflixRed,
    onPrimary = Color.White,
    primaryContainer = NetflixRedDark,
    onPrimaryContainer = Color.White,
    secondary = NetflixWhite,
    onSecondary = NetflixBlack,
    secondaryContainer = NetflixElevatedSurface,
    onSecondaryContainer = NetflixWhite,
    tertiary = NetflixMatchGreen,
    onTertiary = NetflixBlack,
    background = NetflixBlack,
    onBackground = NetflixWhite,
    surface = NetflixDarkSurface,
    onSurface = NetflixWhite,
    surfaceVariant = NetflixCardSurface,
    onSurfaceVariant = NetflixLightGray,
    outline = NetflixMutedGray
)

@OptIn(ExperimentalFoundationApi::class)
val StandardBringIntoViewSpec: BringIntoViewSpec = object : BringIntoViewSpec {}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    // Disattiva PivotBringIntoViewSpec (attivo di default su Fire TV Stick / Android TV con FEATURE_LEANBACK)
    // uniformando al 100% il comportamento di focus/scroll tra Fire Stick, Android TV, Google TV e Tablet.
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides StandardBringIntoViewSpec
    ) {
        MaterialTheme(
            colorScheme = VixFlixColorScheme,
            typography = Typography,
            content = content
        )
    }
}

