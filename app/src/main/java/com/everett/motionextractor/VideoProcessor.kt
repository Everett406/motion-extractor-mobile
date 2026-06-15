package com.everett.motionextractor

import android.content.Context
import android.net.Uri
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.opencv.videoio.VideoWriter
import org.opencv.videoio.Videoio
import java.io.File

/**
 * Handles video frame extraction and writing.
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
        val path = copyToTempFile(uri)
        val capture = VideoCapture(path)
        if (!capture.isOpened) {
            capture.release()
            return null
        }

        val width = capture.get(Videoio.CAP_PROP_FRAME_WIDTH).toInt()
        val height = capture.get(Videoio.CAP_PROP_FRAME_HEIGHT).toInt()
        val fps = capture.get(Videoio.CAP_PROP_FPS)
        val frameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT).toInt()
        val durationMs = ((frameCount / fps) * 1000).toLong()

        capture.release()
        return VideoInfo(width, height, fps, frameCount, durationMs)
    }

    /**
     * Generate a few preview frames spread across the video.
     */
    fun generatePreviewFrames(
        uri: Uri,
        params: MotionExtractParams,
        count: Int = 5
    ): List<Mat> {
        val path = copyToTempFile(uri)
        val capture = VideoCapture(path)
        val total = capture.get(Videoio.CAP_PROP_FRAME_COUNT).toInt()
        val indices = if (total <= count) {
            (0 until total).toList()
        } else {
            List(count) { index -> (total * (index + 1)) / (count + 1) }
        }

        val result = mutableListOf<Mat>()
        val current = Mat()
        val offset = Mat()

        for (idx in indices) {
            capture.set(Videoio.CAP_PROP_POS_FRAMES, idx.toDouble())
            if (!capture.read(current)) continue

            val target = (idx + params.offsetFrames).coerceAtMost(total - 1)
            capture.set(Videoio.CAP_PROP_POS_FRAMES, target.toDouble())
            if (!capture.read(offset)) {
                // Fall back to current frame if offset is out of range.
                current.copyTo(offset)
            }

            val processed = MotionExtractor.processFrame(current, offset, params)
            result.add(processed)
        }

        current.release()
        offset.release()
        capture.release()
        return result
    }

    /**
     * Process the whole video and write to an output file.
     *
     * @return the output file path
     */
    fun exportVideo(
        uri: Uri,
        params: MotionExtractParams,
        onProgress: (Float) -> Unit
    ): File {
        val inputPath = copyToTempFile(uri)
        val outputFile = File(context.cacheDir, "motion_extract_${System.currentTimeMillis()}.avi")

        val capture = VideoCapture(inputPath)
        val width = capture.get(Videoio.CAP_PROP_FRAME_WIDTH).toInt()
        val height = capture.get(Videoio.CAP_PROP_FRAME_HEIGHT).toInt()
        val fps = capture.get(Videoio.CAP_PROP_FPS)
        val total = capture.get(Videoio.CAP_PROP_FRAME_COUNT).toInt()

        // Use MJPG in AVI container; more compatible on Android than mp4v.
        val writer = VideoWriter(
            outputFile.absolutePath,
            VideoWriter.fourcc('M', 'J', 'P', 'G'),
            fps,
            Size(width.toDouble(), height.toDouble())
        )

        if (!writer.isOpened) {
            capture.release()
            writer.release()
            throw RuntimeException("Cannot open VideoWriter")
        }

        // Buffer upcoming frames to allow offset lookups without slow seeking.
        val maxOffset = params.offsetFrames
        val buffer = ArrayDeque<Mat>()

        repeat(maxOffset + 1) {
            val frame = Mat()
            if (capture.read(frame)) {
                buffer.add(frame)
            }
        }

        var processedCount = 0
        val current = Mat()
        val offset = Mat()

        while (buffer.isNotEmpty()) {
            buffer.removeFirst().copyTo(current)

            // Replenish buffer.
            val next = Mat()
            if (capture.read(next)) {
                buffer.add(next)
            }

            // Get offset frame.
            val offsetIndex = params.offsetFrames.coerceAtMost(buffer.size - 1)
            if (offsetIndex >= 0) {
                buffer[offsetIndex].copyTo(offset)
            } else {
                current.copyTo(offset)
            }

            val processed = MotionExtractor.processFrame(current, offset, params)
            writer.write(processed)

            processed.release()
            processedCount++
            if (total > 0) {
                onProgress(processedCount.toFloat() / total)
            }
        }

        current.release()
        offset.release()
        buffer.forEach { it.release() }
        capture.release()
        writer.release()

        onProgress(1.0f)
        return outputFile
    }

    /**
     * Copy a content URI to a temporary file so OpenCV can read it.
     */
    private fun copyToTempFile(uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream for $uri")
        val tempFile = File(context.cacheDir, "input_${System.currentTimeMillis()}.mp4")
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
        input.close()
        return tempFile.absolutePath
    }
}
