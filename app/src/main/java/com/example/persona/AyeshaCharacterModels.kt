package com.example.persona

import androidx.annotation.DrawableRes
import com.example.R

enum class AyeshaOutfit(
    val id: String,
    val labelBn: String,
    @DrawableRes val drawableRes: Int,
    val mouthCenterX: Float,
    val mouthCenterY: Float,
    val mouthHalfW: Float,
    val leftEyeX: Float,
    val leftEyeY: Float,
    val rightEyeX: Float,
    val rightEyeY: Float,
    val chinY: Float,
    val neckY: Float,
    val shoulderY: Float
) {
    WHITE_FLORAL(
        id = "white_floral",
        labelBn = "সাদা-কালো ড্রেস",
        drawableRes = R.drawable.ayesha_outfit_white_floral,
        mouthCenterX = 0.532f,
        mouthCenterY = 0.302f,
        mouthHalfW = 0.051f,
        leftEyeX = 0.459f,
        leftEyeY = 0.237f,
        rightEyeX = 0.583f,
        rightEyeY = 0.231f,
        chinY = 0.354f,
        neckY = 0.405f,
        shoulderY = 0.479f
    ),
    YELLOW(
        id = "yellow",
        labelBn = "হলুদ ড্রেস",
        drawableRes = R.drawable.ayesha_outfit_yellow,
        mouthCenterX = 0.531f,
        mouthCenterY = 0.257f,
        mouthHalfW = 0.050f,
        leftEyeX = 0.457f,
        leftEyeY = 0.192f,
        rightEyeX = 0.580f,
        rightEyeY = 0.187f,
        chinY = 0.312f,
        neckY = 0.370f,
        shoulderY = 0.504f
    ),
    BLACK_HEART(
        id = "black_heart",
        labelBn = "কালো হার্ট ড্রেস",
        drawableRes = R.drawable.ayesha_outfit_black_heart,
        mouthCenterX = 0.528f,
        mouthCenterY = 0.252f,
        mouthHalfW = 0.050f,
        leftEyeX = 0.456f,
        leftEyeY = 0.192f,
        rightEyeX = 0.578f,
        rightEyeY = 0.186f,
        chinY = 0.308f,
        neckY = 0.365f,
        shoulderY = 0.479f
    ),
    RED_ROSE(
        id = "red_rose",
        labelBn = "লাল গোলাপ ড্রেস",
        drawableRes = R.drawable.ayesha_outfit_red_rose,
        mouthCenterX = 0.538f,
        mouthCenterY = 0.308f,
        mouthHalfW = 0.051f,
        leftEyeX = 0.459f,
        leftEyeY = 0.236f,
        rightEyeX = 0.581f,
        rightEyeY = 0.230f,
        chinY = 0.362f,
        neckY = 0.415f,
        shoulderY = 0.458f
    );

    val eyeCenterY: Float
        get() = (leftEyeY + rightEyeY) * 0.5f

    companion object {
        fun fromIdOrCycle(raw: String?, current: AyeshaOutfit): AyeshaOutfit {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return when {
                normalized.contains("yellow") || normalized.contains("হলুদ") -> YELLOW
                normalized.contains("white") || normalized.contains("floral") || normalized.contains("সাদা") -> WHITE_FLORAL
                normalized.contains("black") || normalized.contains("heart") || normalized.contains("কালো") -> BLACK_HEART
                normalized.contains("red") || normalized.contains("rose") || normalized.contains("লাল") -> RED_ROSE
                normalized == "next" || normalized == "other" || normalized.isBlank() -> {
                    val all = entries
                    val nextIndex = (all.indexOf(current) + 1) % all.size
                    all[nextIndex]
                }
                else -> {
                    val all = entries
                    val nextIndex = (all.indexOf(current) + 1) % all.size
                    all[nextIndex]
                }
            }
        }
    }
}

enum class AyeshaEmotion(
    val id: String,
    val labelBn: String
) {
    WARM("warm", "আন্তরিক"),
    HAPPY("happy", "হাসিখুশি"),
    POUTING("pouting", "অভিমান"),
    TEARY("teary", "আবেগী"),
    SHY_AFFECTION("shy", "লাজুক");

    companion object {
        fun fromId(raw: String?): AyeshaEmotion {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return when {
                normalized.contains("happy") || normalized.contains("laugh") || normalized.contains("smile") || normalized.contains("হাসি") -> HAPPY
                normalized.contains("pout") || normalized.contains("sulk") || normalized.contains("angry") || normalized.contains("অভিমান") -> POUTING
                normalized.contains("tear") || normalized.contains("cry") || normalized.contains("sad") || normalized.contains("কান্না") -> TEARY
                normalized.contains("shy") || normalized.contains("blush") || normalized.contains("romantic") || normalized.contains("love") || normalized.contains("লাজুক") -> SHY_AFFECTION
                else -> WARM
            }
        }
    }
}
