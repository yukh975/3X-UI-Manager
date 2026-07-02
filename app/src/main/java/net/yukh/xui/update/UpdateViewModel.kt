package net.yukh.xui.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import net.yukh.xui.data.prefs.AppSettingsStore
import net.yukh.xui.i18n.LANG_RU
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data object UpToDate : UpdateState
    data object Error : UpdateState
    data class Downloading(val percent: Int) : UpdateState
}

/**
 * Drives the self-update flow: check GitLab releases, download the APK, and hand
 * it to the system installer (same signing key + higher versionCode → in-place
 * update). Shared between the silent startup check and the manual Settings button.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val app: Application,
    private val settings: AppSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Newest release any check has seen — survives dialog dismissal so the
     *  Dashboard app-version card can keep hinting until the user updates. */
    private val _latestAvailable = MutableStateFlow<AppRelease?>(null)
    val latestAvailable: StateFlow<AppRelease?> = _latestAvailable.asStateFlow()

    private val http = OkHttpClient()

    private fun currentVersion(): String =
        runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }
            .getOrNull() ?: "0"

    /** Swap the English release body for the changelog section in the UI language. */
    private suspend fun localized(release: AppRelease): AppRelease {
        val russian = settings.getLanguage() == LANG_RU
        val notes = UpdateChecker.localizedNotes(release.version, russian)
        return if (notes != null) release.copy(notes = notes) else release
    }

    /** Silent check for startup — only surfaces a dialog when a newer build exists. */
    fun checkOnStart() {
        if (_state.value != UpdateState.Idle) return
        viewModelScope.launch {
            val rel = runCatching { UpdateChecker.latestIfNewer(currentVersion()) }.getOrNull()
            if (rel != null) {
                val localized = localized(rel)
                _latestAvailable.value = localized
                if (_state.value == UpdateState.Idle) {
                    _state.value = UpdateState.Available(localized)
                }
            }
        }
    }

    /** Manual check from Settings — reports up-to-date and errors too. */
    fun checkNow() {
        _state.value = UpdateState.Checking
        viewModelScope.launch {
            val latest = runCatching { UpdateChecker.fetchLatest() }.getOrNull()
            _state.value = when {
                latest == null -> UpdateState.Error
                UpdateChecker.isNewer(latest.version, currentVersion()) -> {
                    val localized = localized(latest)
                    _latestAvailable.value = localized
                    UpdateState.Available(localized)
                }
                else -> UpdateState.UpToDate
            }
        }
    }

    /** Re-open the update dialog from the Dashboard app-version card. */
    fun reshow() {
        _latestAvailable.value?.let { _state.value = UpdateState.Available(it) }
    }

    fun dismiss() { _state.value = UpdateState.Idle }

    /** Download the release APK and launch the installer (falls back to the web
     *  page if the release has no APK asset). */
    fun downloadAndInstall(release: AppRelease) {
        val url = release.apkUrl ?: run { openPage(release); return }
        _state.value = UpdateState.Downloading(0)
        viewModelScope.launch {
            val file = runCatching {
                download(url) { pct -> _state.value = UpdateState.Downloading(pct) }
            }.getOrNull()
            if (file == null) {
                _state.value = UpdateState.Error
            } else {
                install(file)
                _state.value = UpdateState.Idle
            }
        }
    }

    fun openPage(release: AppRelease) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(i) }
        _state.value = UpdateState.Idle
    }

    private suspend fun download(url: String, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }   // drop stale APKs
            val out = File(dir, "update.apk")
            val req = Request.Builder().url(url).build()
            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "HTTP ${resp.code}" }
                val body = resp.body ?: error("empty body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } >= 0) {
                            output.write(buf, 0, read)
                            done += read
                            if (total > 0) onProgress(((done * 100) / total).toInt())
                        }
                    }
                }
            }
            out
        }

    private fun install(file: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // On Android 8+ the installer itself prompts to enable "install unknown
        // apps" for us if it isn't granted yet, so no pre-check is needed.
        runCatching { app.startActivity(intent) }
    }
}
