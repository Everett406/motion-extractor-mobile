package com.everett.motionextractor

import android.media.Image
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Converts between Android's YUV_420_888 [Image] and OpenCV BGR [Mat].
 *
 * Internally normalises everything to I420 (YYYY... U... V...) so OpenCV can do
 * the heavy lifting with [Imgproc.cvtColor]. This handles both planar and
 * semi-planar YUV_420_888 layouts as well as arbitrary row strides.
 */
object Yuv420Converter {

    /**
     * Convert a YUV_420_888 image to a 3-channel BGR Mat.
     */
    fun imageToBgrMat(image: Image): Mat {
        val width = image.width
        val height = image.height
        val i420 = imageToI420(image)

        val yuvMat = Mat((height * 1.5).toInt(), width, CvType.CV_8UC1)
        yuvMat.put(0, 0, i420)
        val bgr = Mat()
        Imgproc.cvtColor(yuvMat, bgr, Imgproc.COLOR_YUV2BGR_I420)
        yuvMat.release()
        return bgr
    }

    /**
     * Convert a 3-channel BGR Mat to an I420 byte array.
     *
     * If [dst] is provided it will be filled, otherwise a new array is allocated.
     */
    fun matToI420(mat: Mat, dst: ByteArray? = null): ByteArray {
        val yuvMat = Mat()
        Imgproc.cvtColor(mat, yuvMat, Imgproc.COLOR_BGR2YUV_I420)
        val size = (yuvMat.total() * yuvMat.elemSize()).toInt()
        val bytes = dst ?: ByteArray(size)
        yuvMat.get(0, 0, bytes)
        yuvMat.release()
        return bytes
    }

    /**
     * Write an I420 byte array into an [Image] (YUV_420_888), handling planar
     * or semi-planar target layouts.
     */
    fun i420ToImage(i420: ByteArray, image: Image) {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvWidth = width / 2
        val uvHeight = height / 2

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // Y plane
        writePlane(yPlane, i420, 0, ySize, width)

        if (uPlane.pixelStride == 1 && vPlane.pixelStride == 1) {
            // Planar target (I420 / YV12-like)
            writePlane(uPlane, i420, ySize, ySize / 4, uvWidth)
            writePlane(vPlane, i420, ySize + ySize / 4, ySize / 4, uvWidth)
        } else {
            // Semi-planar target: interleave U and V rows.
            val uOffset = ySize
            val vOffset = ySize + ySize / 4
            val uvRowBytes = ByteArray(width)
            val sharedBuffer = uPlane.buffer === vPlane.buffer
            val uFirst = sharedBuffer && uPlane.buffer.position() <= vPlane.buffer.position()

            for (row in 0 until uvHeight) {
                val uSrc = uOffset + row * uvWidth
                val vSrc = vOffset + row * uvWidth
                for (col in 0 until uvWidth) {
                    uvRowBytes[col * 2] = i420[uSrc + col]
                    uvRowBytes[col * 2 + 1] = i420[vSrc + col]
                }

                if (sharedBuffer) {
                    val basePos = row * uPlane.rowStride + if (uFirst) 0 else 1
                    uPlane.buffer.position(basePos)
                    uPlane.buffer.put(uvRowBytes)
                } else {
                    for (col in 0 until uvWidth) {
                        val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                        val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                        uPlane.buffer.put(uIndex, uvRowBytes[col * 2])
                        vPlane.buffer.put(vIndex, uvRowBytes[col * 2 + 1])
                    }
                }
            }
        }
    }

    /**
     * Convert any YUV_420_888 [Image] to a packed I420 byte array.
     */
    private fun imageToI420(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvWidth = width / 2
        val uvHeight = height / 2
        val i420 = ByteArray(ySize + ySize / 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        // Y plane
        readPlane(yPlane, i420, 0, ySize, width)

        if (uPlane.pixelStride == 1 && vPlane.pixelStride == 1) {
            // Planar source
            readPlane(uPlane, i420, ySize, ySize / 4, uvWidth)
            readPlane(vPlane, i420, ySize + ySize / 4, ySize / 4, uvWidth)
        } else if (uPlane.pixelStride == 2 && vPlane.pixelStride == 2 &&
            areBuffersInterleaved(uPlane, vPlane)
        ) {
            // Shared interleaved UV buffer: copy whole rows directly.
            val shared = if (uPlane.buffer.position() <= vPlane.buffer.position()) uPlane.buffer else vPlane.buffer
            val firstIsU = uPlane.buffer.position() <= vPlane.buffer.position()
            val shift = if (firstIsU) 0 else 1
            val uOffset = ySize
            val vOffset = ySize + ySize / 4
            val rowBytes = ByteArray(width)
            for (row in 0 until uvHeight) {
                shared.position(row * uPlane.rowStride + shift)
                shared.get(rowBytes, 0, width)
                for (col in 0 until uvWidth) {
                    if (firstIsU) {
                        i420[uOffset + row * uvWidth + col] = rowBytes[col * 2]
                        i420[vOffset + row * uvWidth + col] = rowBytes[col * 2 + 1]
                    } else {
                        i420[vOffset + row * uvWidth + col] = rowBytes[col * 2]
                        i420[uOffset + row * uvWidth + col] = rowBytes[col * 2 + 1]
                    }
                }
            }
        } else {
            // Generic semi-planar source: sample U and V independently.
            val uOffset = ySize
            val vOffset = ySize + ySize / 4
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                    val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                    i420[uOffset + row * uvWidth + col] = uPlane.buffer.get(uIndex)
                    i420[vOffset + row * uvWidth + col] = vPlane.buffer.get(vIndex)
                }
            }
        }

        return i420
    }

    /**
     * Heuristic to detect whether U and V planes share the same interleaved
     * buffer (the common Android YUV_420_888 semi-planar case).
     */
    private fun areBuffersInterleaved(uPlane: Image.Plane, vPlane: Image.Plane): Boolean {
        if (uPlane.rowStride != vPlane.rowStride) return false
        val uPos = uPlane.buffer.position()
        val vPos = vPlane.buffer.position()
        return kotlin.math.abs(uPos - vPos) == 1
    }

    /**
     * Copy a rectangular region from [src] starting at [srcOffset] into an
     * [Image.Plane], skipping any row padding.
     */
    private fun writePlane(
        plane: Image.Plane,
        src: ByteArray,
        srcOffset: Int,
        count: Int,
        copyWidth: Int
    ) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val height = count / copyWidth
        for (row in 0 until height) {
            val srcPos = srcOffset + row * copyWidth
            val dstPos = row * rowStride
            buffer.position(dstPos)
            buffer.put(src, srcPos, copyWidth)
        }
    }

    /**
     * Copy a rectangular region from an [Image.Plane] into [dst] starting at
     * [dstOffset], skipping any row padding.
     */
    private fun readPlane(
        plane: Image.Plane,
        dst: ByteArray,
        dstOffset: Int,
        count: Int,
        copyWidth: Int
    ) {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val height = count / copyWidth
        for (row in 0 until height) {
            val srcPos = row * rowStride
            val dstPos = dstOffset + row * copyWidth
            buffer.position(srcPos)
            buffer.get(dst, dstPos, copyWidth)
        }
    }
}
