package com.theeclecticwitch.powertothepeople

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Power to the People",
        state = rememberWindowState(size = DpSize(1100.dp, 850.dp)),
    ) {
        App()
    }
}
