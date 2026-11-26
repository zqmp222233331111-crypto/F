package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.AccentTheme

private val AyeshaShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AyeshaTheme(
    accentTheme: AccentTheme = AccentTheme.VIOLET_PINK,
    content: @Composable () -> Unit
) {
    val (primaryAccent, secondaryAccent) = when (accentTheme) {
        AccentTheme.VIOLET_PINK -> VioletPink to PinkHighlight
        AccentTheme.TEAL_AURORA -> TealSuccess to Color(0xFF38BDF8)
        AccentTheme.AMBER_GLOW -> AmberWarning to Color(0xFFFB923C)
        AccentTheme.ROSE_BERRY -> Color(0xFFFB7185) to VioletPink
    }

    val colorScheme = darkColorScheme(
        primary = primaryAccent,
        onPrimary = Color(0xFF0A0D1C),
        primaryContainer = primaryAccent.copy(alpha = 0.22f),
        onPrimaryContainer = TextPrimary,
        secondary = secondaryAccent,
        onSecondary = Color(0xFF0A0D1C),
        secondaryContainer = secondaryAccent.copy(alpha = 0.18f),
        onSecondaryContainer = TextPrimary,
        tertiary = TealSuccess,
        onTertiary = Color(0xFF052E2B),
        background = NavyDeep,
        onBackground = TextPrimary,
        surface = NavySurface,
        onSurface = TextPrimary,
        surfaceVariant = NavySurfaceHigh,
        onSurfaceVariant = TextSecondary,
        outline = NavyBorder,
        error = RoseDanger,
        onError = Color.White
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AyeshaTypography,
        shapes = AyeshaShapes
    ) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary) {
            content()
        }
    }
}
