## 结论
LSTPoker 为 Kotlin Multiplatform 五模块项目（`app` / `shared` / `server` / `admin` / `desktopApp`），错误处理策略围绕 **`kotlin.check` 前置断言 + `GameEvent` 成功事件流**，并通过独立的 `:server` Ktor WebSocket 服务与 `:admin` 本地 HTTP API 提供带 code 的结构化业务错误码。共享引擎不抛出受检异常、不返回 `Result`；非法调用以 `IllegalStateException` 冒泡，由服务端捕获后映射为 `ServerMessage.Error`。客户端通过 `OnlineSession.Phase.Ended` 与 `_error: StateFlow<String?>` 呈现连接级/动作级两类错误。

## 关键文件与包
- `shared/src/commonMain/kotlin/com/aniapp/pokerdemo/engine/TableEngine.kt`：公共引擎，使用 `check(condition) { message }` 校验阶段、轮次、加注范围等不变量；`require(players.size in 2..6)` 校验构造参数。
- `shared/src/commonMain/kotlin/com/aniapp/pokerdemo/engine/GameEvent.kt`：`sealed interface GameEvent` 定义所有成功路径离散事件（`BlindsPosted`、`HoleCardsDealt`、`ActionTaken`、`StreetDealt`、`UncalledReturned`、`ShowdownRevealed`、`PotAwarded`、`HandFinished`、`MatchFinished`）。
- `shared/src/commonMain/kotlin/com/aniapp/pokerdemo/net/Protocol.kt`：`@Serializable sealed interface ServerMessage` 中唯一错误类型 `data class Error(val code: String, val message: String)`；`ClientMessage.CreateRoom` 新增可选字段 `difficulty: AiDifficulty = AiDifficulty.STANDARD`，随房间创建透传到服务器。
- `shared/src/commonMain/kotlin/com/aniapp/pokerdemo/session/LocalSession.kt`：本地会话，无 try/catch，直接调用 `engine.act()` / `startHand()` 并通过 `publish()` 更新 `GameUiState.lastEvents`。
- `shared/src/commonMain/kotlin/com/aniapp/pokerdemo/session/OnlineSession.kt`：在线会话，将 `ServerMessage.Error` 按 code 分类：`join_failed`、`not_found`、`bad_seat_count` 转为 `Phase.Ended(message)`；其余写入瞬时 `_error` 并提供 `clearError()`；支持 `backToLobby()` 在结算覆盖层后返回大厅。
- `server/src/main/kotlin/com/aniapp/pokerdemo/server/Application.kt`：Ktor WebSocket 入口，`pingPeriodMillis = 15_000`、`timeoutMillis = 30_000`；消息反序列化失败时返回 `ServerMessage.Error("bad_message", …)`；连接握手失败时客户端侧重试两次并指数退避；对 `CreateRoom` 中的 `difficulty` 字段直传给 `RoomManager.create(seatCount, aiFill, difficulty)`。
- `server/src/main/kotlin/com/aniapp/pokerdemo/server/Room.kt`：房间管理器，定义业务异常 `RoomException`，并在 `requestAct` 中 `catch (ex: IllegalStateException)` 将其转换为 `sendError(seat, "illegal_action", …)`；`create(seatCount, aiFill, difficulty)` 通过 `AiProfiles.server(difficulty)` 初始化 AI 策略；`seatCount` 使用 `require(2..6)` 校验，违反时抛 `IllegalArgumentException`（不同于引擎 `check`）。
- `admin/src/main/kotlin/com/aniapp/pokerdemo/admin/Admin.kt`：基于 Ktor CIO 的本地 HTTP 服务（监听 `127.0.0.1:8787`），通过全局拦截器校验 Host 白名单（仅 `127.0.0.1`、`localhost`、`::1`）、禁止 `OPTIONS`、要求 POST 携带 `X-Admin-Request=1`；`/api/status` 聚合服务状态，`/api/service/{action}` 控制 `com.aniapp.pokerdemo.server` launchd 服务，`/api/tunnel/restart` 重启 Cloudflare 隧道，`/api/logs/{name}` 与 `/api/logs/{name}/stream` 提供日志尾随与 SSE 推送。
- `admin/src/main/kotlin/com/aniapp/pokerdemo/admin/Sys.kt`：封装 `launchctl` / `lsof` / `pgrep` / `ps` 调用，管理 `~/.config/poker-daemon` 状态目录，周期探测公网 `https://poker.lain-priv.work/ws`（每 20s），通过 HTTP GET `http://127.0.0.1:8080/status` 读取游戏快照，`healthyCodes = setOf(400, 426, 101)` 判定服务健康。
- `app/src/main/java/com/aniapp/pokerdemo/MainActivity.kt`：Android 单 Activity 入口，无任何全局异常处理器或日志框架。

## 架构与约定
1. **校验即错误**：`TableEngine` 在方法入口使用 `check(condition) { message }` 做前置断言——`startHand()` 要求 `state.phase == Phase.IDLE || state.phase == Phase.HAND_OVER`；`act()` 要求 `state.phase.isBetting() && state.acting == seat`；`Check` 要求 `legal.canCheck`；`Call` 要求 `legal.canCall`；`RaiseTo` 要求 `legal.canRaise` 且金额在 `[minRaiseTo, maxRaiseTo]` 范围内。违反条件抛出 `IllegalStateException`，调用方不捕获，由上层暴露违规调用。
2. **成功路径走事件流**：每次状态变更追加一个或多个 `GameEvent`，`startHand()` / `act()` 均返回 `List<GameEvent>`。`LocalSession.publish()` 将事件附加到 `GameUiState.lastEvents`，AI 协程也通过同一通道推进。
3. **服务端结构化错误响应**：`RoomException` 是服务端的业务异常类型，用于表示“房间已满”“对局进行中无法加入”等可恢复的业务错误；`Application.kt` 与 `Room.kt` 统一通过 `reply(ServerMessage.Error(code, message))` 返回给客户端。`RequestAct` 中仅捕获 `IllegalStateException`（来自引擎 `check`），其他异常（如 JSON 解析）用宽泛 `catch (_: Exception)` 包装为 `bad_message`。
4. **客户端错误分类**：`OnlineSession.handle()` 对 `ServerMessage.Error` 按 code 分类：`join_failed`、`not_found`、`bad_seat_count` 视为连接/房间级错误，转为 `Phase.Ended(message)`；其余作为瞬时错误存入 `_error` 并由 UI 展示。`OnlineConfig.secure` 控制连接失败时的提示文案，适配 wss:// 公网隧道场景。
5. **WebSocket 保活与容错**：服务端启用 `pingPeriodMillis = 15_000`、`timeoutMillis = 30_000`；客户端在连接握手失败时重试两次并指数退避（初始 1500ms），整体受 30s 超时限制；连接断开时自动进入 `Phase.Ended`。
6. **结算覆盖层保留**：服务器广播 `Lobby` 时，若当前处于 `Phase.Playing` 且 `table.result != null`，则挂起等待玩家通过 `backToLobby()` 主动返回，避免结算覆盖层被直接跳过。
7. **管理面板安全边界**：`:admin` 模块独立于 `:shared`，通过 Ktor CIO 在回环地址暴露 HTTP API；所有请求经全局拦截器校验 Host 白名单与 `X-Admin-Request` 头，POST 动作仅限 `{start, stop, restart, rebuild, tunnel}`，并发操作通过 `AtomicReference` 互斥，返回 `HttpStatusCode.Conflict` 表示已有操作进行中；`/api/logs/{name}/stream` 使用 SSE 推送日志，心跳行 `:hb` 用于保活检测。
8. **无全局异常捕获**：`MainActivity.kt` 仅为 Compose 入口；`LocalSession` 不捕获引擎抛出的异常；未引入日志框架或全局 `UncaughtExceptionHandler`。
9. **AI 难度透传路径**：`ClientMessage.CreateRoom.difficulty: AiDifficulty`（默认 `STANDARD`）→ Application.kt 转调 `manager.create(seatCount, aiFill, difficulty)` → Room.kt 中 `AiProfiles.server(difficulty)` 构造策略注入房间。该字段沿网络协议向上游传递，不承载错误语义。

## 约束
- 本仓库未声明任何强制性的错误处理规范；以上描述为对现有源码的观察总结。
- 当前实现的约束是事实性的：共享引擎对外 API 不抛出受检异常、不返回 Result，任何违反 `check` 条件都会以 `IllegalStateException` 形式向上冒泡；服务端是唯一主动捕获并转换该异常的位置，将其映射为带 code 的 `ServerMessage.Error`。新增引擎方法应沿用 `check` + `GameEvent` 模式，避免引入新的异常类型或 try/catch。