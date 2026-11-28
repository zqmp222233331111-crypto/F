package com.example.persona

import androidx.annotation.DrawableRes
import com.example.R

enum class AyeshaOutfit(
    val id: String,
    val labelBn: String,
    @DrawableRes val drawableRes: Int,
    val mouthCenterX: Float,
    val mouthCenterY: Float,
    val eyeCenterY: Float,
    val leftEyeX: Float,
    val rightEyeX: Float
) {
    WHITE_FLORAL(
        id = "white_floral",
        labelBn = "সাদা-কালো ড্রেস",
        drawableRes = R.drawable.ayesha_outfit_white_floral,
        mouthCenterX = 0.520f,
        mouthCenterY = 0.254f,
        eyeCenterY = 0.189f,
        leftEyeX = 0.405f,
        rightEyeX = 0.607f
    ),
    YELLOW(
        id = "yellow",
        labelBn = "হলুদ ড্রেস",
        drawableRes = R.drawable.ayesha_outfit_yellow,
        mouthCenterX = 0.532f,
        mouthCenterY = 0.256f,
        eyeCenterY = 0.177f,
        leftEyeX = 0.400f,
        rightEyeX = 0.599f
    ),
    BLACK_HEART(
        id = "black_heart",
        labelBn = "কালো হার্ট ড্রেস",
        drawableRes = R.drawable.ayesha_outfit_black_heart,
        mouthCenterX = 0.524f,
        mouthCenterY = 0.255f,
        eyeCenterY = 0.175f,
        leftEyeX = 0.400f,
        rightEyeX = 0.592f
    ),
    RED_ROSE(
        id = "red_rose",
        labelBn = "লাল গোলাপ ড্রেস",
        drawableRes = R.drawable.ayesha_outfit_red_rose,
        mouthCenterX = 0.518f,
        mouthCenterY = 0.261f,
        eyeCenterY = 0.189f,
        leftEyeX = 0.402f,
        rightEyeX = 0.610f
    );

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
