package com.raen.kisaracolorizer

import android.app.Application
import com.raen.kisaracolorizer.data.model.ColorizationModelManager
import com.raen.kisaracolorizer.data.service.BatchColorizer
import com.raen.kisaracolorizer.data.service.ColorizationService

class KisaraColorizerApp : Application() {

    lateinit var modelManager: ColorizationModelManager
        private set

    lateinit var colorizationService: ColorizationService
        private set

    lateinit var batchColorizer: BatchColorizer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.raen.kisaracolorizer.data.logger.AppLogger.init(this)
        com.raen.kisaracolorizer.data.monitor.ResourceMonitor.start()
        modelManager = ColorizationModelManager(this)
        colorizationService = ColorizationService(this, modelManager)
        batchColorizer = BatchColorizer(this, colorizationService)
    }

    companion object {
        lateinit var instance: KisaraColorizerApp
            private set
    }
}
