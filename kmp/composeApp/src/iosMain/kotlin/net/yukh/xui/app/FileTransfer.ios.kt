@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package net.yukh.xui.app

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.posix.memcpy

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

/** The frontmost view controller to present from (walks past any presented VC). */
private fun topViewController(): UIViewController? {
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

actual fun platformExportFile(filename: String, bytes: ByteArray) {
    val path = NSTemporaryDirectory() + filename
    bytes.toNSData().writeToFile(path, atomically = true)
    val url = NSURL.fileURLWithPath(path)
    val activity = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    val vc = topViewController() ?: return
    vc.presentViewController(activity, animated = true, completion = null)
}

// Held while a picker is open so the delegate isn't collected mid-flight.
private var pickerDelegateRef: PickerDelegate? = null

actual fun platformPickFile(onResult: (filename: String, bytes: ByteArray) -> Unit) {
    val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData))
    val delegate = PickerDelegate(onResult)
    pickerDelegateRef = delegate
    picker.delegate = delegate
    topViewController()?.presentViewController(picker, animated = true, completion = null)
}

private class PickerDelegate(
    val onResult: (String, ByteArray) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url != null) {
            val name = url.lastPathComponent ?: "x-ui.db"
            val scoped = url.startAccessingSecurityScopedResource()
            val data = NSData.dataWithContentsOfURL(url)
            if (scoped) url.stopAccessingSecurityScopedResource()
            if (data != null) onResult(name, data.toByteArray())
        }
        pickerDelegateRef = null
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        pickerDelegateRef = null
    }
}
