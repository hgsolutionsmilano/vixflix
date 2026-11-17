package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = VixFlixColorScheme,
        typography = Typography,
        content = content
    )
}
