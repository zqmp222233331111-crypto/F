package com.example.video

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Hardware-accelerated on-device video optimizer backed by Jetpack Media3 Transformer.
 * Shrinks high-bitrate 1080p/4K phone videos (up to 5+ minutes) into lightweight 480p H.264/AAC MP4s
 * so both short 12s clips (~35 MB -> ~2 MB) and 5-minute videos (~900 MB -> ~35 MB) upload rapidly
 * without network timeouts. Safely returns null on any codec issue to fall back to original upload.
 */
object VideoCompressor {

    const val COMPRESS_THRESHOLD_BYTES = 12L * 1024L * 1024L
    private const val COMPRESSION_TIMEOUT_MS = 180_000L

    fun shouldCompress(sizeBytes: Long): Boolean = sizeBytes > COMPRESS_THRESHOLD_BYTES

    fun targetBitrateForDuration(durationMs: Long): Int {
        return if (durationMs > 120_000L) 800_000 else 1_100_000
    }

    fun targetPresentationHeight(width: Int, height: Int, rotationDegrees: Int): Int? {
        if (width <= 0 || height <= 0) return 480
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        val effectiveW = if (swapped) height else width
        val effectiveH = if (swapped) width else height
        return if (effectiveH > effectiveW) {
            if (effectiveW > 540) 854 else null
        } else {
            if (effectiveH > 540) 480 else null
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun compressIfBeneficial(
        context: Context,
        sourceUri: Uri,
        originalSizeBytes: Long,
        durationMs: Long,
        onProgress: (String) -> Unit
    ): File? {
        if (!shouldCompress(originalSizeBytes)) return null

        val outFile = File(
            context.cacheDir,
            "ayesha_opt_${System.currentTimeMillis()}.mp4"
        )
        if (outFile.exists()) outFile.delete()

        val (width, height, rotation) = readVideoDimensions(context, sourceUri)
        val targetHeight = targetPresentationHeight(width, height, rotation)
        val targetBitrate = targetBitrateForDuration(durationMs)

        val resultFile = withContext(Dispatchers.Main) {
            withTimeoutOrNull(COMPRESSION_TIMEOUT_MS) {
                runTransformerExport(
                    context = context,
                    sourceUri = sourceUri,
                    outputFile = outFile,
                    targetHeight = targetHeight,
                    targetBitrate = targetBitrate,
                    onProgress = onProgress
                )
            }
        }

        return if (
            resultFile != null &&
            resultFile.exists() &&
            resultFile.length() in 1024L until originalSizeBytes
        ) {
            resultFile
        } else {
            runCatching { outFile.delete() }
            null
        }
    }

    private fun readVideoDimensions(context: Context, uri: Uri): Triple<Int, Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            Triple(w, h, rot)
        } catch (_: Exception) {
            Triple(0, 0, 0)
        } finally {
            runCatching { retriever.release() }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun runTransformerExport(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        targetHeight: Int?,
        targetBitrate: Int,
        onProgress: (String) -> Unit
    ): File? = suspendCancellableCoroutine { cont ->
        val mainHandler = Handler(Looper.getMainLooper())
        var finished = false
        var transformerRef: Transformer? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (finished) return
                finished = true
                mainHandler.removeCallbacksAndMessages(null)
                if (cont.isActive) cont.resume(outputFile)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                if (finished) return
                finished = true
                mainHandler.removeCallbacksAndMessages(null)
                runCatching { outputFile.delete() }
                if (cont.isActive) cont.resume(null)
            }
        }

        try {
            val mediaItem = MediaItem.fromUri(sourceUri)
            val editedBuilder = EditedMediaItem.Builder(mediaItem)
            if (targetHeight != null) {
                editedBuilder.setEffects(
                    Effects(
                        emptyList(),
                        listOf(Presentation.createForHeight(targetHeight))
                    )
                )
            }
            val editedItem = editedBuilder.build()

            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setEnableFallback(true)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(targetBitrate)
                        .build()
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()
            transformerRef = transformer

            val progressHolder = ProgressHolder()
            val progressRunnable = object : Runnable {
                override fun run() {
                    if (finished || !cont.isActive) return
                    val state = runCatching { transformer.getProgress(progressHolder) }
                        .getOrDefault(Transformer.PROGRESS_STATE_NOT_STARTED)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        val pct = progressHolder.progress.coerceIn(1, 99)
                        onProgress("ভিডিও অপটিমাইজ হচ্ছে... $pct%")
                    } else if (state == Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                        onProgress("ভিডিও প্রস্তুত হচ্ছে...")
                    }
                    if (!finished && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        mainHandler.postDelayed(this, 400L)
                    } else if (!finished) {
                        mainHandler.postDelayed(this, 400L)
                    }
                }
            }

            cont.invokeOnCancellation {
                finished = true
                mainHandler.removeCallbacksAndMessages(null)
                mainHandler.post {
                    runCatching { transformerRef?.cancel() }
                    runCatching { outputFile.delete() }
                }
            }

            onProgress("বড় ভিডিও ছোট ও দ্রুত করা হচ্ছে...")
            transformer.start(editedItem, outputFile.absolutePath)
            mainHandler.postDelayed(progressRunnable, 350L)
        } catch (_: Throwable) {
            finished = true
            mainHandler.removeCallbacksAndMessages(null)
            runCatching { outputFile.delete() }
            if (cont.isActive) cont.resume(null)
        }
    }
}
