package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.live.LiveCallStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.TealSuccess
import kotlin.math.cos
import kotlin.math.sin

enum class OrbVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING;

    companion object {
        fun fromCallStatus(status: LiveCallStatus): OrbVisualState = when (status) {
            LiveCallStatus.IDLE, LiveCallStatus.ERROR -> IDLE
            LiveCallStatus.CONNECTING, LiveCallStatus.RECONNECTING, LiveCallStatus.THINKING -> THINKING
            LiveCallStatus.LISTENING -> LISTENING
            LiveCallStatus.SPEAKING -> SPEAKING
        }
    }
}

/**
 * Ayesha's signature animated glowing orb with distinct visual behaviors
 * for IDLE, LISTENING, THINKING, and SPEAKING, reacting dynamically to live audio amplitude.
 */
@Composable
fun AyeshaOrb(
    state: OrbVisualState,
    amplitude: Float = 0f,
    size: Dp = 210.dp,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    val infinite = rememberInfiniteTransition(label = "orb_transition")

    val rotationAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbVisualState.THINKING -> 2200
                    OrbVisualState.SPEAKING -> 4000
                    OrbVisualState.LISTENING -> 6500
                    OrbVisualState.IDLE -> 11000
                },
                easing = LinearEasing
            )
        ),
        label = "orb_rotation"
    )

    val breath by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    OrbVisualState.SPEAKING -> 650
                    OrbVisualState.THINKING -> 900
                    OrbVisualState.LISTENING -> 1400
                    OrbVisualState.IDLE -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breath"
    )

    val smoothAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 380f),
        label = "orb_amplitude"
    )

    val stateAccent = when (state) {
        OrbVisualState.IDLE -> primary
        OrbVisualState.LISTENING -> TealSuccess
        OrbVisualState.THINKING -> AmberWarning
        OrbVisualState.SPEAKING -> secondary
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = this.size.minDimension
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val ampBoost = 1f + smoothAmp * 0.18f
            val coreRadius = (minDim * 0.30f) * breath * ampBoost

            // Soft outer aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        stateAccent.copy(alpha = 0.38f + smoothAmp * 0.25f),
                        primary.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = minDim * 0.50f
                ),
                radius = minDim * 0.50f,
                center = center
            )

            // Reactive pulse ring when listening or speaking
            if (state == OrbVisualState.LISTENING || state == OrbVisualState.SPEAKING) {
                val pulseRadius = coreRadius * (1.22f + smoothAmp * 0.28f)
                drawCircle(
                    color = stateAccent.copy(alpha = 0.28f + smoothAmp * 0.45f),
                    radius = pulseRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Rotating glowing orbital arcs
            rotate(degrees = rotationAngle, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            primary,
                            stateAccent,
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = 0f,
                    sweepAngle = if (state == OrbVisualState.THINKING) 240f else 170f,
                    useCenter = false,
                    topLeft = Offset(center.x - coreRadius * 1.16f, center.y - coreRadius * 1.16f),
                    size = androidx.compose.ui.geometry.Size(coreRadius * 2.32f, coreRadius * 2.32f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            rotate(degrees = -rotationAngle * 0.7f, pivot = center) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            secondary,
                            TealSuccess,
                            Color.Transparent
                        ),
                        center = center
                    ),
                    startAngle = 90f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(center.x - coreRadius * 1.07f, center.y - coreRadius * 1.07f),
                    size = androidx.compose.ui.geometry.Size(coreRadius * 2.14f, coreRadius * 2.14f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Inner glowing sphere
            val rad = Math.toRadians(rotationAngle.toDouble())
            val highlightOffset = Offset(
                center.x + (cos(rad) * coreRadius * 0.28f).toFloat(),
                center.y + (sin(rad) * coreRadius * 0.28f).toFloat()
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        stateAccent.copy(alpha = 0.92f),
                        primary.copy(alpha = 0.88f),
                        Color(0xFF1A1040)
                    ),
                    center = highlightOffset,
                    radius = coreRadius * 1.2f
                ),
                radius = coreRadius,
                center = center
            )

            // Subtle glass rim
            drawCircle(
                color = Color.White.copy(alpha = 0.28f),
                radius = coreRadius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
