package com.theeclecticwitch.powertothepeople

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File

/** The title bar and taskbar icon; drawn by tools/icon/desktop_icons.py. */
private val appIcon = Thread.currentThread().contextClassLoader.getResourceAsStream("icon.png")!!.use { BitmapPainter(loadImageBitmap(it)) }

fun main() {
    // JNA's native part from inside the Mac App Store copy (see prepareNativesForMac): a sandboxed Mac refuses
    // the copy JNA would unpack. Tip the Creator reaches StoreKit through JNA there.
    System.getProperty("compose.application.resources.dir")?.let { dir ->
        if (File(dir, "libjnidispatch.dylib").isFile) {
            System.setProperty("jna.boot.library.path", dir)
            System.setProperty("jna.nounpack", "true")
        }
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Power to the People",
            icon = appIcon,
            state = rememberWindowState(size = DpSize(1100.dp, 850.dp)),
        ) {
            App()
        }
    }
}
