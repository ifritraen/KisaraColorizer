package com.raen.kisaracolorizer.data.engine

import android.content.Context
import android.os.PowerManager
import com.raen.kisaracolorizer.data.logger.AppLogger

object WakeLockHelper {
    private const val TAG = "KisaraColorizer::ProcessingWakeLock"
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context, timeoutMs: Long = 10 * 60 * 1000L) {
        try {
            if (wakeLock == null) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(timeoutMs)
                AppLogger.info("WakeLock acquired (timeout ${timeoutMs / 1000}s) to prevent CPU sleep during task.")
            }
        } catch (e: Exception) {
            AppLogger.warn("Failed to acquire WakeLock: ${e.message}")
        }
    }

    @Synchronized
    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                AppLogger.info("WakeLock released.")
            }
        } catch (e: Exception) {
            AppLogger.warn("Failed to release WakeLock: ${e.message}")
        }
    }
}
