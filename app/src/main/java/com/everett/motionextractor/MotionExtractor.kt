package com.everett.motionextractor

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Parameters for motion extraction.
 *
 * Mirrors the parameters from the Python backend version.
 */
data class MotionExtractParams(
    val offsetFrames: Int = 1,
    val invert: Boolean = true,
    val opacity: Double = 0.5,
    val blurRadius: Double = 0.0,
    val glowRadius: Double = 0.0,
    val glowIntensity: Double = 0.5,
    val contrast: Double = 1.0,
    val brightness: Double = 0.0,
    val mode: OutputMode = OutputMode.GRAYSCALE,
    val useRgbOffsets: Boolean = false,
    val rgbOffsets: RgbOffsets = RgbOffsets()
)

data class RgbOffsets(
    val r: Int = 0,
    val g: Int = 0,
    val b: Int = 0
)

enum class OutputMode {
    COLOR, GRAYSCALE, INVERTED
}

object MotionExtractor {

    /**
     * Process a single pair of frames.
     *
     * @param current current frame in BGR
     * @param offset frame at [offsetFrames] ahead
     * @param params processing parameters
     * @return processed frame, 8-bit 3-channel BGR
     */
    fun processFrame(current: Mat, offset: Mat, params: MotionExtractParams): Mat {
        return processFrame(current, offset, null, null, null, params)
    }

    /**
     * Process with optional per-channel RGB offsets (artistic rainbow trails).
     *
     * @param current current frame in BGR
     * @param offset main offset frame
     * @param offsetR red channel offset frame (null = use main offset)
     * @param offsetG green channel offset frame (null = use main offset)
     * @param offsetB blue channel offset frame (null = use main offset)
     * @param params processing parameters
     * @return processed frame, 8-bit 3-channel BGR
     */
    fun processFrame(
        current: Mat,
        offset: Mat,
        offsetR: Mat?,
        offsetG: Mat?,
        offsetB: Mat?,
        params: MotionExtractParams
    ): Mat {
        val cur = Mat()
        val off = Mat()
        current.convertTo(cur, CvType.CV_32F, 1.0 / 255.0)
        offset.convertTo(off, CvType.CV_32F, 1.0 / 255.0)

        // Optional pre-blur to emphasize large-scale motion.
        if (params.blurRadius > 0.5) {
            Imgproc.GaussianBlur(cur, cur, kernelSize(params.blurRadius), params.blurRadius)
            Imgproc.GaussianBlur(off, off, kernelSize(params.blurRadius), params.blurRadius)
        }

        // Base motion extraction: blend current with inverted offset.
        val offInv = Mat()
        if (params.invert) {
            Core.subtract(Mat.ones(off.size(), off.type()), off, offInv)
        } else {
            off.copyTo(offInv)
        }

        val opacity = params.opacity.coerceIn(0.0, 1.0)
        val result = Mat()
        Core.addWeighted(cur, 1.0 - opacity, offInv, opacity, 0.0, result)

        // Per-channel RGB offsets (artistic rainbow trails).
        val rgbResult = if (params.useRgbOffsets) {
            val channels = mutableListOf<Mat>()
            val channelOffsets = listOf(offsetR, offsetG, offsetB)
            for (ci in 0..2) {
                val offChannel = channelOffsets[ci]
                if (offChannel == null || offChannel.empty()) {
                    // Fallback to the regular result channel.
                    val ch = Mat()
                    Core.extractChannel(result, ch, ci)
                    channels.add(ch)
                } else {
                    val offC = Mat()
                    offChannel.convertTo(offC, CvType.CV_32F, 1.0 / 255.0)
                    if (params.blurRadius > 0.5) {
                        Imgproc.GaussianBlur(offC, offC, kernelSize(params.blurRadius), params.blurRadius)
                    }
                    val curChannel = Mat()
                    val offChannelMat = Mat()
                    Core.extractChannel(cur, curChannel, ci)
                    Core.extractChannel(offC, offChannelMat, ci)
                    val channel = Mat()
                    Core.absdiff(curChannel, offChannelMat, channel)
                    Core.multiply(channel, Mat(channel.size(), channel.type(), org.opencv.core.Scalar.all(3.0)), channel)
                    Core.min(channel, Mat.ones(channel.size(), channel.type()), channel)
                    channels.add(channel)
                    curChannel.release()
                    offChannelMat.release()
                    offC.release()
                }
            }
            val merged = Mat()
            Core.merge(channels, merged)
            for (ch in channels) ch.release()
            merged
        } else {
            result
        }

        // Post contrast / brightness.
        Core.subtract(
            rgbResult,
            Mat(rgbResult.size(), rgbResult.type(), org.opencv.core.Scalar.all(0.5)),
            rgbResult
        )
        Core.multiply(
            rgbResult,
            Mat(rgbResult.size(), rgbResult.type(), org.opencv.core.Scalar.all(params.contrast)),
            rgbResult
        )
        Core.add(
            rgbResult,
            Mat(rgbResult.size(), rgbResult.type(), org.opencv.core.Scalar.all(0.5 + params.brightness)),
            rgbResult
        )
        Core.min(rgbResult, Mat.ones(rgbResult.size(), rgbResult.type()), rgbResult)
        Core.max(rgbResult, Mat.zeros(rgbResult.size(), rgbResult.type()), rgbResult)

        // Output mode.
        val converted = when (params.mode) {
            OutputMode.GRAYSCALE -> {
                val gray = Mat()
                rgbResult.convertTo(gray, CvType.CV_8U, 255.0)
                Imgproc.cvtColor(gray, gray, Imgproc.COLOR_BGR2GRAY)
                Imgproc.cvtColor(gray, gray, Imgproc.COLOR_GRAY2BGR)
                gray.convertTo(rgbResult, CvType.CV_32F, 1.0 / 255.0)
                rgbResult
            }
            OutputMode.INVERTED -> {
                Core.subtract(Mat.ones(rgbResult.size(), rgbResult.type()), rgbResult, rgbResult)
                rgbResult
            }
            else -> rgbResult
        }

        // Glow effect based on motion magnitude.
        if (params.glowRadius > 0.5) {
            val diff = Mat()
            Core.absdiff(cur, off, diff)
            val channels = ArrayList<Mat>(3)
            Core.split(diff, channels)
            val motion = Mat()
            Core.addWeighted(channels[0], 1.0 / 3.0, channels[1], 1.0 / 3.0, 0.0, motion)
            Core.addWeighted(motion, 1.0, channels[2], 1.0 / 3.0, 0.0, motion)
            for (m in channels) m.release()
            diff.release()

            Imgproc.GaussianBlur(motion, motion, kernelSize(params.glowRadius), params.glowRadius)
            Core.multiply(
                motion,
                Mat(motion.size(), motion.type(), org.opencv.core.Scalar.all(params.glowIntensity)),
                motion
            )
            Core.min(motion, Mat.ones(motion.size(), motion.type()), motion)

            val glow = Mat()
            Core.merge(listOf(motion, motion, motion), glow)
            Core.add(converted, glow, converted)
            Core.min(converted, Mat.ones(converted.size(), converted.type()), converted)
            motion.release()
            glow.release()
        }

        // Convert back to 8-bit BGR.
        val output = Mat()
        converted.convertTo(output, CvType.CV_8U, 255.0)

        cur.release()
        off.release()
        offInv.release()
        if (rgbResult != result) rgbResult.release()
        result.release()

        return output
    }

    private fun kernelSize(radius: Double): Size {
        val k = ((radius * 2).toInt() or 1).coerceAtLeast(1)
        return Size(k.toDouble(), k.toDouble())
    }
}
