package com.raen.kisaracolorizer.data.model

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Model Manager ported from Venera-SSR's ColorizationModelManager:
 * - Handles model onboarding & availability validation (>minSizeBytes)
 * - Multi-mirror downloading with Range resume & live progress
 * - Safe 64KB streaming from ContentResolver for external .onnx models
 * - Checks local app models and device fallback storage paths
 */
class ColorizationModelManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelManager"
        private const val PREFS_NAME = "kisara_colorizer_model_prefs"
        private const val KEY_VARIANT = "active_variant"
        private const val KEY_CUSTOM_NAME = "custom_model_name"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _activeVariant = MutableStateFlow(
        ModelVariant.fromId(prefs.getString(KEY_VARIANT, ModelVariant.MANGA_COLORIZER_V2.id)!!)
    )
    val activeVariant: StateFlow<ModelVariant> = _activeVariant.asStateFlow()

    private val _customModelName = MutableStateFlow(prefs.getString(KEY_CUSTOM_NAME, null))
    val customModelName: StateFlow<String?> = _customModelName.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadStatus = MutableStateFlow("")
    val downloadStatus: StateFlow<String> = _downloadStatus.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    fun setVariant(variant: ModelVariant) {
        _activeVariant.value = variant
        prefs.edit().putString(KEY_VARIANT, variant.id).apply()
    }

    /**
     * Resolves the active model file.
     * Returns the file if it exists and is larger than [variant.minSizeBytes], otherwise null.
     */
    fun getActiveModelFile(): File? {
        val variant = _activeVariant.value
        val localFile = File(modelsDir, variant.fileName)
        if (localFile.exists() && localFile.canRead() && localFile.length() > variant.minSizeBytes) {
            return localFile
        }

        // Check convenient device shared storage fallback (e.g. pushed via adb)
        if (variant == ModelVariant.MANGA_COLORIZER_V2) {
            val deviceFallback = File("/storage/emulated/0/manga_colorization_v2_int8.onnx")
            if (deviceFallback.exists() && deviceFallback.canRead() && deviceFallback.length() > variant.minSizeBytes) {
                // Copy to app internal directory so ONNX Runtime native code has guaranteed read access
                try {
                    if (!localFile.exists() || localFile.length() <= 0) {
                        deviceFallback.inputStream().use { input ->
                            FileOutputStream(localFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (localFile.exists() && localFile.length() > variant.minSizeBytes) {
                            Log.i(TAG, "Copied device fallback model to ${localFile.absolutePath}")
                            return localFile
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not copy device fallback model: ${e.message}")
                }
                return deviceFallback
            }
        }

        return null
    }

    fun isModelReady(): Boolean {
        return getActiveModelFile() != null
    }

    /**
     * Downloads the current model variant using multi-mirror fallback and Range resume.
     */
    suspend fun downloadActiveModel(
        onProgress: ((Float) -> Unit)? = null,
        onStatus: ((String) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        if (_isDownloading.value) return@withContext

        val variant = _activeVariant.value
        val targetFile = File(modelsDir, variant.fileName)
        val tempFile = File(modelsDir, "${variant.fileName}.tmp")

        _isDownloading.value = true
        _downloadProgress.value = 0f
        _downloadStatus.value = "Preparing download..."
        com.raen.kisaracolorizer.data.engine.WakeLockHelper.acquire(context)

        try {
            var downloadedSuccessfully = false
            var lastError: Exception? = null

            for ((index, url) in variant.downloadUrls.withIndex()) {
                val statusText = "Trying mirror ${index + 1}/${variant.downloadUrls.size}..."
                _downloadStatus.value = statusText
                onStatus?.invoke(statusText)

                try {
                    com.raen.kisaracolorizer.data.logger.AppLogger.info("Attempting model download from mirror: $url")
                    downloadFileWithResume(url, tempFile) { received, total ->
                        val progress = if (total > 0) (received.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                        _downloadProgress.value = progress
                        _downloadStatus.value = "Downloading ${(progress * 100).toInt()}% (${received / (1024 * 1024)}MB / ${total / (1024 * 1024)}MB)"
                        onProgress?.invoke(progress)
                    }

                    if (tempFile.length() > variant.minSizeBytes) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        downloadedSuccessfully = true
                        _downloadProgress.value = 1.0f
                        _downloadStatus.value = "Model downloaded successfully"
                        com.raen.kisaracolorizer.data.logger.AppLogger.success("Model ${variant.displayName} successfully saved (${targetFile.length() / (1024 * 1024)} MB).")
                        break
                    } else {
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    com.raen.kisaracolorizer.data.logger.AppLogger.warn("Mirror $url failed: ${e.message}")
                    Log.w(TAG, "Mirror $url failed: ${e.message}")
                    lastError = e
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            if (!downloadedSuccessfully) {
                com.raen.kisaracolorizer.data.logger.AppLogger.error("All download mirrors failed for ${variant.displayName}", lastError)
                throw lastError ?: Exception("All download mirrors failed.")
            }
        } finally {
            com.raen.kisaracolorizer.data.engine.WakeLockHelper.release()
            _isDownloading.value = false
        }
    }

    private fun downloadFileWithResume(
        url: String,
        targetFile: File,
        progressCallback: (Long, Long) -> Unit,
    ) {
        var startByte = 0L
        if (targetFile.exists()) {
            startByte = targetFile.length()
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "KisaraColorizer/1.0")

        if (startByte > 0) {
            requestBuilder.header("Range", "bytes=$startByte-")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0) contentLength + startByte else -1L

            val append = startByte > 0 && response.code == 206
            if (!append && targetFile.exists()) {
                targetFile.delete()
                startByte = 0L
            }

            body.byteStream().use { input ->
                FileOutputStream(targetFile, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var currentReceived = startByte

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        currentReceived += bytesRead
                        progressCallback(currentReceived, totalBytes)
                    }
                    output.flush()
                }
            }
        }
    }

    /**
     * Imports an external .onnx model safely via ContentResolver without OOM.
     */
    suspend fun importModelFromUri(uri: Uri, displayName: String): Boolean = withContext(Dispatchers.IO) {
        val targetFile = File(modelsDir, ModelVariant.CUSTOM.fileName)
        val tempFile = File(modelsDir, "${ModelVariant.CUSTOM.fileName}.tmp")

        try {
            val resolver = context.contentResolver
            val inputStream = resolver.openInputStream(uri)
                ?: throw Exception("Cannot open stream for URI $uri")

            var totalBytes = 0L
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalBytes += read
                    }
                    output.flush()
                }
            }

            if (totalBytes > ModelVariant.CUSTOM.minSizeBytes) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                _customModelName.value = displayName
                prefs.edit().putString(KEY_CUSTOM_NAME, displayName).apply()
                setVariant(ModelVariant.CUSTOM)
                true
            } else {
                tempFile.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model from URI", e)
            if (tempFile.exists()) tempFile.delete()
            false
        }
    }

    fun deleteActiveModel() {
        val variant = _activeVariant.value
        val file = File(modelsDir, variant.fileName)
        if (file.exists()) file.delete()
        if (variant == ModelVariant.CUSTOM) {
            _customModelName.value = null
            prefs.edit().remove(KEY_CUSTOM_NAME).apply()
            setVariant(ModelVariant.MANGA_COLORIZER_V2)
        }
    }
}
