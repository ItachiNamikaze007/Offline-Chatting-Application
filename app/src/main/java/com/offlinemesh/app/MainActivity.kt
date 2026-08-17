package com.offlinemesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.offlinemesh.app.ui.navigation.AppNavigation
import com.offlinemesh.app.ui.theme.OfflineMeshTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OfflineMeshApplication
        val container = app.container

        setContent {
            OfflineMeshTheme {
                AppNavigation(container = container)
            }
        }
    }
}
