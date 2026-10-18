package com.theeclecticwitch.powertothepeople

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** The title bar and taskbar icon; drawn by tools/icon/desktop_icons.py. */
private val appIcon = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")!!.use { BitmapPainter(loadImageBitmap(it)) }

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Power to the People",
        icon = appIcon,
        state = rememberWindowState(size = DpSize(1100.dp, 850.dp)),
    ) {
        App()
    }
}
