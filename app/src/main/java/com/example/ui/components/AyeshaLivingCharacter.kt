package com.example.ui.components

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.persona.AyeshaEmotion
import com.example.persona.AyeshaOutfit
import kotlinx.coroutines.delay
import kotlin.math.sin

private const val PORTRAIT_SOURCE_W = 540f
private const val PORTRAIT_SOURCE_H = 960f

/**
 * Renders Ayesha's portrait outfit with natural, low-memory 60fps 2D life:
 * - Subtle chest breathing + organic head/upper-body conversational motion
 * - Natural periodic eye blinks locked onto image coordinates
 * - Real-time audio-driven lip-sync synced to speakerAmplitude
 * - Expressive emotion cues (smile/blush, pouting shade, teary eye shimmer & tear)
 */
@Composable
fun AyeshaLivingCharacter(
    outfit: AyeshaOutfit,
    emotion: AyeshaEmotion,
    speakerAmplitude: Float,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "ayesha_living_char")

    val breathPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (emotion) {
                    AyeshaEmotion.HAPPY -> 2100
                    AyeshaEmotion.TEARY -> 3200
                    else -> 2700
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "char_breath"
    )

    val swayPhase by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (emotion) {
                    AyeshaEmotion.HAPPY -> 3400
                    AyeshaEmotion.POUTING -> 5200
                    else -> 4300
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "char_sway"
    )

    val syllableWave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "char_syllable"
    )

    val tearDropProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "char_tear"
    )

    // Periodic natural single + occasional double blink cycle
    var blinkAmount by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var cycle = 0
        while (true) {
            delay(if (cycle % 3 == 0) 2600L else 3700L)
            blinkAmount = 0.65f
            delay(42L)
            blinkAmount = 1.0f
            delay(62L)
            blinkAmount = 0.35f
            delay(40L)
            blinkAmount = 0f
            if (cycle % 4 == 2) {
                delay(160L)
                blinkAmount = 0.9f
                delay(55L)
                blinkAmount = 0f
            }
            cycle++
        }
    }

    val smoothAmp by animateFloatAsState(
        targetValue = if (isSpeaking) speakerAmplitude.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(stiffness = 460f, dampingRatio = 0.72f),
        label = "char_lip_amp"
    )

    val emotionTint = remember(emotion) {
        when (emotion) {
            AyeshaEmotion.HAPPY -> Color(0x16FF9EEB)
            AyeshaEmotion.POUTING -> Color(0x1C392059)
            AyeshaEmotion.TEARY -> Color(0x221F3568)
            AyeshaEmotion.SHY_AFFECTION -> Color(0x22FF73A8)
            AyeshaEmotion.WARM -> Color(0x0EFFB8D9)
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF070410))
    ) {
        Crossfade(
            targetState = outfit,
            animationSpec = tween(durationMillis = 420),
            label = "outfit_crossfade"
        ) { targetOutfit ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val activeAmp = smoothAmp
                        val emotionTiltBias = when (emotion) {
                            AyeshaEmotion.POUTING -> -1.55f
                            AyeshaEmotion.SHY_AFFECTION -> 1.65f
                            AyeshaEmotion.TEARY -> -0.95f
                            AyeshaEmotion.HAPPY -> 0.55f
                            else -> 0f
                        }
                        val proximityBoost = when (emotion) {
                            AyeshaEmotion.SHY_AFFECTION -> 0.016f
                            AyeshaEmotion.WARM -> 0.008f
                            else -> 0f
                        }
                        val swayAmplitudeDeg = if (activeAmp > 0.08f) 1.65f else 0.95f
                        val scaleBase = 1.028f + proximityBoost + breathPhase * 0.015f + activeAmp * 0.013f
                        scaleX = scaleBase
                        scaleY = scaleBase + breathPhase * 0.006f
                        rotationZ = emotionTiltBias + swayPhase * swayAmplitudeDeg
                        translationX = swayPhase * (size.width * 0.009f)
                        translationY = (breathPhase - 0.5f) * (-size.height * 0.006f) - activeAmp * (size.height * 0.004f)
                        transformOrigin = TransformOrigin(0.5f, 0.85f)
                    }
            ) {
                Image(
                    painter = painterResource(id = targetOutfit.drawableRes),
                    contentDescription = "আয়শা (${targetOutfit.labelBn})",
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )

                // Emotion mood veil + facial animation overlay locked to portrait coordinates
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (emotionTint != Color.Transparent) {
                        drawRect(color = emotionTint)
                    }

                    // Exact ContentScale.Crop + Alignment.TopCenter coordinate projection
                    val scale = maxOf(size.width / PORTRAIT_SOURCE_W, size.height / PORTRAIT_SOURCE_H)
                    val scaledW = PORTRAIT_SOURCE_W * scale
                    val scaledH = PORTRAIT_SOURCE_H * scale
                    val offsetX = (size.width - scaledW) * 0.5f
                    val offsetY = 0f // TopCenter keeps y=0 pinned

                    fun mapPoint(nx: Float, ny: Float): Offset =
                        Offset(offsetX + nx * scaledW, offsetY + ny * scaledH)

                    val eyeSpan = (targetOutfit.rightEyeX - targetOutfit.leftEyeX) * scaledW
                    val leftEyePos = mapPoint(targetOutfit.leftEyeX, targetOutfit.eyeCenterY)
                    val rightEyePos = mapPoint(targetOutfit.rightEyeX, targetOutfit.eyeCenterY)
                    val mouthPos = mapPoint(targetOutfit.mouthCenterX, targetOutfit.mouthCenterY)

                    // 1. Rosy cheek blush during HAPPY, SHY_AFFECTION, POUTING, or subtle WARM
                    if (emotion != AyeshaEmotion.TEARY) {
                        val blushAlpha = when (emotion) {
                            AyeshaEmotion.SHY_AFFECTION -> 0.36f
                            AyeshaEmotion.HAPPY -> 0.22f
                            AyeshaEmotion.POUTING -> 0.24f
                            AyeshaEmotion.WARM -> 0.11f
                            AyeshaEmotion.TEARY -> 0f
                        }
                        val blushRadius = eyeSpan * 0.38f
                        val leftCheek = Offset(leftEyePos.x - eyeSpan * 0.05f, mouthPos.y - eyeSpan * 0.06f)
                        val rightCheek = Offset(rightEyePos.x + eyeSpan * 0.05f, mouthPos.y - eyeSpan * 0.06f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFF6D9D).copy(alpha = blushAlpha), Color.Transparent),
                                center = leftCheek,
                                radius = blushRadius
                            ),
                            radius = blushRadius,
                            center = leftCheek
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFF6D9D).copy(alpha = blushAlpha), Color.Transparent),
                                center = rightCheek,
                                radius = blushRadius
                            ),
                            radius = blushRadius,
                            center = rightCheek
                        )
                    }

                    // Subtle lively eye catchlight during warm/happy/shy moments
                    if (blinkAmount < 0.2f && emotion != AyeshaEmotion.TEARY) {
                        val sparkleRadius = (eyeSpan * 0.032f).coerceAtLeast(1.3f)
                        val sparkleAlpha = if (emotion == AyeshaEmotion.SHY_AFFECTION || emotion == AyeshaEmotion.HAPPY) 0.55f else 0.30f
                        listOf(leftEyePos, rightEyePos).forEach { eye ->
                            drawCircle(
                                color = Color.White.copy(alpha = sparkleAlpha),
                                radius = sparkleRadius,
                                center = Offset(eye.x - eyeSpan * 0.035f, eye.y - eyeSpan * 0.03f)
                            )
                        }
                    }

                    // 2. Natural periodic eye blink (eyelid sweep + lash curve)
                    if (blinkAmount > 0.05f) {
                        val eyeW = eyeSpan * 0.34f
                        val lidH = eyeSpan * 0.17f * blinkAmount
                        val lidColor = Color(0xFFF3C6B6).copy(alpha = 0.88f * blinkAmount)
                        val lashColor = Color(0xFF281718).copy(alpha = 0.85f * blinkAmount)
                        listOf(leftEyePos, rightEyePos).forEach { eye ->
                            drawRoundRect(
                                color = lidColor,
                                topLeft = Offset(eye.x - eyeW * 0.5f, eye.y - lidH * 0.45f),
                                size = Size(eyeW, lidH),
                                cornerRadius = CornerRadius(eyeW * 0.4f, eyeW * 0.4f)
                            )
                            drawLine(
                                color = lashColor,
                                start = Offset(eye.x - eyeW * 0.45f, eye.y + lidH * 0.25f),
                                end = Offset(eye.x + eyeW * 0.45f, eye.y + lidH * 0.25f),
                                strokeWidth = (eyeSpan * 0.032f).coerceAtLeast(1.4f)
                            )
                        }
                    }

                    // 3. Teary eye shimmer & animated glistening tear during TEARY emotion
                    if (emotion == AyeshaEmotion.TEARY) {
                        val glintRadius = eyeSpan * 0.14f
                        listOf(leftEyePos, rightEyePos).forEach { eye ->
                            drawCircle(
                                color = Color(0xCCDCFAFF),
                                radius = glintRadius * 0.35f,
                                center = Offset(eye.x - eyeSpan * 0.04f, eye.y + eyeSpan * 0.08f)
                            )
                        }
                        val tearY = rightEyePos.y + eyeSpan * (0.12f + tearDropProgress * 0.48f)
                        val tearAlpha = (1f - tearDropProgress * 0.65f).coerceIn(0f, 0.85f)
                        val tearPos = Offset(rightEyePos.x + eyeSpan * 0.06f, tearY)
                        drawCircle(
                            color = Color(0xFFCCF2FF).copy(alpha = tearAlpha),
                            radius = (eyeSpan * 0.042f).coerceAtLeast(1.8f),
                            center = tearPos
                        )
                    }

                    // 4. Audio-synchronized lip-sync mouth movement
                    val mod = 0.78f + 0.22f * ((sin(syllableWave.toDouble()).toFloat() + 1f) * 0.5f)
                    val openFactor = (smoothAmp * 1.45f * mod).coerceIn(0f, 1f)
                    if (openFactor > 0.04f) {
                        val mouthW = eyeSpan * (0.34f + openFactor * 0.08f)
                        val mouthH = eyeSpan * (0.045f + openFactor * 0.145f)
                        val topLeft = Offset(mouthPos.x - mouthW * 0.5f, mouthPos.y - mouthH * 0.35f)

                        // Soft outer lip rose shadow
                        drawOval(
                            color = Color(0xFFD95F76).copy(alpha = (0.38f * openFactor).coerceIn(0f, 0.45f)),
                            topLeft = Offset(topLeft.x - mouthW * 0.08f, topLeft.y - mouthH * 0.15f),
                            size = Size(mouthW * 1.16f, mouthH * 1.32f)
                        )
                        // Inner mouth cavity
                        drawOval(
                            color = Color(0xFF42101D).copy(alpha = (0.78f * openFactor + 0.12f).coerceIn(0f, 0.86f)),
                            topLeft = topLeft,
                            size = Size(mouthW, mouthH)
                        )
                        // Subtle upper tooth sheen on wider vowels
                        if (openFactor > 0.32f) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = ((openFactor - 0.32f) * 0.85f).coerceIn(0f, 0.62f)),
                                topLeft = Offset(mouthPos.x - mouthW * 0.28f, topLeft.y + mouthH * 0.14f),
                                size = Size(mouthW * 0.56f, mouthH * 0.24f),
                                cornerRadius = CornerRadius(mouthW * 0.1f, mouthW * 0.1f)
                            )
                        }
                    } else if (emotion == AyeshaEmotion.POUTING) {
                        val poutW = eyeSpan * 0.30f
                        drawArc(
                            color = Color(0xFF9C3E52).copy(alpha = 0.42f),
                            startAngle = 200f,
                            sweepAngle = 140f,
                            useCenter = false,
                            topLeft = Offset(mouthPos.x - poutW * 0.5f, mouthPos.y - eyeSpan * 0.02f),
                            size = Size(poutW, eyeSpan * 0.10f),
                            style = Stroke(width = (eyeSpan * 0.022f).coerceAtLeast(1.2f))
                        )
                    }
                }
            }
        }
    }
}
