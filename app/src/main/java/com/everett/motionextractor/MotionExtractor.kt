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
    val mode: OutputMode = OutputMode.GRAYSCALE
)

enum class OutputMode {
    COLOR, GRAYSCALE, INVERTED
}

object MotionExtractor {

    /**
     * Process a single pair of frames.
     *
     * @param current current frame in BGR / RGBA
     * @param offset frame at [offsetFrames] ahead
     * @param params processing parameters
     * @return processed frame, 8-bit 3-channel BGR
     */
    fun processFrame(current: Mat, offset: Mat, params: MotionExtractParams): Mat {
        val cur = Mat()
        val off = Mat()
        current.convertTo(cur, CvType.CV_32F, 1.0 / 255.0)
        offset.convertTo(off, CvType.CV_32F, 1.0 / 255.0)

        // Optional pre-blur to emphasize large-scale motion.
        if (params.blurRadius > 0.5) {
            Imgproc.GaussianBlur(cur, cur, kernelSize(params.blurRadius), params.blurRadius)
            Imgproc.GaussianBlur(off, off, kernelSize(params.blurRadius), params.blurRadius)
        }

        // Invert the offset frame if requested.
        val offInv = Mat()
        if (params.invert) {
            Core.subtract(Mat.ones(off.size(), off.type()), off, offInv)
        } else {
            off.copyTo(offInv)
        }

        // Blend current and inverted offset: result = (1-opacity)*cur + opacity*offInv
        val result = Mat()
        Core.addWeighted(cur, 1.0 - params.opacity, offInv, params.opacity, 0.0, result)

        // Post contrast / brightness.
        // result = (result - 0.5) * contrast + 0.5 + brightness
        Core.subtract(result, Mat(result.size(), result.type(), org.opencv.core.Scalar.all(0.5)), result)
        Core.multiply(result, Mat(result.size(), result.type(), org.opencv.core.Scalar.all(params.contrast)), result)
        Core.add(result, Mat(result.size(), result.type(), org.opencv.core.Scalar.all(0.5 + params.brightness)), result)
        Core.min(result, Mat.ones(result.size(), result.type()), result)
        Core.max(result, Mat.zeros(result.size(), result.type()), result)

        // Output mode.
        val converted = when (params.mode) {
            OutputMode.GRAYSCALE -> {
                val gray = Mat()
                result.convertTo(gray, CvType.CV_8U, 255.0)
                Imgproc.cvtColor(gray, gray, Imgproc.COLOR_BGR2GRAY)
                Imgproc.cvtColor(gray, gray, Imgproc.COLOR_GRAY2BGR)
                gray.convertTo(result, CvType.CV_32F, 1.0 / 255.0)
                result
            }
            OutputMode.INVERTED -> {
                Core.subtract(Mat.ones(result.size(), result.type()), result, result)
                result
            }
            else -> result
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
            Core.multiply(motion, Mat(motion.size(), motion.type(), org.opencv.core.Scalar.all(params.glowIntensity)), motion)
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
        result.release()

        return output
    }

    private fun kernelSize(radius: Double): Size {
        val k = ((radius * 2).toInt() or 1).coerceAtLeast(1)
        return Size(k.toDouble(), k.toDouble())
    }
}
