package com.raen.kisaracolorizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.raen.kisaracolorizer.ui.MainScreen
import com.raen.kisaracolorizer.ui.theme.KisaraColorizerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as KisaraColorizerApp
        val colorizationService = app.colorizationService
        val batchColorizer = app.batchColorizer

        setContent {
            KisaraColorizerTheme {
                MainScreen(
                    service = colorizationService,
                    batchColorizer = batchColorizer,
                )
            }
        }
    }
}
