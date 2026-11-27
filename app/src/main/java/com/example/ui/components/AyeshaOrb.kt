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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * Caches shaders/brushes inside drawWithCache so 60fps animation runs with zero per-frame GC churn
 * even on 1 GB / 2 GB RAM devices.
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

    val auraColors = remember(primary, stateAccent) {
        listOf(stateAccent.copy(alpha = 0.42f), primary.copy(alpha = 0.16f), Color.Transparent)
    }
    val sweepColors1 = remember(primary, stateAccent) {
        listOf(Color.Transparent, primary, stateAccent, Color.Transparent)
    }
    val sweepColors2 = remember(secondary) {
        listOf(Color.Transparent, secondary, TealSuccess, Color.Transparent)
    }
    val sphereColors = remember(primary, stateAccent) {
        listOf(
            Color.White.copy(alpha = 0.92f),
            stateAccent.copy(alpha = 0.92f),
            primary.copy(alpha = 0.88f),
            Color(0xFF1A1040)
        )
    }
    val rimColor = remember { Color.White.copy(alpha = 0.28f) }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val minDim = this.size.minDimension
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val outerRadius = minDim * 0.50f
                    val auraBrush = Brush.radialGradient(
                        colors = auraColors,
                        center = center,
                        radius = outerRadius
                    )
                    val sweepBrush1 = Brush.sweepGradient(colors = sweepColors1, center = center)
                    val sweepBrush2 = Brush.sweepGradient(colors = sweepColors2, center = center)
                    val sphereBrush = Brush.radialGradient(
                        colors = sphereColors,
                        center = Offset(center.x - minDim * 0.08f, center.y - minDim * 0.08f),
                        radius = minDim * 0.36f
                    )
                    val pulseStroke = Stroke(width = 3.dp.toPx())
                    val arcStroke1 = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    val arcStroke2 = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    val rimStroke = Stroke(width = 1.5.dp.toPx())
                    val sweepAngle1 = if (state == OrbVisualState.THINKING) 240f else 170f

                    onDrawBehind {
                        val ampBoost = 1f + smoothAmp * 0.18f
                        val coreRadius = (minDim * 0.30f) * breath * ampBoost

                        drawCircle(
                            brush = auraBrush,
                            radius = outerRadius,
                            center = center
                        )

                        if (state == OrbVisualState.LISTENING || state == OrbVisualState.SPEAKING) {
                            val pulseRadius = coreRadius * (1.22f + smoothAmp * 0.28f)
                            drawCircle(
                                color = stateAccent.copy(alpha = 0.28f + smoothAmp * 0.45f),
                                radius = pulseRadius,
                                center = center,
                                style = pulseStroke
                            )
                        }

                        val arc1Radius = coreRadius * 1.16f
                        val arc2Radius = coreRadius * 1.07f
                        rotate(degrees = rotationAngle, pivot = center) {
                            drawArc(
                                brush = sweepBrush1,
                                startAngle = 0f,
                                sweepAngle = sweepAngle1,
                                useCenter = false,
                                topLeft = Offset(center.x - arc1Radius, center.y - arc1Radius),
                                size = Size(arc1Radius * 2f, arc1Radius * 2f),
                                style = arcStroke1
                            )
                        }

                        rotate(degrees = -rotationAngle * 0.7f, pivot = center) {
                            drawArc(
                                brush = sweepBrush2,
                                startAngle = 90f,
                                sweepAngle = 140f,
                                useCenter = false,
                                topLeft = Offset(center.x - arc2Radius, center.y - arc2Radius),
                                size = Size(arc2Radius * 2f, arc2Radius * 2f),
                                style = arcStroke2
                            )
                        }

                        drawCircle(
                            brush = sphereBrush,
                            radius = coreRadius,
                            center = center
                        )

                        drawCircle(
                            color = rimColor,
                            radius = coreRadius,
                            center = center,
                            style = rimStroke
                        )
                    }
                }
        )
    }
}
