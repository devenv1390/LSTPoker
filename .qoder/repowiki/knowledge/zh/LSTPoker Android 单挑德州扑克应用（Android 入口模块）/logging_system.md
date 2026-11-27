## 1. 使用方案

项目包含两个独立的 JVM 服务端模块各自引入 **Logback Classic**（`ch.qos.logback:logback-classic`），通过 Ktor 的 SLF4J 桥接输出到标准输出。Android 客户端 (`app`)、桌面客户端 (`desktopApp`) 以及共享库 (`shared`) 均未引入任何日志框架或日志调用代码。

- `server`：Ktor Netty 服务端，对外暴露 WebSocket 游戏服务。
- `admin`：本地管理面板，基于 Ktor CIO 在 `127.0.0.1:8787` 提供 HTTP API，负责通过 `launchctl` 控制 `com.aniapp.pokerdemo.server` 进程及 Cloudflare 隧道，并读取 `/status` 获取房间快照；同时通过文件尾随方式提供 `/api/logs/{name}` 与 `/api/logs/{name}/stream` 接口，供 Web 仪表盘查看 `daemon.log`、`server.log`、`cloudflared.log`。

## 2. 关键文件

- `gradle/libs.versions.toml` — 版本目录中 `logback = "1.5.12"`，并通过 `logback-classic = { group = "ch.qos.logback", name = "logback-classic", version.ref = "logback" }` 暴露给子模块。
- `server/build.gradle.kts` — 声明 `implementation(libs.logback.classic)`，JVM target 锁定为 `JvmTarget.JVM_11` / `JavaVersion.VERSION_11`。
- `server/src/main/resources/logback.xml` — 定义 ConsoleAppender `STDOUT`，格式 `%d{HH:mm:ss.SSS} %-5level %logger{24} - %msg%n`，根 Logger 级别 `INFO`，并将 `io.ktor` 提升为 `WARN`。
- `admin/build.gradle.kts` — 同样声明 `implementation(libs.logback.classic)`，JVM target 锁定为 `JvmTarget.JVM_11` / `JavaVersion.VERSION_11`。
- `admin/src/main/resources/logback.xml` — 独立配置，ConsoleAppender `STDOUT`，格式 `%d{HH:mm:ss.SSS} %-5level %logger{20} - %msg%n`，根 Logger 级别 `INFO`。
- `admin/src/main/kotlin/com/aniapp/pokerdemo/admin/Admin.kt` — Ktor CIO 入口，监听 `127.0.0.1:8787`，实现状态查询、服务启停、隧道重启、日志流式读取等路由。
- `admin/src/main/kotlin/com/aniapp/pokerdemo/admin/LogTail.kt` — 读取 `Sys.stateDir` 下的 `daemon.log`、`server.log`、`cloudflared.log`，支持一次性 tail 与 SSE 流式 follow。

## 3. 架构与约定

- **双独立实例**：`server` 与 `admin` 是两个彼此独立的 JVM 应用，各自拥有独立的 `logback.xml` 与 `main()` 入口，不存在跨模块共享的日志初始化逻辑。
- **单 Sink 输出**：两个模块均仅注册一个名为 `STDOUT` 的 `ConsoleAppender`，无文件、滚动、异步等其他 sink。
- **输出格式**：均为 `%d{HH:mm:ss.SSS} %-5level %logger{N} - %msg%n`，区别仅在 logger 名截断长度——`server` 为 24，`admin` 为 20。
- **根 Logger 级别**：两个模块均为 `INFO`。
- **Ktor 静默**：仅 `server` 的 `logback.xml` 将 `io.ktor` 包显式提升为 `WARN` 级别以抑制默认 INFO 调试输出；`admin` 未做此处理。
- **无结构化字段**：所有日志消息均为纯文本字符串，没有 JSON 编码的结构化字段。
- **无业务日志点**：源码中未发现对 `org.slf4j.Logger`、`java.util.logging`、`kotlinx.logging` 等的直接引用（包括本次变更新增的 `Application.kt`、`Room.kt`、`OnlineSession.kt`），日志主要由 Ktor 内部通过 SLF4J 产出，再由 Logback 消费。
- **admin 的日志聚合职责**：`admin` 不生产自身业务日志，而是通过 `LogTail.files` 映射集中暴露三个外部日志文件（`daemon.log`、`server.log`、`cloudflared.log`），并以 REST + Server-Sent Events 的方式转发给浏览器端。

## 4. 约定与约束

- **仅 server 与 admin 模块使用日志**：经检索 `app/`、`desktopApp/`、`shared/` 下未发现任何日志框架导入或日志调用，日志能力局限于 `server` 与 `admin` 模块。
- **Logback 为唯一实现**：依赖声明仅包含 `logback-classic`，未引入其他日志门面（如 SLF4J 独立绑定）或后端（如 Logstash、FileAppender）。
- **所有日志经 Ktor 的 SLF4J 通道产生**：源码中未见对 `java.util.logging`、`kotlinx.logging`、`org.slf4j.Logger` 等的直接引用，日志主要由 Ktor 内部通过 SLF4J 产出，再由 Logback 消费。
- **运行环境约束**：`server/build.gradle.kts` 与 `admin/build.gradle.kts` 均将 JVM target 锁定为 `JvmTarget.JVM_11` / `JavaVersion.VERSION_11`，Logback 需在此 JDK 版本上运行。
- **admin 访问约束**：`Admin.kt` 在拦截器中校验 Host 头必须为 `127.0.0.1`、`localhost` 或 `::1`，POST 请求必须携带 `X-Admin-Request=1` 头，否则返回 403；OPTIONS 请求一律拒绝。