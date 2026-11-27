package com.aniapp.pokerdemo.update

import android.content.Context
import android.content.Intent
import com.aniapp.pokerdemo.net.ServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

class UpdateRuntime private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var otaClient: OtaUpdateClient

    val coordinator = UpdateCoordinator(context.applicationContext, scope) { endpoint ->
        otaClient.reconfigure(endpoint)
    }

    init {
        otaClient = OtaUpdateClient(scope, coordinator.installedVersionCode, coordinator::onOtaSignal)
        coordinator.start()
    }

    val state: StateFlow<UpdateUiState>
        get() = coordinator.state

    fun rememberServerAddress(address: ServerAddress) = coordinator.rememberServerAddress(address)

    fun checkForUpdate(silent: Boolean = true) = coordinator.checkForUpdate(silent)

    fun checkManually() = coordinator.checkManually()

    fun confirmDownload() = coordinator.confirmDownload()

    fun dismiss() = coordinator.dismiss()

    fun retry() = coordinator.retry()

    fun canInstall(): Boolean = coordinator.canInstall()

    fun installSettingsIntent(): Intent = coordinator.installSettingsIntent()

    fun onInstallPermissionResult() = coordinator.onInstallPermissionResult()

    companion object {
        @Volatile
        private var instance: UpdateRuntime? = null

        fun get(context: Context): UpdateRuntime = instance ?: synchronized(this) {
            instance ?: UpdateRuntime(context.applicationContext).also { instance = it }
        }
    }
}
