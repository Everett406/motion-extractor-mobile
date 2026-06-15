package com.everett.motionextractor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.opencv.core.Mat

/**
 * Decodes a video file to BGR [Mat] frames using Android's MediaExtractor +
 * MediaCodec. This is more reliable on Android than OpenCV's VideoCapture,
 * which is often built without a functional video I/O backend.
 */
class VideoDecoder(context: Context, uri: Uri) : AutoCloseable {

    private val extractor = MediaExtractor()
    private val decoder: MediaCodec
    private val bufferInfo = MediaCodec.BufferInfo()
    private var outputFormat: MediaFormat? = null
    private var sawInputEos = false
    private var sawOutputEos = false
    private var isStarted = true

    val width: Int
    val height: Int
    val fps: Double
    val frameCount: Int
    val durationMs: Long

    init {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open $uri")
        extractor.setDataSource(pfd.fileDescriptor)
        pfd.close()

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        } ?: throw RuntimeException("No video track found")

        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)

        width = format.getInteger(MediaFormat.KEY_WIDTH)
        height = format.getInteger(MediaFormat.KEY_HEIGHT)
        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000
        fps = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            format.getInteger(MediaFormat.KEY_FRAME_RATE).toDouble()
        } else {
            30.0
        }
        frameCount = ((durationMs / 1000.0) * fps).toInt()

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
        decoder.start()
    }

    /**
     * Read the next decoded frame as a BGR [Mat] together with its presentation
     * timestamp in microseconds. Returns null when the stream ends.
     */
    fun readFrame(): Pair<Mat, Long>? {
        while (!sawOutputEos) {
            feedInput()
            val frame = drainOutput()
            if (frame != null) {
                return frame
            }
        }
        return null
    }

    override fun close() {
        if (isStarted) {
            try {
                decoder.stop()
            } catch (_: Exception) {
            }
            isStarted = false
        }
        decoder.release()
        extractor.release()
    }

    private fun feedInput() {
        if (sawInputEos) return

        val inputBufferId = decoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferId < 0) return

        val inputBuffer = decoder.getInputBuffer(inputBufferId)
            ?: throw RuntimeException("Decoder input buffer is null")

        val sampleSize = extractor.readSampleData(inputBuffer, 0)
        if (sampleSize < 0) {
            decoder.queueInputBuffer(
                inputBufferId,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            sawInputEos = true
        } else {
            decoder.queueInputBuffer(
                inputBufferId,
                0,
                sampleSize,
                extractor.sampleTime,
                0
            )
            extractor.advance()
        }
    }

    private fun drainOutput(): Pair<Mat, Long>? {
        val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

        when {
            outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                outputFormat = decoder.outputFormat
                return null
            }

            outputBufferId >= 0 -> {
                val image = decoder.getOutputImage(outputBufferId)
                val mat = if (image != null) {
                    val m = Yuv420Converter.imageToBgrMat(image)
                    image.close()
                    m
                } else {
                    Mat()
                }

                val pts = bufferInfo.presentationTimeUs
                sawOutputEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                decoder.releaseOutputBuffer(outputBufferId, false)

                if (!mat.empty()) {
                    return mat to pts
                }
            }
        }

        return null
    }

    companion object {
        private const val TIMEOUT_US = 10000L
    }
}
