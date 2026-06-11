package net.yukh.xui.app

/**
 * Platform file save / pick for backup-restore. On iOS these present the system
 * share sheet (export) and document picker (import). The pick callback fires
 * asynchronously after the user chooses a file (or never, if they cancel).
 */
expect fun platformExportFile(filename: String, bytes: ByteArray)

expect fun platformPickFile(onResult: (filename: String, bytes: ByteArray) -> Unit)
