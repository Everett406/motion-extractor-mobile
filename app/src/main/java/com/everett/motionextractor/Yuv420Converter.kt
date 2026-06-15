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
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        return if (uPlane.pixelStride == 1 && vPlane.pixelStride == 1) {
            // Planar source: build I420 and convert.
            val i420 = ByteArray((width * height * 1.5).toInt())
            readPlane(yPlane, i420, 0, width * height, width)
            readPlane(uPlane, i420, width * height, width * height / 4, width / 2)
            readPlane(vPlane, i420, width * height * 5 / 4, width * height / 4, width / 2)
            i420ToBgr(i420, width, height)
        } else if (isSharedInterleaved(uPlane, vPlane)) {
            // Shared semi-planar source: build NV12 or NV21 and convert.
            val uFirst = uPlane.buffer.position() <= vPlane.buffer.position()
            val nv = ByteArray((width * height * 1.5).toInt())
            val uvOffset = width * height
            readPlane(yPlane, nv, 0, width * height, width)
            copySharedUvRows(uPlane, nv, uvOffset, width)
            val code = if (uFirst) Imgproc.COLOR_YUV2BGR_NV12 else Imgproc.COLOR_YUV2BGR_NV21
            nvToBgr(nv, width, height, code)
        } else {
            // Generic semi-planar source: sample to I420.
            val i420 = imageToI420Generic(image)
            i420ToBgr(i420, width, height)
        }
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
        } else if (isSharedInterleaved(uPlane, vPlane)) {
            // Shared semi-planar target: copy interleaved UV rows directly.
            val uFirst = uPlane.buffer.position() <= vPlane.buffer.position()
            val uOffset = ySize
            val vOffset = ySize + ySize / 4
            val uvRowBytes = ByteArray(width)
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    if (uFirst) {
                        uvRowBytes[col * 2] = i420[uOffset + row * uvWidth + col]
                        uvRowBytes[col * 2 + 1] = i420[vOffset + row * uvWidth + col]
                    } else {
                        uvRowBytes[col * 2] = i420[vOffset + row * uvWidth + col]
                        uvRowBytes[col * 2 + 1] = i420[uOffset + row * uvWidth + col]
                    }
                }
                val basePos = row * uPlane.rowStride + if (uFirst) 0 else 1
                uPlane.buffer.position(basePos)
                uPlane.buffer.put(uvRowBytes)
            }
        } else {
            // Generic semi-planar target: interleave U and V rows.
            val uOffset = ySize
            val vOffset = ySize + ySize / 4
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                    val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                    uPlane.buffer.put(uIndex, i420[uOffset + row * uvWidth + col])
                    vPlane.buffer.put(vIndex, i420[vOffset + row * uvWidth + col])
                }
            }
        }
    }

    private fun i420ToBgr(i420: ByteArray, width: Int, height: Int): Mat {
        val yuvMat = Mat((height * 1.5).toInt(), width, CvType.CV_8UC1)
        yuvMat.put(0, 0, i420)
        val bgr = Mat()
        Imgproc.cvtColor(yuvMat, bgr, Imgproc.COLOR_YUV2BGR_I420)
        yuvMat.release()
        return bgr
    }

    private fun nvToBgr(nv: ByteArray, width: Int, height: Int, code: Int): Mat {
        val yuvMat = Mat((height * 1.5).toInt(), width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv)
        val bgr = Mat()
        Imgproc.cvtColor(yuvMat, bgr, code)
        yuvMat.release()
        return bgr
    }

    /**
     * Copy interleaved UV rows from a shared UV plane into a contiguous
     * NV12/NV21 byte array.
     */
    private fun copySharedUvRows(plane: Image.Plane, dst: ByteArray, dstOffset: Int, width: Int) {
        val rowBytes = ByteArray(width)
        val uvHeight = dst.size / width / 2
        for (row in 0 until uvHeight) {
            plane.buffer.position(row * plane.rowStride)
            plane.buffer.get(rowBytes, 0, width)
            System.arraycopy(rowBytes, 0, dst, dstOffset + row * width, width)
        }
    }

    /**
     * Heuristic to detect whether U and V planes share the same interleaved
     * buffer (the common Android YUV_420_888 semi-planar case).
     */
    private fun isSharedInterleaved(uPlane: Image.Plane, vPlane: Image.Plane): Boolean {
        if (uPlane === vPlane) return true
        if (uPlane.rowStride != vPlane.rowStride) return false
        if (uPlane.pixelStride != 2 || vPlane.pixelStride != 2) return false
        val uPos = uPlane.buffer.position()
        val vPos = vPlane.buffer.position()
        return kotlin.math.abs(uPos - vPos) == 1
    }

    /**
     * Fallback generic converter for semi-planar images that don't use a
     * shared UV buffer.
     */
    private fun imageToI420Generic(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvWidth = width / 2
        val uvHeight = height / 2
        val i420 = ByteArray(ySize + ySize / 2)

        readPlane(image.planes[0], i420, 0, ySize, width)

        val uOffset = ySize
        val vOffset = ySize + ySize / 4
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val uIndex = row * image.planes[1].rowStride + col * image.planes[1].pixelStride
                val vIndex = row * image.planes[2].rowStride + col * image.planes[2].pixelStride
                i420[uOffset + row * uvWidth + col] = image.planes[1].buffer.get(uIndex)
                i420[vOffset + row * uvWidth + col] = image.planes[2].buffer.get(vIndex)
            }
        }
        return i420
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
