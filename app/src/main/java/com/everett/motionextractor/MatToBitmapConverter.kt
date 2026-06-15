package com.everett.motionextractor

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * Convert an OpenCV BGR Mat to an Android ARGB_8888 Bitmap.
 */
object MatToBitmapConverter {

    fun toBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }
}
