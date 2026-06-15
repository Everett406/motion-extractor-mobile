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
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat

class MainActivity : AppCompatActivity() {

    private lateinit var btnPickVideo: Button
    private lateinit var btnPreview: Button
    private lateinit var btnExport: Button
    private lateinit var btnSave: Button
    private lateinit var btnPlay: Button
    private lateinit var ivPreview: ImageView
    private lateinit var videoView: VideoView
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

    private lateinit var rgMode: RadioGroup
    private lateinit var rbColor: RadioButton
    private lateinit var rbGrayscale: RadioButton
    private lateinit var rbInverted: RadioButton

    private lateinit var cbRgbOffsets: CheckBox
    private lateinit var sliderOffsetR: Slider
    private lateinit var sliderOffsetG: Slider
    private lateinit var sliderOffsetB: Slider

    private lateinit var tvHeaderBasic: TextView
    private lateinit var sectionBasic: View
    private lateinit var tvHeaderEnhance: TextView
    private lateinit var sectionEnhance: View
    private lateinit var tvHeaderRgb: TextView
    private lateinit var sectionRgb: View

    private lateinit var btnPresetClassic: MaterialButton
    private lateinit var btnPresetNeon: MaterialButton
    private lateinit var btnPresetHighContrast: MaterialButton
    private lateinit var btnPresetSoft: MaterialButton
    private lateinit var btnPresetReset: MaterialButton

    private var selectedUri: Uri? = null
    private var lastOutputFile: java.io.File? = null
    private var lastVideoRotation: Int = 0
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
        updateRgbOffsetSliderState()
    }

    private fun bindViews() {
        btnPickVideo = findViewById(R.id.btnPickVideo)
        btnPreview = findViewById(R.id.btnPreview)
        btnExport = findViewById(R.id.btnExport)
        btnSave = findViewById(R.id.btnSave)
        btnPlay = findViewById(R.id.btnPlay)
        ivPreview = findViewById(R.id.ivPreview)
        videoView = findViewById(R.id.videoView)
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

        rgMode = findViewById(R.id.rgMode)
        rbColor = findViewById(R.id.rbColor)
        rbGrayscale = findViewById(R.id.rbGrayscale)
        rbInverted = findViewById(R.id.rbInverted)

        cbRgbOffsets = findViewById(R.id.cbRgbOffsets)
        sliderOffsetR = findViewById(R.id.sliderOffsetR)
        sliderOffsetG = findViewById(R.id.sliderOffsetG)
        sliderOffsetB = findViewById(R.id.sliderOffsetB)

        tvHeaderBasic = findViewById(R.id.tvHeaderBasic)
        sectionBasic = findViewById(R.id.sectionBasic)
        tvHeaderEnhance = findViewById(R.id.tvHeaderEnhance)
        sectionEnhance = findViewById(R.id.sectionEnhance)
        tvHeaderRgb = findViewById(R.id.tvHeaderRgb)
        sectionRgb = findViewById(R.id.sectionRgb)

        btnPresetClassic = findViewById(R.id.btnPresetClassic)
        btnPresetNeon = findViewById(R.id.btnPresetNeon)
        btnPresetHighContrast = findViewById(R.id.btnPresetHighContrast)
        btnPresetSoft = findViewById(R.id.btnPresetSoft)
        btnPresetReset = findViewById(R.id.btnPresetReset)
    }

    private fun setupListeners() {
        btnPickVideo.setOnClickListener {
            resetVideoPlayer()
            pickVideoLauncher.launch("video/*")
        }

        btnPreview.setOnClickListener {
            resetVideoPlayer()
            generatePreview()
        }

        btnExport.setOnClickListener {
            resetVideoPlayer()
            exportVideo()
        }

        btnSave.setOnClickListener {
            saveToGallery()
        }

        btnPlay.setOnClickListener {
            playExportedVideo()
        }

        cbRgbOffsets.setOnCheckedChangeListener { _, _ ->
            updateRgbOffsetSliderState()
        }

        btnPresetClassic.setOnClickListener { applyPreset(Preset.CLASSIC) }
        btnPresetNeon.setOnClickListener { applyPreset(Preset.NEON) }
        btnPresetHighContrast.setOnClickListener { applyPreset(Preset.HIGH_CONTRAST) }
        btnPresetSoft.setOnClickListener { applyPreset(Preset.SOFT) }
        btnPresetReset.setOnClickListener { applyPreset(Preset.RESET) }

        tvHeaderBasic.setOnClickListener { toggleSection(tvHeaderBasic, sectionBasic) }
        tvHeaderEnhance.setOnClickListener { toggleSection(tvHeaderEnhance, sectionEnhance) }
        tvHeaderRgb.setOnClickListener { toggleSection(tvHeaderRgb, sectionRgb) }
    }

    private fun toggleSection(header: TextView, section: View) {
        val isVisible = section.visibility == View.VISIBLE
        section.visibility = if (isVisible) View.GONE else View.VISIBLE
        val title = header.text.toString().substring(2)
        header.text = "${if (isVisible) "▶" else "▼"} $title"
    }

    private fun updateRgbOffsetSliderState() {
        val enabled = cbRgbOffsets.isChecked
        sliderOffsetR.isEnabled = enabled
        sliderOffsetG.isEnabled = enabled
        sliderOffsetB.isEnabled = enabled
    }

    private fun showVideoInfo(uri: Uri) {
        lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) {
                videoProcessor.getVideoInfo(uri)
            }
            info?.let {
                lastVideoRotation = it.rotationDegrees
                val orientation = when (it.rotationDegrees) {
                    0, 180 -> ""
                    else -> " 竖屏"
                }
                tvVideoInfo.text = "分辨率: ${it.width}×${it.height}  帧率: %.2f  时长: %.1fs$orientation".format(it.fps, it.durationMs / 1000.0)
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
                val collage = createPreviewCollage(frames)
                ivPreview.setImageBitmap(collage)
            } else {
                Toast.makeText(this@MainActivity, "无法生成预览，请检查视频是否有效", Toast.LENGTH_LONG).show()
            }

            frames.forEach { it.release() }
            setUiProcessing(false, "预览完成")
        }
    }

    private fun createPreviewCollage(frames: List<Mat>): android.graphics.Bitmap {
        val rotatedFrames = frames.map { rotateMat(it, lastVideoRotation) }

        if (rotatedFrames.size == 1) {
            val mat = rotatedFrames[0]
            val bitmap = android.graphics.Bitmap.createBitmap(
                mat.cols(), mat.rows(),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(mat, bitmap)
            if (mat !== frames[0]) mat.release()
            return bitmap
        }

        val frameW = rotatedFrames[0].cols()
        val frameH = rotatedFrames[0].rows()
        val collage = android.graphics.Bitmap.createBitmap(
            frameW * rotatedFrames.size, frameH,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(collage)
        for ((index, mat) in rotatedFrames.withIndex()) {
            val bitmap = android.graphics.Bitmap.createBitmap(
                frameW, frameH,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(mat, bitmap)
            canvas.drawBitmap(bitmap, (index * frameW).toFloat(), 0f, null)
            bitmap.recycle()
            if (mat !== frames[index]) mat.release()
        }
        return collage
    }

    private fun rotateMat(mat: Mat, degrees: Int): Mat {
        return when (degrees) {
            90 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_90_CLOCKWISE) }
            180 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_180) }
            270 -> Mat().also { Core.rotate(mat, it, Core.ROTATE_90_COUNTER_CLOCKWISE) }
            else -> mat
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
                btnPlay.visibility = View.VISIBLE
                btnPlay.isEnabled = true
                setUiProcessing(false, "导出完成: ${outputFile.name}")
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

    private fun resetVideoPlayer() {
        videoView.stopPlayback()
        videoView.visibility = View.GONE
        ivPreview.visibility = View.VISIBLE
    }

    private fun playExportedVideo() {
        val file = lastOutputFile ?: return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file
        )
        ivPreview.visibility = View.GONE
        videoView.visibility = View.VISIBLE
        videoView.setVideoURI(uri)
        videoView.setOnPreparedListener { mediaPlayer ->
            mediaPlayer.isLooping = true
            videoView.start()
        }
        videoView.setOnErrorListener { _, what, extra ->
            Toast.makeText(this, "播放失败: $what/$extra", Toast.LENGTH_SHORT).show()
            resetVideoPlayer()
            true
        }
    }

    private fun collectParams(): MotionExtractParams {
        val mode = when (rgMode.checkedRadioButtonId) {
            R.id.rbColor -> OutputMode.COLOR
            R.id.rbInverted -> OutputMode.INVERTED
            else -> OutputMode.GRAYSCALE
        }
        return MotionExtractParams(
            offsetFrames = sliderOffset.value.toInt(),
            invert = cbInvert.isChecked,
            opacity = sliderOpacity.value.toDouble(),
            blurRadius = sliderBlur.value.toDouble(),
            glowRadius = sliderGlowRadius.value.toDouble(),
            glowIntensity = sliderGlowIntensity.value.toDouble(),
            contrast = sliderContrast.value.toDouble(),
            brightness = sliderBrightness.value.toDouble(),
            mode = mode,
            useRgbOffsets = cbRgbOffsets.isChecked,
            rgbOffsets = RgbOffsets(
                r = sliderOffsetR.value.toInt(),
                g = sliderOffsetG.value.toInt(),
                b = sliderOffsetB.value.toInt()
            )
        )
    }

    private fun applyPreset(preset: Preset) {
        when (preset) {
            Preset.CLASSIC -> {
                sliderOffset.value = 3f
                cbInvert.isChecked = true
                sliderOpacity.value = 0.5f
                sliderBlur.value = 0f
                sliderGlowRadius.value = 0f
                sliderGlowIntensity.value = 0.5f
                sliderContrast.value = 1.5f
                sliderBrightness.value = 0f
                rgMode.check(R.id.rbGrayscale)
                cbRgbOffsets.isChecked = false
                sliderOffsetR.value = 0f
                sliderOffsetG.value = 0f
                sliderOffsetB.value = 0f
            }
            Preset.NEON -> {
                sliderOffset.value = 3f
                cbInvert.isChecked = true
                sliderOpacity.value = 0.6f
                sliderBlur.value = 0f
                sliderGlowRadius.value = 8f
                sliderGlowIntensity.value = 1.2f
                sliderContrast.value = 1.5f
                sliderBrightness.value = 0f
                rgMode.check(R.id.rbColor)
                cbRgbOffsets.isChecked = true
                sliderOffsetR.value = 2f
                sliderOffsetG.value = 4f
                sliderOffsetB.value = 6f
            }
            Preset.HIGH_CONTRAST -> {
                sliderOffset.value = 2f
                cbInvert.isChecked = true
                sliderOpacity.value = 0.5f
                sliderBlur.value = 0f
                sliderGlowRadius.value = 0f
                sliderGlowIntensity.value = 0.5f
                sliderContrast.value = 3f
                sliderBrightness.value = 0f
                rgMode.check(R.id.rbGrayscale)
                cbRgbOffsets.isChecked = false
                sliderOffsetR.value = 0f
                sliderOffsetG.value = 0f
                sliderOffsetB.value = 0f
            }
            Preset.SOFT -> {
                sliderOffset.value = 5f
                cbInvert.isChecked = true
                sliderOpacity.value = 0.4f
                sliderBlur.value = 3f
                sliderGlowRadius.value = 12f
                sliderGlowIntensity.value = 0.4f
                sliderContrast.value = 0.8f
                sliderBrightness.value = 0.05f
                rgMode.check(R.id.rbColor)
                cbRgbOffsets.isChecked = true
                sliderOffsetR.value = 3f
                sliderOffsetG.value = 5f
                sliderOffsetB.value = 7f
            }
            Preset.RESET -> {
                sliderOffset.value = 3f
                cbInvert.isChecked = true
                sliderOpacity.value = 0.5f
                sliderBlur.value = 0f
                sliderGlowRadius.value = 0f
                sliderGlowIntensity.value = 0.5f
                sliderContrast.value = 1.5f
                sliderBrightness.value = 0f
                rgMode.check(R.id.rbGrayscale)
                cbRgbOffsets.isChecked = false
                sliderOffsetR.value = 0f
                sliderOffsetG.value = 0f
                sliderOffsetB.value = 0f
            }
        }
        updateRgbOffsetSliderState()
    }

    private enum class Preset {
        CLASSIC, NEON, HIGH_CONTRAST, SOFT, RESET
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
