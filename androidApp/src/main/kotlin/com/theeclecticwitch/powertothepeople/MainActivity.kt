package com.theeclecticwitch.powertothepeople

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.theeclecticwitch.powertothepeople.data.initialiseAndroidContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        initialiseAndroidContext(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
