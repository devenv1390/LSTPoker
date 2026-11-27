package com.aniapp.pokerdemo.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.aniapp.pokerdemo.net.ServerAddress
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UpdatePhase { Idle, Checking, UpToDate, Available, Downloading, PermissionRequired, Installing, Failed, Canceled }

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val metadata: UpdateMetadata? = null,
    val downloadedBytes: Long = 0,
    val message: String? = null,
    val userInitiated: Boolean = false
)

class UpdateCoordinator(
    context: Context,
    parentScope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val store = LastServerStore(appContext)
    private val repository = UpdateRepository(File(appContext.cacheDir, "updates"))
    private val installer = UpdateInstaller(appContext, ::onInstallEvent)
    private val packageManager = appContext.packageManager
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var operation: Job? = null

    fun checkForUpdate(silent: Boolean = true) {
        if (operation?.isActive == true || mutableState.value.phase in BUSY_OR_AVAILABLE) return
        operation = scope.launch {
            mutableState.value = UpdateUiState(phase = UpdatePhase.Checking, userInitiated = !silent)
            try {
                val endpoint = withContext(Dispatchers.IO) { store.load() } ?: run {
                    mutableState.value = if (silent) UpdateUiState() else UpdateUiState(
                        phase = UpdatePhase.Failed,
                        message = "尚未保存局域网服务器地址，请先连接一次联机服务器",
                        userInitiated = true
                    )
                    return@launch
                }
                val metadata = repository.fetchMetadata(endpoint)
                activeEndpoint = endpoint
                Log.i(TAG, "update check ${endpoint.baseUrl}: ${metadata.versionName} (${metadata.versionCode})")
                mutableState.value = if (metadata.isNewerThan(currentVersionCode())) {
                    UpdateUiState(phase = UpdatePhase.Available, metadata = metadata, userInitiated = !silent)
                } else {
                    UpdateUiState(phase = UpdatePhase.UpToDate, metadata = metadata, userInitiated = !silent)
                }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                Log.w(TAG, "update check timed out", timeout)
                mutableState.value = if (silent) UpdateUiState() else {
                    UpdateUiState(phase = UpdatePhase.Failed, message = "更新服务响应超时，请检查网络后重试", userInitiated = true)
                }
            } catch (canceled: kotlinx.coroutines.CancellationException) {
                throw canceled
            } catch (error: Exception) {
                Log.w(TAG, "update check failed", error)
                mutableState.value = if (silent) UpdateUiState() else {
                    UpdateUiState(phase = UpdatePhase.Failed, message = error.readableMessage(), userInitiated = true)
                }
            }
        }
    }

    fun checkManually() {
        checkForUpdate(silent = false)
    }

    fun rememberServerAddress(address: ServerAddress) {
        scope.launch {
            withContext(Dispatchers.IO) { store.save(address) }
        }
    }

    fun confirmDownload() {
        val metadata = mutableState.value.metadata ?: return
        if (operation?.isActive == true) return
        if (!canInstall()) {
            mutableState.value = mutableState.value.copy(phase = UpdatePhase.PermissionRequired)
            return
        }
        startDownload(metadata)
    }

    fun retry() {
        if (mutableState.value.metadata != null) confirmDownload() else checkForUpdate(silent = false)
    }

    fun dismiss() {
        when (mutableState.value.phase) {
            UpdatePhase.Checking -> {
                operation?.cancel()
                mutableState.value = UpdateUiState()
            }
            UpdatePhase.Downloading -> operation?.cancel()
            UpdatePhase.Installing -> return
            else -> mutableState.value = UpdateUiState()
        }
    }

    fun canInstall(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    fun installSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${appContext.packageName}")
    )

    fun onInstallPermissionResult() {
        if (canInstall()) {
            mutableState.value.metadata?.let(::startDownload)
        } else {
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.Failed,
                message = "未允许此应用安装未知应用，可稍后重试"
            )
        }
    }

    fun close() {
        scope.cancel()
        installer.close()
        repository.close()
    }

    private fun startDownload(metadata: UpdateMetadata) {
        val target = activeEndpoint
        if (target == null) {
            mutableState.value = UpdateUiState(phase = UpdatePhase.Failed, message = "更新服务地址已失效，请重新连接局域网服务器")
            return
        }
        operation = scope.launch {
            mutableState.value = UpdateUiState(
                phase = UpdatePhase.Downloading,
                metadata = metadata,
                downloadedBytes = 0
            )
            try {
                val apk = repository.download(target, metadata) { downloaded, total ->
                    mutableState.value = mutableState.value.copy(downloadedBytes = downloaded)
                }
                mutableState.value = UpdateUiState(
                    phase = UpdatePhase.Installing,
                    metadata = metadata,
                    downloadedBytes = metadata.sizeBytes
                )
                installer.install(apk)
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                mutableState.value = UpdateUiState(
                    phase = UpdatePhase.Failed,
                    metadata = metadata,
                    message = "下载超时，请检查网络后重试"
                )
            } catch (canceled: kotlinx.coroutines.CancellationException) {
                mutableState.value = UpdateUiState(phase = UpdatePhase.Canceled, metadata = metadata)
                throw canceled
            } catch (error: Exception) {
                Log.w(TAG, "update download/install failed", error)
                mutableState.value = UpdateUiState(
                    phase = UpdatePhase.Failed,
                    metadata = metadata,
                    message = error.readableMessage()
                )
            }
        }
    }

    private fun onInstallEvent(event: InstallEvent) {
        val metadata = mutableState.value.metadata
        mutableState.value = when (event) {
            InstallEvent.Success -> UpdateUiState(phase = UpdatePhase.UpToDate)
            InstallEvent.Aborted -> UpdateUiState(phase = UpdatePhase.Canceled, metadata = metadata)
            InstallEvent.PendingUserAction -> UpdateUiState(
                phase = UpdatePhase.Installing,
                metadata = metadata,
                downloadedBytes = metadata?.sizeBytes ?: 0,
                message = "请在系统安装确认界面点击“安装”"
            )
            is InstallEvent.Failure -> UpdateUiState(
                phase = UpdatePhase.Failed,
                metadata = metadata,
                message = event.message
            )
        }
    }

    private fun currentVersionCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
        else @Suppress("DEPRECATION") packageManager.getPackageInfo(appContext.packageName, 0).versionCode.toLong()

    private fun Exception.readableMessage(): String = when (this) {
        is java.io.IOException -> "更新文件读写失败，请检查网络或存储空间后重试"
        else -> message?.takeIf { it.isNotBlank() && !it.startsWith("/") } ?: "更新失败，请检查网络后重试"
    }

    private var activeEndpoint: UpdateEndpoint? = null

    private object Settings {
        const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
    }

    private companion object {
        const val TAG = "PokerUpdate"
        val BUSY_OR_AVAILABLE = setOf(
            UpdatePhase.Checking,
            UpdatePhase.Available,
            UpdatePhase.Downloading,
            UpdatePhase.PermissionRequired,
            UpdatePhase.Installing
        )
    }
}
