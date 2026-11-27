package com.aniapp.pokerdemo.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Parcelable
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface InstallEvent {
    data object Success : InstallEvent
    data object Aborted : InstallEvent
    data object PendingUserAction : InstallEvent
    data class Failure(val message: String) : InstallEvent
}

class UpdateInstaller(
    private val context: Context,
    private val onEvent: (InstallEvent) -> Unit
) {
    private var receiver: BroadcastReceiver? = null

    init {
        val filter = IntentFilter(ACTION_RESULT)
        val resultReceiver = ResultReceiver()
        ContextCompat.registerReceiver(
            context,
            resultReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiver = resultReceiver
    }

    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("LSTPoker.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val intent = Intent(ACTION_RESULT).setPackage(context.packageName)
            val sender = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(sender.intentSender)
        }
    }

    fun close() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    private inner class ResultReceiver : BroadcastReceiver() {
        override fun onReceive(receiver: Context, intent: Intent) {
            if (intent.action != ACTION_RESULT) return
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    if (confirmation == null) {
                        onEvent(InstallEvent.Failure("系统未提供安装确认界面"))
                    } else {
                        onEvent(InstallEvent.PendingUserAction)
                        runCatching {
                            context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure {
                            onEvent(InstallEvent.Failure("无法打开系统安装确认：${it.message}"))
                        }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> onEvent(InstallEvent.Success)
                PackageInstaller.STATUS_FAILURE_ABORTED -> onEvent(InstallEvent.Aborted)
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> onEvent(
                    InstallEvent.Failure("签名或版本不兼容，需卸载旧版后安装；卸载会清除应用数据")
                )
                else -> {
                    val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    onEvent(InstallEvent.Failure(detail?.takeIf { it.isNotBlank() } ?: "系统安装失败（$status）"))
                }
            }
        }
    }

    private object IntentCompat {
        fun <T : Parcelable> getParcelableExtra(intent: Intent, name: String, clazz: Class<T>): T? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(name, clazz)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<T>(name)
            }
    }

    private companion object {
        const val ACTION_RESULT = "com.aniapp.pokerdemo.action.UPDATE_INSTALL_RESULT"
    }
}
