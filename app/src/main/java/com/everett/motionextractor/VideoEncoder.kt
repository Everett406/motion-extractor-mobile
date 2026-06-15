package com.everett.motionextractor

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import org.opencv.core.Mat
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes BGR [Mat] frames to an MP4 file using Android's MediaCodec +
 * MediaMuxer.
 *
 * Prefers planar YUV input (I420) so frames can be supplied as a single byte
 * buffer without per-pixel interleaving. Falls back to YUV_420_888 image input
 * if planar is not available.
 */
class VideoEncoder(
    outputFile: File,
    private val width: Int,
    private val height: Int,
    fps: Double,
    bitrate: Int = width * height * 4,
    rotationDegrees: Int = 0
) : AutoCloseable {

    private val encoder: MediaCodec
    private val muxer: MediaMuxer
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0L
    private val frameIntervalUs = (1_000_000.0 / fps).toLong()
    private val useImageInput: Boolean
    private val yuvBuffer: ByteArray

    init {
        val colorFormat = selectColorFormat()
        useImageInput = (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)

        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt().coerceAtLeast(1))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        encoder = MediaCodec.createEncoderByType("video/avc")
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxer.setOrientationHint(rotationDegrees)
        yuvBuffer = ByteArray((width * height * 1.5).toInt())
    }

    /**
     * Encode a single BGR frame.
     */
    fun encodeFrame(mat: Mat) {
        Yuv420Converter.matToI420(mat, yuvBuffer)

        val inputBufferId = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferId < 0) {
            throw RuntimeException("Encoder input buffer unavailable")
        }

        if (useImageInput) {
            val image = encoder.getInputImage(inputBufferId)
                ?: throw RuntimeException("Encoder input image is null")
            Yuv420Converter.i420ToImage(yuvBuffer, image)
            encoder.queueInputBuffer(
                inputBufferId,
                0,
                yuvBuffer.size,
                frameIndex * frameIntervalUs,
                0
            )
        } else {
            val buffer = encoder.getInputBuffer(inputBufferId)
                ?: throw RuntimeException("Encoder input buffer is null")
            buffer.clear()
            buffer.put(yuvBuffer)
            encoder.queueInputBuffer(
                inputBufferId,
                0,
                yuvBuffer.size,
                frameIndex * frameIntervalUs,
                0
            )
        }

        drainEncoder(false)
        frameIndex++
    }

    /**
     * Signal end-of-stream and finish muxing.
     */
    fun finish() {
        val inputBufferId = encoder.dequeueInputBuffer(TIMEOUT_US)
        if (inputBufferId >= 0) {
            encoder.queueInputBuffer(
                inputBufferId,
                0,
                0,
                frameIndex * frameIntervalUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
        drainEncoder(true)
    }

    override fun close() {
        try {
            encoder.stop()
        } catch (_: Exception) {
        }
        encoder.release()
        try {
            muxer.stop()
        } catch (_: Exception) {
        }
        muxer.release()
    }

    private fun selectColorFormat(): Int {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val codecName = codecList.findEncoderForFormat(
            MediaFormat.createVideoFormat("video/avc", width, height)
        ) ?: throw RuntimeException("No AVC encoder available")

        val codecInfo = codecList.codecInfos.first { it.name == codecName }
        val capabilities = codecInfo.getCapabilitiesForType("video/avc")

        // Prefer planar (fast byte-buffer input). Fall back to flexible image input.
        return capabilities.colorFormats.firstOrNull {
            it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        } ?: capabilities.colorFormats.firstOrNull {
            it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        } ?: capabilities.colorFormats.firstOrNull {
            it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
        } ?: throw RuntimeException("No supported YUV420 color format")
    }

    private fun drainEncoder(endOfStream: Boolean) {
        while (true) {
            val outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        throw RuntimeException("Format changed twice")
                    }
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                outputBufferId >= 0 -> {
                    val encodedData: ByteBuffer = encoder.getOutputBuffer(outputBufferId)
                        ?: throw RuntimeException("Encoder output buffer is null")

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0) {
                        if (!muxerStarted) {
                            throw RuntimeException("Muxer not started")
                        }
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferId, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return
                    }
                }

                outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
            }
        }
    }

    companion object {
        private const val TIMEOUT_US = 10000L
    }
}
