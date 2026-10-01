package com.raen.kisaracolorizer.data.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class ColorizeDiagnostics(
    val durationMs: Long = 0,
    val inputDim: String = "",
    val outputDim: String = "",
    val bufSize: Int = 0,
    val plane: Int = 0,
    val ch0Mean: Float = 0f,
    val ch1Mean: Float = 0f,
    val ch2Mean: Float = 0f,
    val chDiff: Float = 0f,
    val isNnapi: Boolean = false,
    val pipelineMode: ColorizeImageUtils.PipelineMode = ColorizeImageUtils.PipelineMode.LAB_SPLICING,
    val intensity: Float = 4.0f,
)

data class ColorizeResult(
    val bitmap: Bitmap,
    val diagnostics: ColorizeDiagnostics,
)

/**
 * High-performance on-device ONNX Runtime Colorization Engine.
 * Supports Manga Colorizer v2, DeOldify, and custom models.
 */
class MangaColorizeEngine(
    private val env: OrtEnvironment = OrtEnvironment.getEnvironment(),
) : AutoCloseable {

    companion object {
        private const val TAG = "KisaraColorizer"
    }

    private var activeSession: OrtSession? = null
    private var activeModelPath: String? = null
    private var isNnapiActive: Boolean = false
    private var currentSpeedTier: Int = 8

    @Synchronized
    private fun getExpectedInputChannels(session: OrtSession, modelFile: File): Int {
        val inputInfo = session.inputInfo.values.firstOrNull()
        val tensorInfo = inputInfo?.info as? ai.onnxruntime.TensorInfo
        val shapeChannels = tensorInfo?.shape?.getOrNull(1)?.toInt()?.takeIf { it > 0 }
        if (shapeChannels != null) return shapeChannels
        return if (modelFile.name.contains("manga", ignoreCase = true)) 5 else 3
    }

    @Synchronized
    fun getSession(modelFile: File, useNnapi: Boolean, speedTier: Int = 3): OrtSession {
        if (activeSession != null && activeModelPath == modelFile.absolutePath && isNnapiActive == useNnapi && currentSpeedTier == speedTier) {
            return activeSession!!
        }

        activeSession?.close()
        val perf = ColorizeImageUtils.PerformanceConfig.fromLevel(speedTier)
        var session: OrtSession? = null
        var isNnapi = false

        if (useNnapi) {
            try {
                val nnapiOpts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(perf.cpuThreads)
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    addNnapi()
                }
                val candidate = env.createSession(modelFile.absolutePath, nnapiOpts)
                val channels = getExpectedInputChannels(candidate, modelFile)
                val targetDim = if (channels == 5) 576 else 256
                if (verifyNnapiSanity(candidate, targetDim, channels)) {
                    session = candidate
                    isNnapi = true
                    com.raen.kisaracolorizer.data.logger.AppLogger.success("NNAPI hardware acceleration active! Threads: ${perf.cpuThreads} (${perf.label})")
                    Log.i(TAG, "NNAPI session successfully initialized with ${perf.cpuThreads} threads")
                } else {
                    com.raen.kisaracolorizer.data.logger.AppLogger.warn("NNAPI probe failed or produced NaNs, falling back to multi-core CPU")
                    Log.w(TAG, "NNAPI sanity probe failed, falling back to CPU")
                    candidate.close()
                }
            } catch (e: Exception) {
                com.raen.kisaracolorizer.data.logger.AppLogger.warn("NNAPI init failed: ${e.message}, falling back to multi-core CPU")
                Log.w(TAG, "NNAPI init failed, falling back to CPU", e)
            }
        }

        if (session == null) {
            val cpuOpts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(perf.cpuThreads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                addCPU(true)
            }
            session = env.createSession(modelFile.absolutePath, cpuOpts)
            isNnapi = false
            com.raen.kisaracolorizer.data.logger.AppLogger.info("Multi-core CPU session active (${perf.cpuThreads} threads, ${perf.label})")
            Log.i(TAG, "CPU session successfully initialized with ${perf.cpuThreads} threads")
        }

        activeSession = session
        activeModelPath = modelFile.absolutePath
        isNnapiActive = isNnapi
        currentSpeedTier = speedTier
        return session
    }

    @Synchronized
    fun unloadSession() {
        try {
            activeSession?.close()
            activeSession = null
            activeModelPath = null
            isNnapiActive = false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close session cleanly", e)
        }
    }

    private fun verifyNnapiSanity(session: OrtSession, modelDim: Int, channels: Int): Boolean {
        val targetW = if (channels == 5) 576 else modelDim
        val targetH = if (channels == 5) 576 else modelDim
        return try {
            val probeBuffer = AiBufferUtils.allocateDirectFloatBuffer(channels * targetW * targetH)
            for (i in 0 until (channels * targetW * targetH)) {
                probeBuffer.put(i, 0.5f)
            }
            probeBuffer.rewind()
            val inputName = session.inputNames.firstOrNull { it.contains("input", ignoreCase = true) || it.contains("data", ignoreCase = true) }
                ?: session.inputNames.first()
            val tensor = OnnxTensor.createTensor(
                env,
                probeBuffer,
                longArrayOf(1L, channels.toLong(), targetH.toLong(), targetW.toLong()),
            )
            val result = session.run(Collections.singletonMap(inputName, tensor))
            val out = result.get(0) as OnnxTensor
            val buf = out.floatBuffer
            var nanOrInf = false
            for (i in 0 until min(buf.remaining(), 100)) {
                val v = buf.get(i)
                if (v.isNaN() || v.isInfinite()) {
                    nanOrInf = true
                    break
                }
            }
            tensor.close()
            result.close()
            !nanOrInf
        } catch (e: Exception) {
            com.raen.kisaracolorizer.data.logger.AppLogger.warn("NNAPI sanity check exception: ${e.message}")
            false
        }
    }

    /**
     * Runs colorization on the black & white [inputBitmap].
     */
    suspend fun colorize(
        inputBitmap: Bitmap,
        modelFile: File,
        pipelineMode: ColorizeImageUtils.PipelineMode = ColorizeImageUtils.PipelineMode.DIRECT_RGB_BLEND,
        intensity: Float = 1.2f,
        inkThreshold: Float = 0.15f,
        useNnapi: Boolean = false,
        tuningParams: ColorizeImageUtils.ColorizerTuningParams? = null,
        speedTier: Int = 3,
        onProgress: ((step: String, percent: Float) -> Unit)? = null,
    ): ColorizeResult = withContext(Dispatchers.Default) {
        if (!modelFile.exists()) {
            throw IllegalArgumentException("Model file does not exist: ${modelFile.absolutePath}")
        }

        val perf = ColorizeImageUtils.PerformanceConfig.fromLevel(speedTier)
        onProgress?.invoke("Initializing session (${modelFile.name}, ${perf.label})...", 0.10f)
        com.raen.kisaracolorizer.data.logger.AppLogger.step("Initializing session for ${modelFile.name} (Speed: ${perf.label}, useNnapi=$useNnapi)...")

        val startTime = System.currentTimeMillis()
        val session = getSession(modelFile, useNnapi, speedTier)
        val channels = getExpectedInputChannels(session, modelFile)
        val isMangaV2 = channels == 5 || modelFile.name.contains("manga", ignoreCase = true)

        // Compute target dimensions
        val (targetW, targetH) = if (isMangaV2) {
            val baseDim = 576
            val aspect = inputBitmap.height.toFloat() / inputBitmap.width.coerceAtLeast(1).toFloat()
            val h = ((baseDim * aspect / 32.0f).roundToInt() * 32).coerceIn(384, 896)
            baseDim to h
        } else {
            256 to 256
        }

        onProgress?.invoke("Preparing NCHW tensor (${channels}x${targetH}x${targetW})...", 0.25f)
        com.raen.kisaracolorizer.data.logger.AppLogger.step("Allocating Direct FloatBuffer for 1x${channels}x${targetH}x${targetW} (image: ${inputBitmap.width}x${inputBitmap.height})")

        // Prepare NCHW FloatBuffer
        val isDeOldify = modelFile.name.contains("deoldify", ignoreCase = true)
        val inputBuffer = if (channels == 5) {
            ColorizeImageUtils.bitmapToMangaColorizerV2NchwFloatBuffer(inputBitmap, targetW, targetH)
        } else {
            ColorizeImageUtils.bitmapToDeOldifyNchwFloatBuffer(inputBitmap, targetW, targetH)
        }

        val inputName = session.inputNames.firstOrNull { it.contains("input", ignoreCase = true) || it.contains("data", ignoreCase = true) }
            ?: session.inputNames.first()

        val inputTensor = OnnxTensor.createTensor(
            env,
            inputBuffer,
            longArrayOf(1L, channels.toLong(), targetH.toLong(), targetW.toLong()),
        )

        onProgress?.invoke("Running neural network forward pass...", 0.45f)
        com.raen.kisaracolorizer.data.logger.AppLogger.step("Running forward inference pass on ${if (isNnapiActive) "NNAPI Hardware Acceleration (NPU/GPU)" else "ARM CPU (Multi-core NEON)"}...")

        val results = session.run(Collections.singletonMap(inputName, inputTensor))
        val outputTensor = results.get(0) as OnnxTensor
        val outBuffer = outputTensor.floatBuffer

        // Rewind native buffer
        outBuffer.rewind()

        val plane = targetW * targetH
        val rawFloats = FloatArray(outBuffer.remaining())
        outBuffer.get(rawFloats)

        inputTensor.close()
        outputTensor.close()
        results.close()

        val durationMs = System.currentTimeMillis() - startTime
        onProgress?.invoke("Inference done (${durationMs}ms). Processing tensor output...", 0.80f)

        // Compute channel diagnostics
        val bufSize = rawFloats.size
        val ch0Mean = if (bufSize >= plane) rawFloats.take(plane).average().toFloat() else -1f
        val ch1Mean = if (bufSize >= 2 * plane) rawFloats.slice(plane until 2 * plane).average().toFloat() else -1f
        val ch2Mean = if (bufSize >= 3 * plane) rawFloats.slice(2 * plane until 3 * plane).average().toFloat() else -1f
        val chDiff = if (ch0Mean >= 0 && ch1Mean >= 0) ch0Mean - ch1Mean else 0f

        com.raen.kisaracolorizer.data.logger.AppLogger.info("Inference completed in ${durationMs}ms. Ch0: %.3f, Ch1: %.3f, Ch2: %.3f (diff: %.3f)".format(ch0Mean, ch1Mean, ch2Mean, chDiff))

        // Build raw model RGB bitmap
        val rawModelBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        var maxVal = 0.0f
        for (i in 0 until min(rawFloats.size, 1000)) {
            val v = abs(rawFloats[i])
            if (v > maxVal) maxVal = v
        }
        val normFactor = if (maxVal <= 2.0f) 1.0f else 255.0f
        val isBgr = !isDeOldify && modelFile.name.contains("bgr", ignoreCase = true)

        val colorPixels = IntArray(plane)
        for (i in 0 until plane) {
            val c0 = ((rawFloats[i] / normFactor).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val c1 = ((rawFloats[plane + i] / normFactor).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val c2 = ((rawFloats[2 * plane + i] / normFactor).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)

            // DeOldify: raw tensor c0 is Red, c1 is Green, c2 is Blue.
            // (Previously isBgr inverted c0 and c2, causing cold cyan skin instead of warm peach).
            val r = if (isBgr) c2 else c0
            val g = c1
            val b = if (isBgr) c0 else c2

            colorPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        rawModelBitmap.setPixels(colorPixels, 0, targetW, 0, 0, targetW, targetH)

        // For DeOldify, automatically route to Venera-style LAB Splicing pipeline
        val effectivePipeline = if (isDeOldify) {
            ColorizeImageUtils.PipelineMode.LAB_SPLICING
        } else {
            pipelineMode
        }
        val effectiveIntensity = if (isDeOldify && intensity > 2.5f) 1.0f else intensity

        onProgress?.invoke("Applying ${effectivePipeline.name} (boost: ${effectiveIntensity}x)...", 0.90f)
        com.raen.kisaracolorizer.data.logger.AppLogger.step("Applying ${effectivePipeline.name} with intensity=$effectiveIntensity, inkThreshold=$inkThreshold")

        // Apply selected pipeline option
        val finalBitmap = when (effectivePipeline) {
            ColorizeImageUtils.PipelineMode.LAB_SPLICING -> {
                ColorizeImageUtils.spliceLabColorization(
                    originalBitmap = inputBitmap,
                    rawModelRgbBitmap = rawModelBitmap,
                    intensity = effectiveIntensity,
                )
            }
            ColorizeImageUtils.PipelineMode.DIRECT_RGB_BLEND -> {
                ColorizeImageUtils.blendDirectRgbWithLineArt(
                    originalBitmap = inputBitmap,
                    rawModelRgbBitmap = rawModelBitmap,
                    inkThreshold = inkThreshold,
                    saturationBoost = effectiveIntensity,
                )
            }
            ColorizeImageUtils.PipelineMode.TUNED_PRESET -> {
                ColorizeImageUtils.applyTuningPipeline(
                    originalBitmap = inputBitmap,
                    rawRgbBitmap = rawModelBitmap,
                    params = tuningParams ?: ColorizeImageUtils.ColorizerTuningParams.OPTIMIZED,
                    intensity = effectiveIntensity,
                    numThreads = perf.chunkWorkers,
                )
            }
        }
        rawModelBitmap.recycle()
        System.gc()

        onProgress?.invoke("Colorization finished!", 1.0f)
        com.raen.kisaracolorizer.data.logger.AppLogger.success("Output ready: ${finalBitmap.width}x${finalBitmap.height} in ${durationMs}ms")

        val diagnostics = ColorizeDiagnostics(
            durationMs = durationMs,
            inputDim = "1x${channels}x${targetH}x${targetW}",
            outputDim = "1x3x${targetH}x${targetW}",
            bufSize = bufSize,
            plane = plane,
            ch0Mean = ch0Mean,
            ch1Mean = ch1Mean,
            ch2Mean = ch2Mean,
            chDiff = chDiff,
            isNnapi = isNnapiActive,
            pipelineMode = pipelineMode,
            intensity = intensity,
        )

        ColorizeResult(finalBitmap, diagnostics)
    }

    override fun close() {
        activeSession?.close()
        activeSession = null
        activeModelPath = null
    }
}
