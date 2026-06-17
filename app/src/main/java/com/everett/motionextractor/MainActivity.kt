package com.everett.motionextractor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import com.everett.motionextractor.ui.ExoPlayerManager
import com.everett.motionextractor.ui.LocalExoPlayer
import com.everett.motionextractor.ui.MotionExtractorApp
import com.everett.motionextractor.ui.theme.MotionExtractorTheme
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var videoProcessor: VideoProcessor
    private lateinit var exoPlayerManager: ExoPlayerManager

    // Use MutableState to hold selected URI - survives recomposition
    private val selectedUriState = mutableStateOf<Uri?>(null)

    private var pendingSaveFile: File? = null
    private val openCvReady: Boolean
        get() = OpenCVLoader.initLocal()

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            pendingSaveFile?.let { saveToGallery(it) }
        } else {
            Toast.makeText(this, "需要存储权限才能保存视频", Toast.LENGTH_SHORT).show()
        }
    }

    // Video picker launcher
    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        // Update state instead of recreating content
        selectedUriState.value = uri
    }

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        videoProcessor = VideoProcessor(this)
        exoPlayerManager = ExoPlayerManager(this)

        setContent {
            MotionExtractorTheme {
                CompositionLocalProvider(LocalExoPlayer provides exoPlayerManager.player) {
                    MotionExtractorApp(
                        lifecycleScope = lifecycleScope,
                        videoProcessor = videoProcessor,
                        onPickVideo = { pickVideoLauncher.launch("video/*") },
                        onSaveToGallery = { file -> saveToGallery(file) },
                        selectedUri = selectedUriState.value,
                        openCvReady = openCvReady
                    )
                }
            }
        }
    }

    private fun saveToGallery(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Use MediaStore
            val values = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/MotionExtractor")
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let { destUri ->
                contentResolver.openOutputStream(destUri)?.use { output ->
                    file.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 9 and below: Check permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val destDir = File(getExternalFilesDir(null), "MotionExtractor")
                if (!destDir.exists()) destDir.mkdirs()
                val destFile = File(destDir, file.name)
                file.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                // Notify gallery
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(destFile)))
                Toast.makeText(this, "已保存到相册", Toast.LENGTH_SHORT).show()
            } else {
                pendingSaveFile = file
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayerManager.release()
    }
}
