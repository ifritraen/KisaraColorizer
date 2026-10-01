package com.raen.kisaracolorizer.data.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class BatchProgress(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val currentFileName: String = "",
    val statusText: String = "",
    val percent: Float = 0f,
    val completedFiles: List<File> = emptyList(),
)

class BatchColorizer(
    private val context: Context,
    private val service: ColorizationService,
) {
    private val _progress = MutableStateFlow(BatchProgress())
    val progress: StateFlow<BatchProgress> = _progress.asStateFlow()

    /**
     * Colorizes all images inside the specified SAF folder [treeUri].
     */
    suspend fun colorizeFolder(treeUri: Uri) = withContext(Dispatchers.IO) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Cannot access folder: $treeUri")

        val imageFiles = rootDoc.listFiles()
            .filter { it.isFile && isImageMime(it.type, it.name) }
            .sortedWith { a, b -> naturalCompare(a.name ?: "", b.name ?: "") }

        if (imageFiles.isEmpty()) {
            _progress.value = BatchProgress(statusText = "No image files found in selected folder")
            return@withContext
        }

        val total = imageFiles.size
        val outputDir = File(context.getExternalFilesDir(null), "BatchColorized_${System.currentTimeMillis()}").apply { mkdirs() }
        val completed = mutableListOf<File>()

        com.raen.kisaracolorizer.data.engine.WakeLockHelper.acquire(context, timeoutMs = 60 * 60 * 1000L)
        try {
            _progress.value = BatchProgress(
                isRunning = true,
                current = 0,
                total = total,
                statusText = "Starting batch colorization of $total pages...",
                percent = 0f,
            )

            for ((index, docFile) in imageFiles.withIndex()) {
                val fileName = docFile.name ?: "page_${index + 1}.jpg"
                val stepPercent = index.toFloat() / total.toFloat()

                _progress.value = _progress.value.copy(
                    current = index + 1,
                    currentFileName = fileName,
                    statusText = "Colorizing page ${index + 1}/$total ($fileName) • ${(stepPercent * 100).toInt()}%",
                    percent = stepPercent,
                )

                try {
                    val inputBitmap = context.contentResolver.openInputStream(docFile.uri)?.use {
                        BitmapFactory.decodeStream(it)
                    } ?: continue

                    val result = service.colorizeBitmap(inputBitmap)
                    inputBitmap.recycle()

                    val outFile = File(outputDir, fileName.substringBeforeLast(".") + "_colorized.png")
                    FileOutputStream(outFile).use { out ->
                        result.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        out.flush()
                    }
                    result.bitmap.recycle()
                    completed.add(outFile)
                } catch (e: Exception) {
                    android.util.Log.e("BatchColorizer", "Failed to colorize $fileName", e)
                }
            }

            _progress.value = BatchProgress(
                isRunning = false,
                current = total,
                total = total,
                statusText = "Batch complete! Successfully colorized ${completed.size}/$total pages.",
                percent = 1f,
                completedFiles = completed,
            )
        } finally {
            com.raen.kisaracolorizer.data.engine.WakeLockHelper.release()
            service.scheduleIdleUnload(5_000L)
        }
    }

    private fun isImageMime(type: String?, name: String?): Boolean {
        if (type?.startsWith("image/") == true) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase() ?: ""
        return ext in listOf("jpg", "jpeg", "png", "webp")
    }

    private fun naturalCompare(s1: String, s2: String): Int {
        var i = 0
        var j = 0
        while (i < s1.length && j < s2.length) {
            val c1 = s1[i]
            val c2 = s2[j]
            if (c1.isDigit() && c2.isDigit()) {
                var num1 = 0
                while (i < s1.length && s1[i].isDigit()) {
                    num1 = num1 * 10 + (s1[i] - '0')
                    i++
                }
                var num2 = 0
                while (j < s2.length && s2[j].isDigit()) {
                    num2 = num2 * 10 + (s2[j] - '0')
                    j++
                }
                if (num1 != num2) return num1.compareTo(num2)
            } else {
                val cmp = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return s1.length.compareTo(s2.length)
    }
}
