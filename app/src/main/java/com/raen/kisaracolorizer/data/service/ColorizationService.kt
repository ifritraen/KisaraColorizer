package com.raen.kisaracolorizer.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.raen.kisaracolorizer.data.engine.ColorizeDiagnostics
import com.raen.kisaracolorizer.data.engine.ColorizeImageUtils
import com.raen.kisaracolorizer.data.engine.ColorizeResult
import com.raen.kisaracolorizer.data.engine.MangaColorizeEngine
import com.raen.kisaracolorizer.data.model.ColorizationModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ColorizationService(
    private val context: Context,
    val modelManager: ColorizationModelManager,
) {
    private val engine = MangaColorizeEngine()

    private val _pipelineMode = MutableStateFlow(ColorizeImageUtils.PipelineMode.TUNED_PRESET)
    val pipelineMode: StateFlow<ColorizeImageUtils.PipelineMode> = _pipelineMode.asStateFlow()

    private val _intensity = MutableStateFlow(1.0f) // Standard intensity for Studio Tuning pipeline
    val intensity: StateFlow<Float> = _intensity.asStateFlow()

    private val _inkThreshold = MutableStateFlow(0.15f)
    val inkThreshold: StateFlow<Float> = _inkThreshold.asStateFlow()

    private val _useNnapi = MutableStateFlow(false) // Default to ultra-fast ARM NEON multi-core CPU
    val useNnapi: StateFlow<Boolean> = _useNnapi.asStateFlow()

    private val _speedTier = MutableStateFlow(3) // Level 1..4 (default 3: High / 4 Big Cores)
    val speedTier: StateFlow<Int> = _speedTier.asStateFlow()

    private val _tuningParams = MutableStateFlow(ColorizeImageUtils.ColorizerTuningParams.OPTIMIZED)
    val tuningParams: StateFlow<ColorizeImageUtils.ColorizerTuningParams> = _tuningParams.asStateFlow()

    private val _activePresetName = MutableStateFlow("Optimized")
    val activePresetName: StateFlow<String> = _activePresetName.asStateFlow()

    private val _lastDiagnostics = MutableStateFlow<ColorizeDiagnostics?>(null)
    val lastDiagnostics: StateFlow<ColorizeDiagnostics?> = _lastDiagnostics.asStateFlow()

    // Persistent single-page state preserved across tab changes!
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
    private var singleJob: kotlinx.coroutines.Job? = null
    private var idleUnloadJob: kotlinx.coroutines.Job? = null

    fun scheduleIdleUnload(delayMs: Long = 5_000L) {
        idleUnloadJob?.cancel()
        idleUnloadJob = serviceScope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (!_isProcessing.value) {
                freeMemory()
                com.raen.kisaracolorizer.data.logger.AppLogger.info("Auto-freed RAM: ONNX session unloaded after ${delayMs / 1000}s idle.")
            }
        }
    }

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap: StateFlow<Bitmap?> = _originalBitmap.asStateFlow()

    private val _colorizedBitmap = MutableStateFlow<Bitmap?>(null)
    val colorizedBitmap: StateFlow<Bitmap?> = _colorizedBitmap.asStateFlow()

    private val _pcReferenceBitmap = MutableStateFlow<Bitmap?>(null)
    val pcReferenceBitmap: StateFlow<Bitmap?> = _pcReferenceBitmap.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _progressStep = MutableStateFlow("")
    val progressStep: StateFlow<String> = _progressStep.asStateFlow()

    private val _progressPercent = MutableStateFlow(0f)
    val progressPercent: StateFlow<Float> = _progressPercent.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        serviceScope.launch {
            modelManager.activeVariant.collect { variant ->
                when (variant) {
                    com.raen.kisaracolorizer.data.model.ModelVariant.DEOLDIFY -> {
                        _pipelineMode.value = ColorizeImageUtils.PipelineMode.LAB_SPLICING
                        _intensity.value = 1.0f
                        com.raen.kisaracolorizer.data.logger.AppLogger.info("DeOldify selected: Auto-configured Venera-style LAB Splicing pipeline (intensity 1.0x).")
                    }
                    com.raen.kisaracolorizer.data.model.ModelVariant.MANGA_COLORIZER_V2 -> {
                        _pipelineMode.value = ColorizeImageUtils.PipelineMode.TUNED_PRESET
                        _intensity.value = 1.0f
                        com.raen.kisaracolorizer.data.logger.AppLogger.info("Manga Colorizer v2 FP32 selected: Auto-configured Studio Tuning Presets & 15 Filter Metrics.")
                    }
                    else -> {}
                }
            }
        }
    }

    fun setSelectedImage(uri: Uri, bitmap: Bitmap) {
        _selectedImageUri.value = uri
        _originalBitmap.value = bitmap
        _colorizedBitmap.value = null
        _errorMessage.value = null
        _progressStep.value = ""
        _progressPercent.value = 0f
        com.raen.kisaracolorizer.data.logger.AppLogger.info("Selected image: ${bitmap.width}x${bitmap.height} ($uri)")
    }

    fun setPcReferenceBitmap(bitmap: Bitmap?) {
        _pcReferenceBitmap.value = bitmap
        if (bitmap != null) {
            com.raen.kisaracolorizer.data.logger.AppLogger.info("Loaded PC Reference image: ${bitmap.width}x${bitmap.height}")
            val orig = _originalBitmap.value
            val col = _colorizedBitmap.value
            if (orig != null && col != null) {
                exportDebugImages(orig, col, bitmap)
            }
        }
    }

    fun exportDebugImages(original: Bitmap, colorized: Bitmap, pcRef: Bitmap? = null) {
        try {
            val debugDir = File(context.filesDir, "debug").apply { mkdirs() }
            val origFile = File(debugDir, "latest_input.png")
            val colorFile = File(debugDir, "latest_colorized.png")
            FileOutputStream(origFile).use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileOutputStream(colorFile).use { colorized.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (pcRef != null) {
                val pcFile = File(debugDir, "latest_pc_reference.png")
                FileOutputStream(pcFile).use { pcRef.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            com.raen.kisaracolorizer.data.logger.AppLogger.success("Exported comparison output to: ${colorFile.absolutePath}")
        } catch (e: Exception) {
            com.raen.kisaracolorizer.data.logger.AppLogger.warn("Failed to export comparison images: ${e.message}")
        }
    }

    fun startColorization() {
        val bitmap = _originalBitmap.value ?: return
        if (_isProcessing.value) return

        idleUnloadJob?.cancel()
        singleJob?.cancel()
        singleJob = serviceScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            _progressPercent.value = 0.05f
            _progressStep.value = "Starting colorization..."
            com.raen.kisaracolorizer.data.engine.WakeLockHelper.acquire(context)
            com.raen.kisaracolorizer.data.logger.AppLogger.info("Starting colorization on service scope (survives tab changes)...")

            try {
                val result = colorizeBitmap(bitmap) { step, pct ->
                    _progressStep.value = step
                    _progressPercent.value = pct
                }
                _colorizedBitmap.value = result.bitmap
                val secStr = String.format(java.util.Locale.US, "%.2fs", result.diagnostics.durationMs / 1000f)
                _progressStep.value = "Done in $secStr (${result.diagnostics.durationMs}ms)"
                _progressPercent.value = 1f
                com.raen.kisaracolorizer.data.logger.AppLogger.success("Colorization completed in $secStr (${result.diagnostics.durationMs}ms)")
                exportDebugImages(bitmap, result.bitmap, _pcReferenceBitmap.value)
            } catch (e: Exception) {
                com.raen.kisaracolorizer.data.logger.AppLogger.error("Colorization failed", e)
                _errorMessage.value = "Colorization failed: ${e.message}"
            } finally {
                com.raen.kisaracolorizer.data.engine.WakeLockHelper.release()
                _isProcessing.value = false
                scheduleIdleUnload(30_000L)
            }
        }
    }

    fun cancelColorization() {
        singleJob?.cancel()
        singleJob = null
        _isProcessing.value = false
        _progressStep.value = "Cancelled"
        com.raen.kisaracolorizer.data.logger.AppLogger.warn("Colorization cancelled by user.")
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setPipelineMode(mode: ColorizeImageUtils.PipelineMode) {
        _pipelineMode.value = mode
    }

    fun setIntensity(value: Float) {
        _intensity.value = value.coerceIn(0.5f, 30.0f)
    }

    fun setInkThreshold(value: Float) {
        _inkThreshold.value = value.coerceIn(0.02f, 0.50f)
    }

    fun setUseNnapi(enabled: Boolean) {
        _useNnapi.value = enabled
    }

    fun setSpeedTier(tier: Int) {
        _speedTier.value = tier.coerceIn(1, 4)
        val perf = ColorizeImageUtils.PerformanceConfig.fromLevel(_speedTier.value)
        com.raen.kisaracolorizer.data.logger.AppLogger.info("Speed Profile set to ${perf.label} (${perf.cpuThreads} CPU threads, ${perf.chunkWorkers} workers)")
    }

    fun setPreset(name: String) {
        _activePresetName.value = name
        _tuningParams.value = ColorizeImageUtils.ColorizerTuningParams.getPreset(name)
        com.raen.kisaracolorizer.data.logger.AppLogger.info("Applied tuning preset: $name")
    }

    fun updateTuningParams(params: ColorizeImageUtils.ColorizerTuningParams) {
        _activePresetName.value = "Custom"
        _tuningParams.value = params
    }

    fun freeMemory() {
        engine.unloadSession()
        System.gc()
        Runtime.getRuntime().gc()
        com.raen.kisaracolorizer.data.logger.AppLogger.info("RAM freed: Unloaded session and executed GC.")
    }

    suspend fun colorizeBitmap(
        inputBitmap: Bitmap,
        onProgress: ((step: String, percent: Float) -> Unit)? = null,
    ): ColorizeResult = withContext(Dispatchers.Default) {
        val modelFile = modelManager.getActiveModelFile()
            ?: throw IllegalStateException("Model is not ready. Please download or import a model first.")

        try {
            val result = engine.colorize(
                inputBitmap = inputBitmap,
                modelFile = modelFile,
                pipelineMode = _pipelineMode.value,
                intensity = _intensity.value,
                inkThreshold = _inkThreshold.value,
                useNnapi = _useNnapi.value,
                tuningParams = _tuningParams.value,
                speedTier = _speedTier.value,
                onProgress = onProgress,
            )
            _lastDiagnostics.value = result.diagnostics
            result
        } finally {
            System.gc()
            Runtime.getRuntime().gc()
        }
    }

    suspend fun colorizeUri(uri: Uri): Pair<Bitmap, ColorizeResult> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for URI: $uri")
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("Cannot decode image from URI: $uri")
        inputStream.close()

        val result = colorizeBitmap(originalBitmap)
        Pair(originalBitmap, result)
    }

    suspend fun saveBitmapToCache(bitmap: Bitmap, name: String = "colorized_${System.currentTimeMillis()}.png"): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "colorized_pages").apply { mkdirs() }
        val outFile = File(cacheDir, name)
        FileOutputStream(outFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        outFile
    }

    fun clearCache() {
        val cacheDir = File(context.cacheDir, "colorized_pages")
        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
        }
    }
}
