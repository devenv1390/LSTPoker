package com.aniapp.pokerdemo.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = when (state.phase) {
        UpdatePhase.Checking,
        UpdatePhase.Available,
        UpdatePhase.Downloading,
        UpdatePhase.PermissionRequired,
        UpdatePhase.Installing,
        UpdatePhase.Failed -> true
        UpdatePhase.UpToDate -> state.userInitiated
        UpdatePhase.Idle, UpdatePhase.Canceled -> false
    }
    if (!visible) return

    val metadata = state.metadata
    val title = when (state.phase) {
        UpdatePhase.Available -> "发现新版本 v${metadata?.versionName ?: "未知"}"
        UpdatePhase.Downloading -> "正在下载更新"
        UpdatePhase.PermissionRequired -> "需要安装权限"
        UpdatePhase.Installing -> "正在安装更新"
        UpdatePhase.Checking -> "正在检查更新"
        UpdatePhase.UpToDate -> "已是最新版本"
        UpdatePhase.Failed -> "更新失败"
        else -> "应用更新"
    }
    val message = when (state.phase) {
        UpdatePhase.Available -> "版本 ${metadata?.versionName ?: "未知"} · ${formatBytes(metadata?.sizeBytes ?: 0)}"
        UpdatePhase.Downloading -> "已下载 ${formatBytes(state.downloadedBytes)} / ${formatBytes(metadata?.sizeBytes ?: 0)}"
        UpdatePhase.PermissionRequired -> "请允许 LSTPoker 安装未知应用"
        UpdatePhase.Installing -> state.message ?: "安装包已校验，正在等待系统确认"
        UpdatePhase.Checking -> "正在连接局域网更新服务…"
        UpdatePhase.UpToDate -> "当前版本已是最新，无需更新"
        UpdatePhase.Failed -> state.message ?: "请检查网络后重试"
        else -> ""
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
                val action = when (state.phase) {
                    UpdatePhase.Available -> "下载" to onConfirm
                    UpdatePhase.Downloading -> "取消" to onDismiss
                    UpdatePhase.PermissionRequired -> "去设置" to onOpenSettings
                    UpdatePhase.Failed -> "重试" to onRetry
                    else -> null
                }
                action?.let { (label, callback) ->
                    TextButton(onClick = callback) { Text(label) }
                }
            }
            if (state.phase == UpdatePhase.Checking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            } else if (state.phase == UpdatePhase.Downloading) {
                val total = metadata?.sizeBytes ?: 0L
                val progress = if (total > 0L) state.downloadedBytes.toFloat() / total else 0f
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
