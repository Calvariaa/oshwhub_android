package io.github.Calvariaa.oshwhub.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// oshwhub 品牌绿
val BrandGreen = Color(0xFF00A870)
val BrandGreenDark = Color(0xFF00754E)
val BrandBlue = Color(0xFF1E6FFF)
val BrandOrange = Color(0xFFF5533D)

val LightColors = lightColorScheme(
    primary = Color(0xFF006D4B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8CF8C2),
    onPrimaryContainer = Color(0xFF002115),
    secondary = Color(0xFF4D6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF0A1F16),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8F3),
    surfaceContainer = Color(0xFFEFF2ED),
    surfaceContainerHigh = Color(0xFFE9ECE7),
    surfaceContainerHighest = Color(0xFFE3E6E1),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF70DBAB),
    onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005138),
    onPrimaryContainer = Color(0xFF8CF8C2),
    secondary = Color(0xFFB3CCBE),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B40),
    onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = Color(0xFFA5CCDF),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244C5B),
    onTertiaryContainer = Color(0xFFC1E8FB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0C0F0D),
    surfaceContainerLow = Color(0xFF191C1A),
    surfaceContainer = Color(0xFF1D201E),
    surfaceContainerHigh = Color(0xFF272B28),
    surfaceContainerHighest = Color(0xFF323533),
)

val PureBlackDarkColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF171717),
    surfaceContainerHighest = Color(0xFF1F1F1F),
)
