package com.everett.motionextractor

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat

class MainActivity : AppCompatActivity() {

    private lateinit var btnPickVideo: Button
    private lateinit var btnPreview: Button
    private lateinit var btnExport: Button
    private lateinit var btnSave: Button
    private lateinit var ivPreview: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvVideoInfo: TextView

    private lateinit var sliderOffset: Slider
    private lateinit var sliderOpacity: Slider
    private lateinit var sliderBlur: Slider
    private lateinit var sliderGlowRadius: Slider
    private lateinit var sliderGlowIntensity: Slider
    private lateinit var sliderContrast: Slider
    private lateinit var sliderBrightness: Slider
    private lateinit var cbInvert: CheckBox

    private var selectedUri: Uri? = null
    private var lastOutputFile: java.io.File? = null
    private var openCvReady = false
    private lateinit var videoProcessor: VideoProcessor

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            if (openCvReady) {
                btnPreview.isEnabled = true
                btnExport.isEnabled = true
            }
            showVideoInfo(uri)
        }
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
        setContentView(R.layout.activity_main)

        openCvReady = initializeOpenCV()
        if (!openCvReady) {
            ErrorReporter.showErrorDialog(this, ErrorReporter.getLogs())
        }

        videoProcessor = VideoProcessor(this)
        bindViews()
        setupListeners()
    }

    private fun bindViews() {
        btnPickVideo = findViewById(R.id.btnPickVideo)
        btnPreview = findViewById(R.id.btnPreview)
        btnExport = findViewById(R.id.btnExport)
        btnSave = findViewById(R.id.btnSave)
        ivPreview = findViewById(R.id.ivPreview)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tvVideoInfo = findViewById(R.id.tvVideoInfo)

        sliderOffset = findViewById(R.id.sliderOffset)
        sliderOpacity = findViewById(R.id.sliderOpacity)
        sliderBlur = findViewById(R.id.sliderBlur)
        sliderGlowRadius = findViewById(R.id.sliderGlowRadius)
        sliderGlowIntensity = findViewById(R.id.sliderGlowIntensity)
        sliderContrast = findViewById(R.id.sliderContrast)
        sliderBrightness = findViewById(R.id.sliderBrightness)
        cbInvert = findViewById(R.id.cbInvert)
    }

    private fun setupListeners() {
        btnPickVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        btnPreview.setOnClickListener {
            generatePreview()
        }

        btnExport.setOnClickListener {
            exportVideo()
        }

        btnSave.setOnClickListener {
            saveToGallery()
        }
    }

    private fun showVideoInfo(uri: Uri) {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                videoProcessor.getVideoInfo(uri)
            }
            info?.let {
                tvVideoInfo.text = "分辨率: ${it.width}×${it.height}  帧率: %.2f  时长: %.1fs".format(it.fps, it.durationMs / 1000.0)
                tvVideoInfo.visibility = View.VISIBLE
            }
        }
    }

    private fun generatePreview() {
        val uri = selectedUri ?: return
        val params = collectParams()

        setUiProcessing(true, "正在生成预览...")

        lifecycleScope.launch {
            val frames = withContext(Dispatchers.IO) {
                videoProcessor.generatePreviewFrames(uri, params)
            }

            if (frames.isNotEmpty()) {
                val middle = frames[frames.size / 2]
                val bitmap = android.graphics.Bitmap.createBitmap(
                    middle.cols(), middle.rows(),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                Utils.matToBitmap(middle, bitmap)
                ivPreview.setImageBitmap(bitmap)
            }

            frames.forEach { it.release() }
            setUiProcessing(false, "预览完成")
        }
    }

    private fun exportVideo() {
        val uri = selectedUri ?: return
        val params = collectParams()

        setUiProcessing(true, "正在导出视频...")

        lifecycleScope.launch {
            try {
                val outputFile = withContext(Dispatchers.IO) {
                    videoProcessor.exportVideo(uri, params) { progress ->
                        runOnUiThread {
                            progressBar.progress = (progress * 100).toInt()
                            tvStatus.text = "导出进度: ${(progress * 100).toInt()}%"
                        }
                    }
                }

                lastOutputFile = outputFile
                btnSave.visibility = View.VISIBLE
                btnSave.isEnabled = true
                setUiProcessing(false, "导出完成: ${outputFile.name}")

                // Auto share intent.
                shareFile(outputFile)
            } catch (e: Exception) {
                e.printStackTrace()
                setUiProcessing(false, "导出失败: ${e.message}")
                Toast.makeText(this@MainActivity, "导出失败", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveToGallery() {
        val file = lastOutputFile ?: return

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

    private fun shareFile(file: java.io.File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享视频"))
    }

    private fun collectParams(): MotionExtractParams {
        return MotionExtractParams(
            offsetFrames = sliderOffset.value.toInt(),
            invert = cbInvert.isChecked,
            opacity = sliderOpacity.value.toDouble(),
            blurRadius = sliderBlur.value.toDouble(),
            glowRadius = sliderGlowRadius.value.toDouble(),
            glowIntensity = sliderGlowIntensity.value.toDouble(),
            contrast = sliderContrast.value.toDouble(),
            brightness = sliderBrightness.value.toDouble(),
            mode = OutputMode.GRAYSCALE
        )
    }

    private fun setUiProcessing(isProcessing: Boolean, status: String) {
        progressBar.visibility = if (isProcessing) View.VISIBLE else View.GONE
        progressBar.progress = 0
        tvStatus.visibility = View.VISIBLE
        tvStatus.text = status

        btnPickVideo.isEnabled = !isProcessing
        btnPreview.isEnabled = openCvReady && !isProcessing && selectedUri != null
        btnExport.isEnabled = openCvReady && !isProcessing && selectedUri != null
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

        // Method 1: initLocal (bundled OpenCV).
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

        // Method 2: explicit loadLibrary then initLocal.
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

        // Method 3: initDebug (last resort, deprecated).
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
