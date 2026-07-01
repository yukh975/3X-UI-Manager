package net.yukh.xui.app

import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

actual fun platformOpenUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
    }
}

/** Desktop file save / pick via Swing's JFileChooser (modal). */
actual fun platformExportFile(filename: String, bytes: ByteArray) {
    val chooser = JFileChooser().apply {
        dialogTitle = "Save backup"
        selectedFile = File(filename)
    }
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.writeBytes(bytes)
    }
}

actual fun platformPickFile(onResult: (filename: String, bytes: ByteArray) -> Unit) {
    val chooser = JFileChooser().apply { dialogTitle = "Choose backup file" }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        val f = chooser.selectedFile
        onResult(f.name, f.readBytes())
    }
}
