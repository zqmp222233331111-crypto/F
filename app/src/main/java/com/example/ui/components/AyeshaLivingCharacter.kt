package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.example.persona.AyeshaEmotion
import com.example.persona.AyeshaOutfit
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private const val MESH_COLS = 28
private const val MESH_ROWS = 48
private const val MESH_VERTEX_COUNT = (MESH_COLS + 1) * (MESH_ROWS + 1)

/**
 * Renders Ayesha's portrait outfit with natural, locked-frame 60fps 2.5D mesh articulation:
 * - Outer rectangular frame & borders stay 100% stationary (zero cardboard/frame wobble)
 * - Independent neck-pivoted head tilts, nods, shoulder breathing & gentle upper-body life via drawBitmapMesh
 * - Real lip & jaw mesh deformation synced directly to speakerAmplitude on her actual lips
 * - Pixel-exact eye blinks, warm cheek blush, eye catchlights, and teary shimmer/teardrop
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
                    AyeshaEmotion.TEARY -> 3100
                    else -> 2650
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
                    AyeshaEmotion.HAPPY -> 3300
                    AyeshaEmotion.POUTING -> 4900
                    else -> 4200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "char_sway"
    )

    val gesturePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "char_gesture"
    )

    val syllableWave by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 390, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "char_syllable"
    )

    val tearDropProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "char_tear"
    )

    // Natural periodic single + occasional double blink
    var blinkAmount by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var cycle = 0
        while (true) {
            delay(if (cycle % 3 == 0) 2500L else 3600L)
            blinkAmount = 0.65f
            delay(40L)
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
        animationSpec = spring(stiffness = 480f, dampingRatio = 0.72f),
        label = "char_lip_amp"
    )

    val emotionTint = remember(emotion) {
        when (emotion) {
            AyeshaEmotion.HAPPY -> Color(0x14FF9EEB)
            AyeshaEmotion.POUTING -> Color(0x18392059)
            AyeshaEmotion.TEARY -> Color(0x201A3163)
            AyeshaEmotion.SHY_AFFECTION -> Color(0x20FF73A8)
            AyeshaEmotion.WARM -> Color(0x0CFF9EC9)
        }
    }

    val context = LocalContext.current

    Box(
        modifier = modifier
            .background(Color(0xFF000000))
    ) {
        Crossfade(
            targetState = outfit,
            animationSpec = tween(durationMillis = 420),
            label = "outfit_crossfade"
        ) { targetOutfit ->
            val bitmap: Bitmap? = remember(targetOutfit.drawableRes) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inScaled = false }
                    BitmapFactory.decodeResource(context.resources, targetOutfit.drawableRes, opts)
                }.getOrNull()
            }

            // Reused vertex arrays across frames for zero GC churn
            val meshVerts = remember(targetOutfit) { FloatArray(MESH_VERTEX_COUNT * 2) }
            val meshPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG) }

            Box(modifier = Modifier.fillMaxSize()) {
                if (bitmap == null) {
                    // Fallback painter if resource decode fails in constrained test environment
                    Image(
                        painter = painterResource(id = targetOutfit.drawableRes),
                        contentDescription = "আয়শা (${targetOutfit.labelBn})",
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val srcW = (bitmap?.width ?: 540).toFloat()
                    val srcH = (bitmap?.height ?: 960).toFloat()

                    // ContentScale.Crop + Alignment.TopCenter coordinate mapping (edges stay 100% locked!)
                    val scale = maxOf(size.width / srcW, size.height / srcH)
                    val scaledW = srcW * scale
                    val scaledH = srcH * scale
                    val offsetX = (size.width - scaledW) * 0.5f
                    val offsetY = 0f

                    val activeAmp = smoothAmp
                    val syllableMod = 0.70f + 0.30f * ((sin(syllableWave.toDouble()).toFloat() + 1f) * 0.5f)
                    val secondarySyllable = 0.88f + 0.12f * cos(syllableWave * 1.65)
                        .toFloat()
                    val openFactor = (activeAmp * 1.48f * syllableMod * secondarySyllable).coerceIn(0f, 1f)

                    // Emotion & conversational posture angles (applied internally around neck, NEVER to frame!)
                    val emotionTiltDeg = when (emotion) {
                        AyeshaEmotion.POUTING -> -2.2f
                        AyeshaEmotion.SHY_AFFECTION -> 2.4f
                        AyeshaEmotion.TEARY -> -1.5f
                        AyeshaEmotion.HAPPY -> 0.8f
                        AyeshaEmotion.WARM -> 0.3f
                    }
                    val headTiltDeg = emotionTiltDeg +
                        swayPhase * (if (activeAmp > 0.08f) 2.1f else 1.25f) +
                        sin(gesturePhase.toDouble()).toFloat() * 0.55f
                    val headTiltRad = (headTiltDeg * PI / 180.0).toFloat()
                    val cosHead = cos(headTiltRad.toDouble()).toFloat()
                    val sinHead = sin(headTiltRad.toDouble()).toFloat()

                    val headNodNorm = ((breathPhase - 0.5f) * -0.0045f) -
                        (activeAmp * 0.0060f) +
                        (if (emotion == AyeshaEmotion.TEARY) 0.004f else 0f)
                    val headSwayNormX = swayPhase * 0.0065f +
                        sin((gesturePhase * 0.5f).toDouble()).toFloat() * 0.002f
                    val breathLiftNorm = (breathPhase - 0.5f) * -0.0075f
                    val bodySwayNormX = sin((gesturePhase + 0.65f).toDouble()).toFloat() * 0.0075f +
                        activeAmp * sin(syllableWave * 0.5) .toFloat() * 0.0025f

                    val neckPivotX = targetOutfit.mouthCenterX
                    val neckPivotY = targetOutfit.neckY
                    val mX = targetOutfit.mouthCenterX
                    val mY = targetOutfit.mouthCenterY
                    val mHalfW = targetOutfit.mouthHalfW
                    val chinY = targetOutfit.chinY
                    val shoulderY = targetOutfit.shoulderY

                    /**
                     * Computes deformed normalized source coordinates (u', v') from rest coordinate (u, v).
                     * Borders (u in {0,1} or v in {0,1}) smoothly damp to zero displacement so frame edges never tear or move.
                     */
                    fun deformNormalized(u: Float, v: Float, applyLipAndBlink: Boolean): Offset {
                        val edgeU = smoothStep(0f, 0.09f, u) * smoothStep(1f, 0.91f, u)
                        val edgeV = smoothStep(0f, 0.06f, v) * smoothStep(1f, 0.94f, v)
                        val edgeLock = edgeU * edgeV
                        if (edgeLock <= 0.0001f) return Offset(u, v)

                        var du = 0f
                        var dv = 0f

                        // A. Independent Neck-Pivoted Head Articulation (head & hair above neckY)
                        val headWeight = smoothStep(shoulderY + 0.03f, chinY + 0.02f, v)
                        if (headWeight > 0.0001f) {
                            val rx = u - neckPivotX
                            val ry = v - neckPivotY
                            val rotX = rx * cosHead - ry * sinHead - rx
                            val rotY = rx * sinHead + ry * cosHead - ry
                            du += (rotX + headSwayNormX) * headWeight
                            dv += (rotY + headNodNorm) * headWeight
                        }

                        // B. Chest Ribcage Breathing & Upper-Body Life (anchored cleanly inside frame)
                        val chestWeight = smoothStep(chinY, shoulderY + 0.04f, v) *
                            smoothStep(0.92f, shoulderY + 0.14f, v)
                        if (chestWeight > 0.0001f) {
                            val ribScale = 1f + (breathPhase - 0.5f) * 0.022f
                            du += (u - 0.5f) * (ribScale - 1f) * chestWeight
                            dv += breathLiftNorm * chestWeight
                        }

                        val torsoLifeWeight = smoothStep(neckPivotY, shoulderY + 0.10f, v) *
                            smoothStep(0.96f, 0.68f, v)
                        if (torsoLifeWeight > 0.0001f) {
                            val armBand = 0.65f + 0.35f * smoothStep(0.18f, 0.40f, kotlin.math.abs(u - 0.5f))
                            du += bodySwayNormX * torsoLifeWeight * armBand
                            dv += (breathLiftNorm * 0.65f - activeAmp * 0.002f) * torsoLifeWeight
                        }

                        // C. Direct Facial Mesh Lip-Sync & Natural Expression (zero pasted oval!)
                        if (applyLipAndBlink) {
                            // 1) Lower lip & jaw drop synchronized with spoken voice
                            if (openFactor > 0.008f) {
                                val dxMouth = (u - mX) / (mHalfW * 1.45f)
                                val horizGauss = exp(-(dxMouth * dxMouth).toDouble()).toFloat()

                                // Lower lip & chin smoothly pulled downward
                                val lowerDelta = v - (mY - 0.004f)
                                if (lowerDelta > -0.006f && v < chinY + 0.07f) {
                                    val vertEnv = smoothStep(-0.006f, 0.012f, lowerDelta) *
                                        smoothStep(chinY + 0.07f, mY + 0.020f, v)
                                    val jawDropNorm = 0.0165f * openFactor // up to ~16 src px on max vowels
                                    dv += jawDropNorm * horizGauss * vertEnv
                                    // Subtle natural mouth rounding / slight inward cheek draw on "O"/"U" syllables
                                    du -= (u - mX) * 0.045f * openFactor * horizGauss * vertEnv
                                }

                                // Upper lip lifts subtly & corners widen slightly
                                val upperDelta = (mY + 0.002f) - v
                                if (upperDelta > 0f && upperDelta < 0.028f) {
                                    val upperEnv = smoothStep(0f, 0.008f, upperDelta) *
                                        smoothStep(0.028f, 0.008f, upperDelta)
                                    dv -= 0.0032f * openFactor * horizGauss * upperEnv
                                }
                            }

                            // 2) Subtle smile lift / emotional mouth-corner curve
                            val cornerSide = kotlin.math.abs(u - mX)
                            if (cornerSide in (mHalfW * 0.45f)..(mHalfW * 1.65f) &&
                                kotlin.math.abs(v - mY) < 0.026f
                            ) {
                                val cornerMask = smoothStep(mHalfW * 0.45f, mHalfW * 0.95f, cornerSide) *
                                    smoothStep(mHalfW * 1.65f, mHalfW * 1.0f, cornerSide) *
                                    smoothStep(0.026f, 0.006f, kotlin.math.abs(v - mY))
                                val smileShift = when (emotion) {
                                    AyeshaEmotion.HAPPY -> -0.0036f
                                    AyeshaEmotion.SHY_AFFECTION -> -0.0026f
                                    AyeshaEmotion.WARM -> -0.0014f
                                    AyeshaEmotion.POUTING -> 0.0022f
                                    AyeshaEmotion.TEARY -> 0.0030f
                                }
                                dv += smileShift * cornerMask
                            }

                            // 3) Subtle upper-eyelid pull on blink so lashes actually dip naturally
                            if (blinkAmount > 0.05f) {
                                fun applyEyeLidDip(ex: Float, ey: Float) {
                                    val dxE = (u - ex) / 0.044f
                                    val dyE = (v - (ey - 0.008f)) / 0.020f
                                    val wEye = exp(-(dxE * dxE + dyE * dyE).toDouble()).toFloat()
                                    if (wEye > 0.02f) {
                                        dv += 0.0068f * blinkAmount * wEye
                                    }
                                }
                                applyEyeLidDip(targetOutfit.leftEyeX, targetOutfit.leftEyeY)
                                applyEyeLidDip(targetOutfit.rightEyeX, targetOutfit.rightEyeY)
                            }
                        }

                        return Offset(
                            x = (u + du * edgeLock).coerceIn(0f, 1f),
                            y = (v + dv * edgeLock).coerceIn(0f, 1f)
                        )
                    }

                    fun toCanvasOffset(norm: Offset): Offset =
                        Offset(offsetX + norm.x * scaledW, offsetY + norm.y * scaledH)

                    // Draw articulated 2D portrait mesh
                    if (bitmap != null) {
                        var idx = 0
                        for (row in 0..MESH_ROWS) {
                            val v = row.toFloat() / MESH_ROWS
                            for (col in 0..MESH_COLS) {
                                val u = col.toFloat() / MESH_COLS
                                val deformed = deformNormalized(u, v, applyLipAndBlink = true)
                                meshVerts[idx++] = offsetX + deformed.x * scaledW
                                meshVerts[idx++] = offsetY + deformed.y * scaledH
                            }
                        }
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawBitmapMesh(
                                bitmap,
                                MESH_COLS,
                                MESH_ROWS,
                                meshVerts,
                                0,
                                null,
                                0,
                                meshPaint
                            )
                        }
                    }

                    // Soft atmosphere color veil
                    if (emotionTint != Color.Transparent) {
                        drawRect(color = emotionTint)
                    }

                    // Facial anchor points tracked through the EXACT SAME head transform so overlays never drift!
                    val leftEyePos = toCanvasOffset(deformNormalized(targetOutfit.leftEyeX, targetOutfit.leftEyeY, applyLipAndBlink = false))
                    val rightEyePos = toCanvasOffset(deformNormalized(targetOutfit.rightEyeX, targetOutfit.rightEyeY, applyLipAndBlink = false))
                    val mouthPos = toCanvasOffset(deformNormalized(mX, mY, applyLipAndBlink = false))
                    val eyeSpan = (rightEyePos.x - leftEyePos.x).coerceAtLeast(size.width * 0.08f)

                    // 1. Subtle recessed dark inner-mouth cavity strictly tucked between parted real lips (NO cartoon mouth!)
                    if (openFactor > 0.08f) {
                        val cavityW = (mHalfW * scaledW) * (0.98f + openFactor * 0.16f)
                        val cavityH = eyeSpan * (0.025f + openFactor * 0.095f)
                        val cavityCenter = Offset(
                            mouthPos.x,
                            mouthPos.y + cavityH * 0.24f
                        )
                        val cavityAlpha = ((openFactor - 0.08f) * 0.72f).coerceIn(0f, 0.64f)
                        drawOval(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF28050D).copy(alpha = cavityAlpha),
                                    Color(0xFF470E1A).copy(alpha = cavityAlpha * 0.58f),
                                    Color.Transparent
                                ),
                                center = cavityCenter,
                                radius = maxOf(cavityW, cavityH) * 0.56f
                            ),
                            topLeft = Offset(cavityCenter.x - cavityW * 0.5f, cavityCenter.y - cavityH * 0.5f),
                            size = Size(cavityW, cavityH)
                        )
                    }

                    // 2. Rosy cheek blush during romantic / happy / pouting states
                    if (emotion != AyeshaEmotion.TEARY) {
                        val blushAlpha = when (emotion) {
                            AyeshaEmotion.SHY_AFFECTION -> 0.34f
                            AyeshaEmotion.HAPPY -> 0.22f
                            AyeshaEmotion.POUTING -> 0.20f
                            AyeshaEmotion.WARM -> 0.11f
                            AyeshaEmotion.TEARY -> 0f
                        }
                        val blushRadius = eyeSpan * 0.42f
                        val leftCheek = Offset(leftEyePos.x - eyeSpan * 0.08f, (leftEyePos.y + mouthPos.y) * 0.54f)
                        val rightCheek = Offset(rightEyePos.x + eyeSpan * 0.08f, (rightEyePos.y + mouthPos.y) * 0.54f)
                        listOf(leftCheek, rightCheek).forEach { cheek ->
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFF6D9D).copy(alpha = blushAlpha), Color.Transparent),
                                    center = cheek,
                                    radius = blushRadius
                                ),
                                radius = blushRadius,
                                center = cheek
                            )
                        }
                    }

                    // 3. Crisp lively eye catchlight when eyes are open
                    if (blinkAmount < 0.22f && emotion != AyeshaEmotion.TEARY) {
                        val sparkleRadius = (eyeSpan * 0.030f).coerceAtLeast(1.2f)
                        val sparkleAlpha = if (emotion == AyeshaEmotion.SHY_AFFECTION || emotion == AyeshaEmotion.HAPPY) 0.52f else 0.28f
                        listOf(leftEyePos, rightEyePos).forEach { eye ->
                            drawCircle(
                                color = Color.White.copy(alpha = sparkleAlpha),
                                radius = sparkleRadius,
                                center = Offset(eye.x - eyeSpan * 0.028f, eye.y - eyeSpan * 0.022f)
                            )
                        }
                    }

                    // 4. Natural skin-blended eyelid sweep + curved lash line locked to exact iris centers
                    if (blinkAmount > 0.06f) {
                        val eyeW = eyeSpan * 0.34f
                        val lidH = eyeSpan * 0.16f * blinkAmount
                        val lashStroke = (eyeSpan * 0.028f).coerceAtLeast(1.3f)
                        listOf(leftEyePos, rightEyePos).forEach { eye ->
                            val lidCenter = Offset(eye.x, eye.y - lidH * 0.10f)
                            drawOval(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFE7B6A2).copy(alpha = 0.88f * blinkAmount),
                                        Color(0xFFD5A08B).copy(alpha = 0.55f * blinkAmount),
                                        Color.Transparent
                                    ),
                                    center = lidCenter,
                                    radius = eyeW * 0.58f
                                ),
                                topLeft = Offset(eye.x - eyeW * 0.55f, eye.y - lidH * 0.55f),
                                size = Size(eyeW * 1.10f, lidH * 1.15f)
                            )
                            drawArc(
                                color = Color(0xFF241315).copy(alpha = 0.84f * blinkAmount),
                                startAngle = 12f,
                                sweepAngle = 156f,
                                useCenter = false,
                                topLeft = Offset(eye.x - eyeW * 0.42f, eye.y - lidH * 0.15f),
                                size = Size(eyeW * 0.84f, lidH * 0.55f),
                                style = Stroke(width = lashStroke, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // 5. Teary eye shimmer & animated glistening teardrop during TEARY emotion
                    if (emotion == AyeshaEmotion.TEARY) {
                        val glintRadius = eyeSpan * 0.13f
                        listOf(leftEyePos, rightEyePos).forEach { eye ->
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xDCE0FBFF), Color.Transparent),
                                    center = Offset(eye.x, eye.y + eyeSpan * 0.06f),
                                    radius = glintRadius
                                ),
                                radius = glintRadius,
                                center = Offset(eye.x, eye.y + eyeSpan * 0.06f)
                            )
                        }
                        val tearY = rightEyePos.y + eyeSpan * (0.09f + tearDropProgress * 0.52f)
                        val tearAlpha = (1f - tearDropProgress * 0.65f).coerceIn(0f, 0.88f)
                        val tearPos = Offset(rightEyePos.x + eyeSpan * 0.04f, tearY)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = tearAlpha),
                                    Color(0xFFB5ECFF).copy(alpha = tearAlpha * 0.72f),
                                    Color.Transparent
                                ),
                                center = tearPos,
                                radius = (eyeSpan * 0.062f).coerceAtLeast(2.4f)
                            ),
                            radius = (eyeSpan * 0.062f).coerceAtLeast(2.4f),
                            center = tearPos
                        )
                    }
                }
            }
        }
    }
}

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge0 == edge1) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
