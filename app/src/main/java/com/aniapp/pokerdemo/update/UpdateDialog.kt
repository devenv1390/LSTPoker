package com.aniapp.pokerdemo.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit
) {
    val metadata = state.metadata
    val visible = when (state.phase) {
        UpdatePhase.Available,
        UpdatePhase.Downloading,
        UpdatePhase.PermissionRequired,
        UpdatePhase.Installing,
        UpdatePhase.Failed -> true
        UpdatePhase.Checking, UpdatePhase.UpToDate -> state.userInitiated
        else -> false
    }
    if (!visible) return

    val title = when (state.phase) {
        UpdatePhase.Available -> "发现新版本 v${metadata?.versionName}"
        UpdatePhase.Downloading -> "正在下载更新"
        UpdatePhase.PermissionRequired -> "需要安装权限"
        UpdatePhase.Installing -> "正在安装更新"
        UpdatePhase.Checking -> "正在检查更新"
        UpdatePhase.UpToDate -> "已是最新版本"
        UpdatePhase.Failed -> "更新失败"
        else -> "应用更新"
    }
    val message = when (state.phase) {
        UpdatePhase.Available -> buildString {
            append("版本 ${metadata?.versionName} · ${formatBytes(metadata?.sizeBytes ?: 0)}\n")
            append("下载并校验后，会进入 Android 系统安装确认。")
        }
        UpdatePhase.Downloading -> "已下载 ${formatBytes(state.downloadedBytes)} / ${formatBytes(metadata?.sizeBytes ?: 0)}"
        UpdatePhase.PermissionRequired -> "请允许 LSTPoker 安装未知应用。返回后会自动继续下载。"
        UpdatePhase.Installing -> state.message ?: "安装包已校验，正在等待系统确认。"
        UpdatePhase.Checking -> "正在连接局域网更新服务…"
        UpdatePhase.UpToDate -> "当前版本 v${metadata?.versionName ?: "未知"} 已是最新，无需更新。"
        UpdatePhase.Failed -> state.message ?: "请检查网络后重试"
        else -> ""
    }

    AlertDialog(
        onDismissRequest = {
            if (state.phase !in setOf(UpdatePhase.Downloading, UpdatePhase.Installing)) onDismiss()
        },
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(message)
                if (state.phase == UpdatePhase.Downloading) {
                    val total = metadata?.sizeBytes ?: 0
                    val progress = if (total > 0) state.downloadedBytes.toFloat() / total else 0f
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
                if (state.phase == UpdatePhase.Checking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            when (state.phase) {
                UpdatePhase.Available -> TextButton(onClick = onConfirm) { Text("下载并安装") }
                UpdatePhase.Downloading -> TextButton(onClick = onDismiss) { Text("取消") }
                UpdatePhase.PermissionRequired -> TextButton(onClick = onOpenSettings) { Text("去设置") }
                UpdatePhase.Checking, UpdatePhase.UpToDate -> TextButton(onClick = onDismiss) { Text("知道了") }
                UpdatePhase.Failed -> TextButton(onClick = onRetry) { Text("重试") }
                else -> Unit
            }
        },
        dismissButton = {
            if (state.phase in setOf(UpdatePhase.Available, UpdatePhase.PermissionRequired, UpdatePhase.Failed)) {
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        }
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
