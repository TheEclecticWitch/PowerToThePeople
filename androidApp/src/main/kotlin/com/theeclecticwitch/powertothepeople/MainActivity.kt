package com.theeclecticwitch.powertothepeople

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.theeclecticwitch.powertothepeople.data.initialiseAndroidContext
import com.theeclecticwitch.powertothepeople.ui.TextSize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        initialiseAndroidContext(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // The clock and battery icons match the app's chosen theme, not just the phone's own setting.
            val prefs by TextSize.flow.collectAsState()
            val phoneDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = prefs.theme == "dark" || (prefs.theme == "system" && phoneDark)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            App()
        }
    }
}
