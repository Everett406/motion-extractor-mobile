package com.everett.motionextractor

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.opencv.core.Mat
import java.io.File

/**
 * Handles video frame extraction and writing.
 *
 * Uses Android's MediaCodec/MediaMuxer instead of OpenCV's VideoCapture/
 * VideoWriter, because the OpenCV Android SDK is often built without a
 * functional video I/O backend.
 */
class VideoProcessor(private val context: Context) {

    data class VideoInfo(
        val width: Int,
        val height: Int,
        val fps: Double,
        val frameCount: Int,
        val durationMs: Long
    )

    /**
     * Read basic information about a video file.
     */
    fun getVideoInfo(uri: Uri): VideoInfo? {
        var pfd: android.os.ParcelFileDescriptor? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return null
            val extractor = MediaExtractor()
            extractor.setDataSource(pfd.fileDescriptor)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: return null

            val format = extractor.getTrackFormat(trackIndex)
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
            val fps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
            } else {
                30.0
            }
            val frameCount = ((durationMs / 1000.0) * fps).toInt()

            extractor.release()
            VideoInfo(width, height, fps, frameCount, durationMs)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                pfd?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Generate a few preview frames spread across the video.
     */
    fun generatePreviewFrames(
        uri: Uri,
        params: MotionExtractParams,
        count: Int = 5
    ): List<Mat> {
        VideoDecoder(context, uri).use { decoder ->
            val total = decoder.frameCount.coerceAtLeast(count)
            val result = mutableListOf<Mat>()
            val buffer = ArrayDeque<Mat>()
            val maxOffset = params.offsetFrames.coerceAtLeast(1)

            // Pre-fill the offset buffer.
            repeat(maxOffset + 1) {
                decoder.readFrame()?.let { (mat, _) -> buffer.add(mat) }
            }

            val step = total / (count + 1)
            var frameIndex = 0
            var nextPreviewIndex = step

            while (buffer.isNotEmpty() && result.size < count) {
                if (frameIndex >= nextPreviewIndex) {
                    val current = buffer.first()
                    val offset = buffer.getOrNull(maxOffset) ?: current
                    val processed = MotionExtractor.processFrame(current, offset, params)
                    result.add(processed)
                    nextPreviewIndex += step
                }

                buffer.removeFirst().release()
                decoder.readFrame()?.let { (mat, _) -> buffer.add(mat) }
                frameIndex++
            }

            buffer.forEach { it.release() }
            return result
        }
    }

    /**
     * Process the whole video and write to an MP4 output file.
     *
     * @return the output file path
     */
    fun exportVideo(
        uri: Uri,
        params: MotionExtractParams,
        onProgress: (Float) -> Unit
    ): File {
        val outputFile = File(context.cacheDir, "motion_extract_${System.currentTimeMillis()}.mp4")

        VideoDecoder(context, uri).use { decoder ->
            VideoEncoder(
                outputFile,
                decoder.width,
                decoder.height,
                decoder.fps
            ).use { encoder ->
                val buffer = ArrayDeque<Mat>()
                val maxOffset = params.offsetFrames

                // Pre-fill the offset buffer.
                repeat(maxOffset + 1) {
                    decoder.readFrame()?.let { (mat, _) -> buffer.add(mat) }
                }

                var processedCount = 0
                val current = Mat()
                val offset = Mat()

                while (buffer.isNotEmpty()) {
                    buffer.removeFirst().apply {
                        copyTo(current)
                        release()
                    }

                    decoder.readFrame()?.let { (mat, _) -> buffer.add(mat) }

                    val offsetIndex = params.offsetFrames.coerceAtMost(buffer.size - 1)
                    if (offsetIndex >= 0) {
                        buffer[offsetIndex].copyTo(offset)
                    } else {
                        current.copyTo(offset)
                    }

                    val processed = MotionExtractor.processFrame(current, offset, params)
                    encoder.encodeFrame(processed)
                    processed.release()

                    processedCount++
                    if (decoder.frameCount > 0) {
                        onProgress(processedCount.toFloat() / decoder.frameCount)
                    }
                }

                current.release()
                offset.release()
                buffer.forEach { it.release() }
                encoder.finish()
                onProgress(1.0f)
            }
        }

        return outputFile
    }
}
