package net.yukh.xui.app

import java.io.File
import javax.swing.JFileChooser

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
