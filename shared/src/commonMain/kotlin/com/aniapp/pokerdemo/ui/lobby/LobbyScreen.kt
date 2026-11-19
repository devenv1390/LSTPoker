package com.aniapp.pokerdemo.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aniapp.pokerdemo.engine.ai.AiDifficulty
import com.aniapp.pokerdemo.net.PublicAddress
import com.aniapp.pokerdemo.net.ServerAddress
import com.aniapp.pokerdemo.session.OnlineConfig
import com.aniapp.pokerdemo.session.OnlineSession
import com.aniapp.pokerdemo.ui.start.DifficultyChips
import com.aniapp.pokerdemo.ui.theme.Gold
import com.aniapp.pokerdemo.ui.theme.TableBackground
import com.aniapp.pokerdemo.ui.theme.TableSurface
import kotlinx.coroutines.launch

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Gold,
    focusedBorderColor = Gold,
    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
    focusedLabelColor = Gold,
    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
)

@Composable
fun LobbyForm(
    onConnect: (OnlineConfig) -> Unit,
    onBack: () -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(true) }
    var seatCount by remember { mutableIntStateOf(4) }
    var aiFill by remember { mutableStateOf(true) }
    var difficulty by remember { mutableStateOf(AiDifficulty.STANDARD) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun connectWith(addr: ServerAddress, autoDiscovery: Boolean = false) {
        error = null
        onConnect(
            OnlineConfig(
                host = addr.host,
                port = addr.port,
                secure = addr.secure,
                nickname = nickname.trim().ifEmpty { "玩家" },
                seatCount = if (creating) seatCount else null,
                joinCode = if (creating) null else code,
                aiFill = aiFill,
                autoDiscovery = autoDiscovery,
                difficulty = difficulty
            )
        )
    }

    Box(Modifier.fillMaxSize().background(TableBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("联机对战", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Gold)
            Spacer(Modifier.height(4.dp))
            Text("局域网或互联网对战，AI 补位可选", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it.take(12) },
                label = { Text("昵称") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                label = { Text("服务器地址") },
                placeholder = { Text("留空 = 公共服务器，或 wss://… / 192.168.1.5:8080", color = Color.White.copy(alpha = 0.3f)) },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton("创建房间", creating, Modifier.weight(1f)) { creating = true }
                ModeButton("加入房间", !creating, Modifier.weight(1f)) { creating = false }
            }
            Spacer(Modifier.height(16.dp))
            if (creating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { seatCount = (seatCount - 1).coerceAtLeast(2) },
                        enabled = seatCount > 2
                    ) { Text("−") }
                    Text("座位数 $seatCount", color = Color.White, fontSize = 16.sp)
                    OutlinedButton(
                        onClick = { seatCount = (seatCount + 1).coerceAtMost(6) },
                        enabled = seatCount < 6
                    ) { Text("+") }
                }
                Spacer(Modifier.height(12.dp))
                AiFillRow(checked = aiFill, enabled = true, onCheckedChange = { aiFill = it })
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("AI 难度", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    DifficultyChips(selected = difficulty, onSelect = { difficulty = it })
                }
            } else {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter { c -> c in 'A'..'Z' }.take(4) },
                    label = { Text("房间码（4 位字母）") },
                    singleLine = true,
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = Color(0xFFE57373), fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                enabled = !resolving,
                onClick = {
                    if (!creating && code.length != 4) {
                        error = "房间码为 4 位字母"
                        return@Button
                    }
                    val addr = ServerAddress.parse(address)
                    when {
                        addr != null -> connectWith(addr)
                        address.isNotEmpty() -> error = "服务器地址不合法（支持 wss://…、ws://… 或 host:port）"
                        else -> {
                            resolving = true
                            error = null
                            scope.launch {
                                val public = PublicAddress.resolve()
                                resolving = false
                                if (public == null) {
                                    error = "无法获取公共服务器地址：请检查网络，或手动填写地址"
                                } else {
                                    connectWith(public, autoDiscovery = true)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF241B04))
            ) {
                Text(
                    when {
                        resolving -> "获取公共服务器地址…"
                        creating -> "创建并进入房间"
                        else -> "加入房间"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("返回", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Gold else TableSurface,
            contentColor = if (selected) Color(0xFF241B04) else Color.White.copy(alpha = 0.7f)
        )
    ) { Text(label) }
}

@Composable
fun LobbyView(
    lobby: OnlineSession.Phase.InLobby,
    error: String?,
    onStart: () -> Unit,
    onAiFill: (Boolean) -> Unit,
    onLeave: () -> Unit
) {
    val isHost = lobby.mySeat == lobby.hostSeat
    val humans = lobby.seats.count { !it.isAi }
    val canStart = lobby.aiFill || humans >= 2
    Box(Modifier.fillMaxSize().background(TableBackground)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("房间码", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
            Text(
                lobby.roomCode,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
                letterSpacing = 8.sp
            )
            Text("把房间码告诉朋友，让他们加入", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            Spacer(Modifier.height(20.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TableSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in 0 until lobby.seatCount) {
                    val seat = lobby.seats.find { it.seat == i }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("${i + 1}", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp, modifier = Modifier.width(20.dp))
                        if (seat == null) {
                            Text(
                                if (lobby.aiFill) "空位 · 开始后 AI 补位" else "空位",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 15.sp
                            )
                        } else {
                            Text(
                                seat.name + if (seat.seat == lobby.mySeat) "（你）" else "",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (seat.seat == lobby.hostSeat) Tag("房主", Gold)
                            if (seat.isAi) Tag("AI", Color(0xFF4A6FA5))
                            if (!seat.connected) Tag("托管", Color(0xFF4A4A4A))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            AiFillRow(checked = lobby.aiFill, enabled = isHost, onCheckedChange = onAiFill)
            if (!isHost) {
                Text("AI 补位由房主控制", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = Color(0xFFE57373), fontSize = 13.sp)
            }
            Spacer(Modifier.height(24.dp))
            if (isHost) {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor = Color(0xFF241B04),
                        disabledContainerColor = TableSurface,
                        disabledContentColor = Color.White.copy(alpha = 0.35f)
                    )
                ) {
                    Text("开始游戏", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                if (!canStart) {
                    Spacer(Modifier.height(8.dp))
                    Text("已关闭 AI 补位：至少 2 名玩家加入后才能开始", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
            } else {
                Text("等待房主开始游戏…", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                Text("离开房间", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun AiFillRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("空位 AI 补位", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (checked) "开局时空位由 AI 补齐" else "仅真人玩家开局（至少 2 人）",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF241B04),
                checkedTrackColor = Gold,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = TableSurface,
                uncheckedBorderColor = Color.White.copy(alpha = 0.3f),
                disabledCheckedThumbColor = Color(0xFF241B04).copy(alpha = 0.5f),
                disabledCheckedTrackColor = Gold.copy(alpha = 0.4f),
                disabledUncheckedThumbColor = Color.White.copy(alpha = 0.3f),
                disabledUncheckedTrackColor = TableSurface.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun Tag(label: String, color: Color) {
    Text(
        label,
        color = Color.White,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        modifier = Modifier
            .background(color, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}