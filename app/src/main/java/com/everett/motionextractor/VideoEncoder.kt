package com.everett.motionextractor

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import org.opencv.core.Mat
import java.io.File
import java.nio.ByteBuffer

/**
 * Encodes BGR [Mat] frames to an MP4 file using Android's MediaCodec +
 * MediaMuxer. Input is supplied through a Surface; frames are rendered by
 * drawing a Bitmap onto that Surface.
 */
class VideoEncoder(
    outputFile: File,
    private val width: Int,
    private val height: Int,
    fps: Double,
    bitrate: Int = width * height * 4
) : AutoCloseable {

    private val encoder: MediaCodec
    private val muxer: MediaMuxer
    private val inputSurface: Surface
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0L
    private val frameIntervalUs = (1_000_000.0 / fps).toLong()

    init {
        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, selectColorFormat())
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.toInt().coerceAtLeast(1))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        encoder = MediaCodec.createEncoderByType("video/avc")
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder.createInputSurface()
        encoder.start()

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    /**
     * Encode a single BGR frame.
     */
    fun encodeFrame(mat: Mat) {
        val bitmap = MatToBitmapConverter.toBitmap(mat)
        val canvas = inputSurface.lockCanvas(null)
            ?: throw RuntimeException("Cannot lock encoder surface")
        try {
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        } finally {
            inputSurface.unlockCanvasAndPost(canvas)
        }
        bitmap.recycle()

        drainEncoder(false)
        frameIndex++
    }

    /**
     * Signal end-of-stream and finish muxing.
     */
    fun finish() {
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

        return capabilities.colorFormats.firstOrNull { it == COLOR_FORMAT_SURFACE }
            ?: capabilities.colorFormats.firstOrNull {
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            }
            ?: capabilities.colorFormats.firstOrNull {
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            }
            ?: capabilities.colorFormats.firstOrNull {
                it == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            }
            ?: throw RuntimeException("No supported YUV420 color format")
    }

    private fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) {
            encoder.signalEndOfInputStream()
        }

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
        private const val COLOR_FORMAT_SURFACE =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
    }
}
