package com.everett.motionextractor

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.everett.motionextractor.ui.LocalExoPlayer
import com.everett.motionextractor.ui.MainScreen
import com.everett.motionextractor.ui.createExoPlayer
import com.everett.motionextractor.ui.theme.MotionExtractorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var videoProcessor: VideoProcessor
    private var openCvReady = false
    private var selectedUri: Uri? = null

    private val exoPlayer by lazy { createExoPlayer(this) }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "需要存储权限以保存视频", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openCvReady = initializeOpenCV()
        if (!openCvReady) {
            ErrorReporter.showErrorDialog(this, ErrorReporter.getLogs())
        }

        videoProcessor = VideoProcessor(this)

        setContent {
            CompositionLocalProvider(LocalExoPlayer provides exoPlayer) {
                MotionExtractorTheme {
                    MainScreen(
                        lifecycleScope = lifecycleScope,
                        videoProcessor = videoProcessor,
                        onPickVideo = {
                            exoPlayer.stop()
                            pickVideoLauncher.launch("video/*")
                        },
                        onSaveToGallery = { file -> saveToGallery(file) },
                        selectedUri = selectedUri,
                        openCvReady = openCvReady
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }

    private fun saveToGallery(file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && !checkStoragePermission()) {
            requestStoragePermission()
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "motion_extract_${System.currentTimeMillis()}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MotionExtractor")
                    }
                    val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let { outputUri ->
                        contentResolver.openOutputStream(outputUri)?.use { output ->
                            file.inputStream().copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "已保存到相册", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            true
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun initializeOpenCV(): Boolean {
        val sb = StringBuilder()
        sb.appendLine("=== OpenCV Init Debug ===")
        sb.appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS?.contentToString()}")

        try {
            val localOk = OpenCVLoader.initLocal()
            sb.appendLine("initLocal() returned: $localOk")
            if (localOk) {
                ErrorReporter.log(sb.toString())
                return true
            }
        } catch (e: Exception) {
            sb.appendLine("initLocal() threw: ${e.stackTraceToString()}")
        }

        val libNames = listOf("opencv_java4", "opencv_java4100", "opencv_java")
        for (lib in libNames) {
            try {
                System.loadLibrary(lib)
                sb.appendLine("System.loadLibrary('$lib') succeeded")
                val localOk = OpenCVLoader.initLocal()
                sb.appendLine("initLocal() after loadLibrary('$lib') returned: $localOk")
                if (localOk) {
                    ErrorReporter.log(sb.toString())
                    return true
                }
            } catch (e: UnsatisfiedLinkError) {
                sb.appendLine("System.loadLibrary('$lib') failed: ${e.message}")
            } catch (e: Exception) {
                sb.appendLine("System.loadLibrary('$lib') threw: ${e.stackTraceToString()}")
            }
        }

        try {
            val debugOk = OpenCVLoader.initDebug()
            sb.appendLine("initDebug() returned: $debugOk")
            if (debugOk) {
                ErrorReporter.log(sb.toString())
                return true
            }
        } catch (e: Exception) {
            sb.appendLine("initDebug() threw: ${e.stackTraceToString()}")
        }

        sb.appendLine("All OpenCV init methods failed.")
        ErrorReporter.log(sb.toString())
        return false
    }
}
