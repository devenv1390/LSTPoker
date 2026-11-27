package com.aniapp.pokerdemo.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.aniapp.pokerdemo.net.ServerAddress
import com.aniapp.pokerdemo.net.ServerMessage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UpdatePhase { Idle, Checking, UpToDate, Available, Downloading, PermissionRequired, Installing, Failed, Canceled }

enum class UpdateSource { STARTUP, MANUAL, OTA }

data class UpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val metadata: UpdateMetadata? = null,
    val downloadedBytes: Long = 0,
    val message: String? = null,
    val userInitiated: Boolean = false,
    val source: String = UpdateSource.STARTUP.name,
    val updateId: String? = null
)

class UpdateCoordinator(
    context: Context,
    parentScope: CoroutineScope,
    private val onServerSaved: (UpdateEndpoint?) -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(
        parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job])
    )
    private val store = LastServerStore(appContext)
    private val repository = UpdateRepository(File(appContext.cacheDir, "updates"))
    private val installer = UpdateInstaller(appContext, ::onInstallEvent)
    private val packageManager = appContext.packageManager
    private val signalPreferences = appContext.getSharedPreferences("poker_update_signals", Context.MODE_PRIVATE)
    private val signalLock = Any()
    private val handledUpdateIds = LinkedHashSet(
        signalPreferences.getStringSet(KEY_HANDLED_IDS, emptySet()).orEmpty().take(MAX_HANDLED_IDS)
    )
    private val pendingUpdateIds = mutableSetOf<String>()
    private val mutableState = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var operation: Job? = null
    private var generation = 0L
    private var started = false
    private var activeEndpoint: UpdateEndpoint? = null

    internal val installedVersionCode: Long
        get() = currentVersionCode()

    fun start() {
        if (started) return
        started = true
        scope.launch {
            val endpoint = withContext(Dispatchers.IO) { store.load() }
            activeEndpoint = endpoint
            endpoint?.let(onServerSaved)
            checkForUpdate(UpdateSource.STARTUP)
        }
    }

    fun checkForUpdate(silent: Boolean = true) {
        checkForUpdate(if (silent) UpdateSource.STARTUP else UpdateSource.MANUAL)
    }

    fun checkForUpdate(source: UpdateSource) {
        startCheck(source, updateId = null)
    }

    fun checkManually() {
        checkForUpdate(UpdateSource.MANUAL)
    }

    fun onOtaSignal(signal: ServerMessage.Error) {
        if (signal.code != "update_available") return
        val updateId = signal.updateId?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val shouldCheck = synchronized(signalLock) {
            if (updateId in handledUpdateIds || updateId in pendingUpdateIds) return
            val current = mutableState.value
            if (current.phase == UpdatePhase.Available && current.updateId == updateId) {
                rememberHandledIdLocked(updateId)
                return
            }
            if (current.phase in UNINTERRUPTIBLE_PHASES) {
                mutableState.value = current.copy(updateId = updateId)
                rememberHandledIdLocked(updateId)
                return
            }
            pendingUpdateIds += updateId
            true
        }
        if (!shouldCheck) return
        if (!startCheck(UpdateSource.OTA, updateId)) {
            synchronized(signalLock) {
                val current = mutableState.value
                if (current.phase in UNINTERRUPTIBLE_PHASES ||
                    (current.phase == UpdatePhase.Available && current.updateId == updateId)
                ) {
                    mutableState.value = current.copy(updateId = updateId)
                    rememberHandledIdLocked(updateId)
                } else {
                    pendingUpdateIds -= updateId
                }
            }
        }
    }

    fun rememberServerAddress(address: ServerAddress) {
        scope.launch {
            val endpoint = withContext(Dispatchers.IO) { store.save(address) } ?: return@launch
            activeEndpoint = endpoint
            onServerSaved(endpoint)
        }
    }

    fun confirmDownload() {
        val current = mutableState.value
        val metadata = current.metadata ?: return
        if (operation?.isActive == true || current.phase == UpdatePhase.Installing) return
        if (!canInstall()) {
            mutableState.value = current.copy(phase = UpdatePhase.PermissionRequired)
            return
        }
        startDownload(metadata)
    }

    fun retry() {
        if (mutableState.value.metadata != null) confirmDownload() else checkManually()
    }

    fun dismiss() {
        when (mutableState.value.phase) {
            UpdatePhase.Checking -> {
                generation++
                operation?.cancel()
                mutableState.value.updateId?.let(::clearPendingUpdateId)
                mutableState.value = UpdateUiState()
            }
            UpdatePhase.Downloading -> {
                generation++
                operation?.cancel()
                mutableState.value = mutableState.value.copy(phase = UpdatePhase.Canceled)
            }
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
        onServerSaved(null)
        installer.close()
        repository.close()
    }

    private fun startCheck(source: UpdateSource, updateId: String?): Boolean {
        val current = mutableState.value
        if (current.phase in UNINTERRUPTIBLE_PHASES) return false
        if (operation?.isActive == true) {
            if (current.phase == UpdatePhase.Checking && source != UpdateSource.STARTUP) {
                generation++
                operation?.cancel()
            } else {
                return false
            }
        }
        if (current.phase == UpdatePhase.Available && source != UpdateSource.OTA) return false

        val checkGeneration = ++generation
        mutableState.value = UpdateUiState(
            phase = UpdatePhase.Checking,
            metadata = current.metadata,
            source = source.name,
            updateId = updateId,
            userInitiated = source == UpdateSource.MANUAL
        )
        operation = scope.launch {
            try {
                val endpoint = withContext(Dispatchers.IO) { store.load() }
                if (checkGeneration != generation) return@launch
                if (endpoint == null) {
                    if (source == UpdateSource.STARTUP) {
                        mutableState.value = UpdateUiState(source = source.name)
                    } else {
                        mutableState.value = checkFailure(
                            source,
                            updateId,
                            "尚未保存局域网服务器地址，请先连接一次联机服务器"
                        )
                    }
                    return@launch
                }
                val metadata = repository.fetchMetadata(endpoint)
                if (checkGeneration != generation) return@launch
                activeEndpoint = endpoint
                Log.i(TAG, "update check ${endpoint.baseUrl}: ${metadata.versionName} (${metadata.versionCode})")
                mutableState.value = if (metadata.isNewerThan(currentVersionCode())) {
                    UpdateUiState(
                        phase = UpdatePhase.Available,
                        metadata = metadata,
                        source = source.name,
                        updateId = updateId,
                        userInitiated = source == UpdateSource.MANUAL
                    )
                } else {
                    UpdateUiState(
                        phase = UpdatePhase.UpToDate,
                        metadata = metadata,
                        source = source.name,
                        updateId = updateId
                    )
                }
                updateId?.let(::markUpdateHandled)
            } catch (timeout: TimeoutCancellationException) {
                if (checkGeneration == generation) {
                    Log.w(TAG, "update check timed out", timeout)
                    mutableState.value = if (source == UpdateSource.STARTUP) {
                        UpdateUiState(source = source.name)
                    } else {
                        checkFailure(source, updateId, "更新服务响应超时，请检查网络后重试")
                    }
                }
            } catch (canceled: CancellationException) {
                if (checkGeneration == generation) {
                    mutableState.value = mutableState.value.copy(
                        phase = UpdatePhase.Canceled,
                        message = null
                    )
                }
                throw canceled
            } catch (error: Exception) {
                if (checkGeneration == generation) {
                    Log.w(TAG, "update check failed", error)
                    mutableState.value = if (source == UpdateSource.STARTUP) {
                        UpdateUiState(source = source.name)
                    } else {
                        checkFailure(source, updateId, error.readableMessage())
                    }
                }
            } finally {
                updateId?.let(::clearPendingUpdateId)
            }
        }
        return true
    }

    private fun startDownload(metadata: UpdateMetadata) {
        val target = activeEndpoint
        if (target == null) {
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.Failed,
                message = "更新服务地址已失效，请重新连接局域网服务器"
            )
            return
        }
        val current = mutableState.value
        val downloadGeneration = ++generation
        mutableState.value = UpdateUiState(
            phase = UpdatePhase.Downloading,
            metadata = metadata,
            source = current.source,
            updateId = current.updateId,
            userInitiated = current.userInitiated
        )
        operation = scope.launch {
            try {
                val apk = repository.download(target, metadata) { downloaded, _ ->
                    if (downloadGeneration == generation) {
                        mutableState.value = mutableState.value.copy(downloadedBytes = downloaded)
                    }
                }
                if (downloadGeneration != generation) return@launch
                mutableState.value = mutableState.value.copy(
                    phase = UpdatePhase.Installing,
                    downloadedBytes = metadata.sizeBytes,
                    message = null
                )
                installer.install(apk)
            } catch (timeout: TimeoutCancellationException) {
                if (downloadGeneration == generation) {
                    mutableState.value = mutableState.value.copy(
                        phase = UpdatePhase.Failed,
                        message = "下载超时，请检查网络后重试"
                    )
                }
            } catch (canceled: CancellationException) {
                if (downloadGeneration == generation) {
                    mutableState.value = mutableState.value.copy(phase = UpdatePhase.Canceled, message = null)
                }
                throw canceled
            } catch (error: Exception) {
                if (downloadGeneration == generation) {
                    Log.w(TAG, "update download/install failed", error)
                    mutableState.value = mutableState.value.copy(
                        phase = UpdatePhase.Failed,
                        message = error.readableMessage()
                    )
                }
            }
        }
    }

    private fun onInstallEvent(event: InstallEvent) {
        val current = mutableState.value
        val metadata = current.metadata
        mutableState.value = when (event) {
            InstallEvent.Success -> UpdateUiState(
                phase = UpdatePhase.UpToDate,
                source = current.source,
                updateId = current.updateId,
                userInitiated = current.userInitiated
            )
            InstallEvent.Aborted -> current.copy(phase = UpdatePhase.Canceled, message = null)
            InstallEvent.PendingUserAction -> current.copy(
                message = "请在系统安装确认界面点击“安装”"
            )
            is InstallEvent.Failure -> current.copy(phase = UpdatePhase.Failed, message = event.message)
        }
    }

    private fun checkFailure(source: UpdateSource, updateId: String?, message: String) = UpdateUiState(
        phase = UpdatePhase.Failed,
        message = message,
        source = source.name,
        updateId = updateId,
        userInitiated = source == UpdateSource.MANUAL
    )

    private fun markUpdateHandled(updateId: String) = synchronized(signalLock) {
        rememberHandledIdLocked(updateId)
    }

    private fun rememberHandledIdLocked(updateId: String) {
        pendingUpdateIds -= updateId
        handledUpdateIds -= updateId
        handledUpdateIds += updateId
        while (handledUpdateIds.size > MAX_HANDLED_IDS) {
            handledUpdateIds.remove(handledUpdateIds.first())
        }
        signalPreferences.edit()
            .putStringSet(KEY_HANDLED_IDS, handledUpdateIds.toSet())
            .apply()
    }

    private fun clearPendingUpdateId(updateId: String) = synchronized(signalLock) {
        pendingUpdateIds -= updateId
    }

    private fun currentVersionCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageManager.getPackageInfo(appContext.packageName, 0).longVersionCode
        else @Suppress("DEPRECATION") packageManager.getPackageInfo(appContext.packageName, 0).versionCode.toLong()

    private fun Exception.readableMessage(): String = when (this) {
        is java.io.IOException -> "更新文件读写失败，请检查网络或存储空间后重试"
        else -> message?.takeIf { it.isNotBlank() && !it.startsWith("/") } ?: "更新失败，请检查网络后重试"
    }

    private object Settings {
        const val ACTION_MANAGE_UNKNOWN_APP_SOURCES = "android.settings.MANAGE_UNKNOWN_APP_SOURCES"
    }

    private companion object {
        const val TAG = "PokerUpdate"
        const val KEY_HANDLED_IDS = "handled_update_ids"
        const val MAX_HANDLED_IDS = 64
        val UNINTERRUPTIBLE_PHASES = setOf(
            UpdatePhase.Downloading,
            UpdatePhase.PermissionRequired,
            UpdatePhase.Installing
        )
    }
}
