package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = OnTerracottaPrimary,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = ArenGoldSecondary,
    onSecondary = OnArenGoldSecondary,
    secondaryContainer = ArenGoldContainer,
    onSecondaryContainer = OnArenGoldContainer,
    tertiary = PandanTertiary,
    onTertiary = OnPandanTertiary,
    tertiaryContainer = PandanContainer,
    onTertiaryContainer = OnPandanContainer,
    background = WarmCreamBackground,
    onBackground = EspressoOnBackground,
    surface = WarmSurface,
    onSurface = EspressoOnSurface,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = EspressoOnSurfaceVariant,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer
)

private val DarkColors = darkColorScheme(
    primary = DarkTerracottaPrimary,
    onPrimary = DarkOnTerracottaPrimary,
    primaryContainer = DarkTerracottaContainer,
    onPrimaryContainer = DarkOnTerracottaContainer,
    secondary = DarkArenGoldSecondary,
    onSecondary = DarkOnArenGoldSecondary,
    secondaryContainer = DarkArenGoldContainer,
    onSecondaryContainer = DarkOnArenGoldContainer,
    tertiary = DarkPandanTertiary,
    onTertiary = DarkOnPandanTertiary,
    tertiaryContainer = DarkPandanContainer,
    onTertiaryContainer = DarkOnPandanContainer,
    background = DarkEspressoBackground,
    onBackground = DarkCreamOnBackground,
    surface = DarkEspressoSurface,
    onSurface = DarkCreamOnSurface,
    surfaceVariant = DarkEspressoSurfaceVariant,
    onSurfaceVariant = DarkWarmOnSurfaceVariant,
    outline = DarkWarmOutline,
    outlineVariant = DarkWarmOutlineVariant,
    error = ErrorContainer,
    onError = OnErrorContainer
)

val KedaiShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun KedaiAaSulthanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KedaiTypography,
        shapes = KedaiShapes,
        content = content
    )
}
