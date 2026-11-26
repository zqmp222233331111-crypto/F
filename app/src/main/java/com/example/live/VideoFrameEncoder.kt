package com.example.live

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Converts CameraX frames and MediaProjection screen capture frames into ~768x768 JPEGs,
 * strictly throttled to at most 1 frame per second.
 */
class VideoFrameEncoder {

    private val lastFrameSentAtMs = AtomicLong(0L)

    fun resetThrottle() {
        lastFrameSentAtMs.set(0L)
    }

    fun processCameraProxy(imageProxy: ImageProxy, onJpegReady: (ByteArray) -> Unit) {
        try {
            val now = System.currentTimeMillis()
            if (now - lastFrameSentAtMs.get() < MIN_FRAME_INTERVAL_MS) return

            val jpeg = imageProxyToJpeg(imageProxy) ?: return
            if (lastFrameSentAtMs.compareAndSet(lastFrameSentAtMs.get(), now) ||
                now - lastFrameSentAtMs.get() >= MIN_FRAME_INTERVAL_MS
            ) {
                lastFrameSentAtMs.set(now)
                onJpegReady(jpeg)
            }
        } catch (_: Exception) {
        } finally {
            imageProxy.close()
        }
    }

    private fun imageProxyToJpeg(imageProxy: ImageProxy): ByteArray? {
        val yuvBytes = yuv420ToNv21(imageProxy) ?: return null
        val yuvImage = YuvImage(
            yuvBytes,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )
        val rawOut = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 82, rawOut)
        val decoded = BitmapFactory.decodeByteArray(rawOut.toByteArray(), 0, rawOut.size())
            ?: return null

        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotated = if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }

        return scaleAndCompressBitmap(rotated)
    }

    fun scaleAndCompressBitmap(source: Bitmap): ByteArray {
        val maxDim = MAX_FRAME_DIM
        val width = source.width
        val height = source.height
        val scale = minOf(1f, maxDim.toFloat() / maxOf(width, height).toFloat())
        val target = if (scale < 0.99f) {
            Bitmap.createScaledBitmap(
                source,
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== source) source.recycle() }
        } else {
            source
        }

        val out = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, 72, out)
        target.recycle()
        return out.toByteArray()
    }

    private fun yuv420ToNv21(image: ImageProxy): ByteArray? {
        if (image.planes.size < 3) return null
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val width = image.width
        val height = image.height
        val nv21 = ByteArray(width * height * 3 / 2)

        // Y plane
        val yRowStride = yPlane.rowStride
        var pos = 0
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }

        // VU interleaved
        val uvRowStride = vPlane.rowStride
        val uvPixelStride = vPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val chromaHeight = height / 2
        val chromaWidth = width / 2

        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIdx = row * uvRowStride + col * uvPixelStride
                val uIdx = row * uRowStride + col * uPixelStride
                nv21[pos++] = vBuffer.get(vIdx)
                nv21[pos++] = uBuffer.get(uIdx)
            }
        }
        return nv21
    }

    companion object {
        const val MIN_FRAME_INTERVAL_MS = 1000L
        const val MAX_FRAME_DIM = 768
    }
}

/**
 * Manages MediaProjection VirtualDisplay + ImageReader to capture screen share frames
 * at <= 1 FPS and ~768px max dimension. Completely releases resources when stopped.
 */
class ScreenShareCaptureManager(
    private val context: Context,
    private val encoder: VideoFrameEncoder
) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var lastCaptureMs = 0L

    fun start(
        projection: MediaProjection,
        onJpegFrame: (ByteArray) -> Unit,
        onProjectionStopped: () -> Unit
    ) {
        stop()
        mediaProjection = projection
        encoder.resetThrottle()

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val scale = minOf(
            1f,
            VideoFrameEncoder.MAX_FRAME_DIM.toFloat() / maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        )
        val width = (metrics.widthPixels * scale).toInt().coerceAtLeast(320)
        val height = (metrics.heightPixels * scale).toInt().coerceAtLeast(320)

        val thread = HandlerThread("AyeshaScreenShare").apply { start() }
        handlerThread = thread
        val handler = Handler(thread.looper)

        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
                onProjectionStopped()
            }
        }, handler)

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ ir ->
            var image: Image? = null
            try {
                image = ir.acquireLatestImage() ?: return@setOnImageAvailableListener
                val now = System.currentTimeMillis()
                if (now - lastCaptureMs < VideoFrameEncoder.MIN_FRAME_INTERVAL_MS) {
                    return@setOnImageAvailableListener
                }
                lastCaptureMs = now
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val rawBitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888
                )
                rawBitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, width, height)
                if (cropped !== rawBitmap) rawBitmap.recycle()

                val jpeg = encoder.scaleAndCompressBitmap(cropped)
                onJpegFrame(jpeg)
            } catch (_: Exception) {
            } finally {
                image?.close()
            }
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "AyeshaScreenShare",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
    }

    fun stop() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        try {
            handlerThread?.quitSafely()
        } catch (_: Exception) {
        }
        handlerThread = null
    }
}
