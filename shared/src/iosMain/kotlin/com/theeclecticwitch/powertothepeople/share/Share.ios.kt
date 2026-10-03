package com.theeclecticwitch.powertothepeople.share

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

actual fun shareText(subject: String, text: String) {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
    var top: UIViewController = window?.rootViewController ?: return
    while (true) top = top.presentedViewController ?: break
    val sheet = UIActivityViewController(listOf(text), null)
    // The subject line is the first line of the text; UIKit takes a subject only through an item source.
    // On an iPad the sheet is a popover and must point somewhere.
    sheet.popoverPresentationController?.sourceView = top.view
    top.presentViewController(sheet, animated = true, completion = null)
}

actual val shareCopies: Boolean = false
