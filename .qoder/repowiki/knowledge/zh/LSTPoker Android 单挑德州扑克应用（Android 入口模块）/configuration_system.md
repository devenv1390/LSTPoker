## 概述

项目为 Kotlin Multiplatform **五模块**工程：`app`（Android 入口）、`shared`（engine/protocol/session/ui 公共库）、`server`（Ktor WebSocket 房间服务）、`desktopApp`（Compose Desktop 客户端）与 `:admin`（本地管理面板）。所有"配置"集中在 Gradle 构建系统与 Android/Kotlin 资源中；不存在运行时配置文件（无 `config/`、`.yaml`、`.env*`、`application.properties`），也没有 Feature Flag、远程配置或密钥管理逻辑。

## 关键文件

- `settings.gradle` — 根工程入口，声明 `pluginManagement.repositories`（阿里云 Google/Central/Gradle Plugin Portal + google() + mavenCentral() + gradlePluginPortal()），并通过 `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 集中仓库，`include ':app'`, `':shared'`, `':server'`, `':admin'`, `':desktopApp'` 注册五个子模块。
- `gradle/libs.versions.toml` — Version Catalog 单一来源：集中 AGP (`8.13.2`)、Kotlin (`2.0.21`)、Compose Multiplatform (`1.7.3`)、Ktor (`3.0.1`)、Serialization (`1.7.3`)、Logback (`1.5.12`)、Coroutines (`1.10.2`) 及所有 `[libraries]`、`[plugins]` 别名。
- `gradle.properties` — 项目级 Gradle 属性：JVM 参数 (`org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`)、`android.useAndroidX=true`、`kotlin.code.style=official`、`android.nonTransitiveRClass=true`。
- `build.gradle`（根）— 仅以 `apply false` 声明 top-level plugins 别名，避免子模块重复 apply。
- `app/build.gradle` — Android 应用模块：`namespace 'com.aniapp.pokerdemo'`、compileSdk/targetSdk 36、minSdk 28；基于 epoch `2026-11-17 00:00 Asia/Shanghai` 的分钟级 `autoVersion`；Release 签名复用 `~/.android/debug.keystore`；APK 输出名格式 `LSTPoker-v<versionName>-<buildType>-yyyyMMdd-HHmm.apk`；依赖 `project(':shared')`。
- `shared/build.gradle.kts` — Kotlin Multiplatform 库：`androidTarget` + `jvm("desktop")` 双目标；`commonMain` 暴露 Compose + Ktor Client + Serialization + Coroutines API；`androidMain` 引入 coroutines-android，`desktopMain` 引入 coroutines-swing。
- `server/build.gradle.kts` — JVM 应用：主类 `com.aniapp.pokerdemo.server.ApplicationKt`，依赖 `:shared` 与 Ktor Server/WebSocket/Content Negotiation/Serialization；提供 `e2eClient` JavaExec task，支持 `-Pe2eArgs="..."` 与 `-Pe2eJvmArgs="..."` 传入参数。
- `desktopApp/build.gradle.kts` — Compose Desktop 应用：主类 `com.aniapp.pokerdemo.desktop.MainKt`，依赖 `:shared` 与 `compose.desktop.currentOs`。
- `admin/build.gradle.kts` — 独立 JVM 应用：主类 `com.aniapp.pokerdemo.admin.AdminKt`，使用 Ktor CIO (`ktor-server-core` + `ktor-server-cio` + `ktor-server-content-negotiation` + `ktor-serialization-kotlinx-json`) 与 Logback；**不依赖 `:shared`**，独立于业务模块。
- `admin/src/main/kotlin/com/aniapp/pokerdemo/admin/Admin.kt` — Ktor CIO 服务器，绑定 `127.0.0.1:8787`；通过 Host/IP 白名单 (`127.0.0.1`、`localhost`、`::1`) 与 `X-Admin-Request=1` 请求头限制访问；提供 `/api/status`、`/api/service/{action}` (start/stop/restart/rebuild)、`/api/tunnel/restart`、`/api/logs/{name}`、`/api/logs/{name}/stream` 等 REST 端点；通过 HTTP 调用 `http://127.0.0.1:8080/status` 获取 server 的房间快照。
- `admin/src/main/kotlin/com/aniapp/pokerdemo/admin/Sys.kt` — 系统操作封装：通过 `launchctl print` / `bootout` / `bootstrap` 控制 macOS LaunchAgent `com.aniapp.pokerdemo.server`；解析 plist 判断 Cloudflare 或 localhost.run (`lhr`) 隧道模式；`lsof -iTCP:8080` 探测 server PID，`pgrep -f cloudflared tunnel --no-autoupdate` 探测隧道进程；状态持久化至 `~/.config/poker-daemon/address`。
- `admin/src/main/resources/logback.xml`、`dashboard.html` — 管理面板日志与前端界面。
- `scripts/install-admin.sh` — 安装脚本：执行 `./gradlew :admin:installDist`，复制 `scripts/com.aniapp.pokerdemo.admin.plist` 到 `~/Library/LaunchAgents/`，通过 `launchctl bootstrap gui/$UID/com.aniapp.pokerdemo.admin` 启动，轮询等待 `http://127.0.0.1:8787/` 就绪。
- `server/src/main/resources/logback.xml` — 服务器日志配置：Console Appender，pattern `%d{HH:mm:ss.SSS} %-5level %logger{24} - %msg%n`，root level INFO，`io.ktor` 包降级至 WARN。
- `app/src/main/res/values/{colors,strings,themes}.xml` — 资源型配置值，由 `shared/ui/theme/Theme.kt`/`Color.kt`/`Type.kt` 消费。
- `scripts/host-online.sh` / `scripts/poker-daemon.sh` / `scripts/com.aniapp.pokerdemo.server.plist` / `scripts/proxychains.conf` — 互联网开房与守护进程脚本。

## 架构与约定

1. **五模块分层**：`app` → `:shared` ← `server` / `desktopApp`；`:admin` 独立于 `:shared`，仅通过 HTTP 与 `server` 通信（`http://127.0.0.1:8080/status`），不走共享库。
2. **依赖版本单一来源**：所有第三方库版本号集中在 `gradle/libs.versions.toml` 的 `[versions]` 段，各模块通过 `libs.xxx` 引用，禁止直接写版本号。
3. **Plugin 别名化**：根 `build.gradle` 用 `alias(libs.plugins.xxx) apply false` 注册插件，子模块再 `alias(libs.plugins.android.application)` 等应用，保证 AGP/Kotlin/Compose/Multiplatform plugin 版本一致。
4. **Repository 集中管理**：`settings.gradle` 中统一声明阿里云镜像 + Google Maven + Maven Central，并启用 `FAIL_ON_PROJECT_REPOS` 禁止子模块自行添加仓库。
5. **动态版本号生成**：`app/build.gradle` 定义 `epochCal` 固定为 `2026-11-17 00:00 Asia/Shanghai`，以 `(当前时间 - epoch)/60000` 计算 `autoVersion`（最小为 1），设置 `versionCode`/`versionName` 并通过 `resValue` 写入 `@string/app_version`。
6. **Release 签名复用 debug.keystore**：`signingConfigs.release` 读取 `System.getProperty("user.home") + "/.android/debug.keystore"`，密码均为 `android`；注释明确说明演示阶段可直装，正式分发前需替换为正式 keystore。
7. **APK 命名约定**：`outputFileName = "LSTPoker-v${variant.versionName}-${variant.buildType.name}-${buildStamp}.apk"`，`buildStamp = new Date().format("yyyyMMdd-HHmm", TimeZone.getTimeZone("Asia/Shanghai"))`。
8. **Build Types 极简**：仅 `release` 一个变体，关闭混淆 (`minifyEnabled false`)，使用 `proguard-rules.pro` 作为自定义规则。
9. **KMP 源集依赖隔离**：`shared/build.gradle.kts` 中 `commonMain` 暴露跨平台 API，`androidMain` 仅追加 coroutines-android，`desktopMain` 仅追加 coroutines-swing，实现平台差异化依赖。
10. **Server 启动与 E2E 测试**：`server/build.gradle.kts` 通过 `application.mainClass.set(...)` 指定 Ktor 入口；`logback.xml` 控制日志级别与格式；`e2eClient` task 通过 `project.findProperty("e2eArgs")` 解析命令行参数，并支持 `-Pe2eJvmArgs="..."` 传递 JVM 参数。
11. **本地管理面板 (:admin)**：`admin/build.gradle.kts` 通过 `application.mainClass.set("com.aniapp.pokerdemo.admin.AdminKt")` 指定 Ktor CIO 入口；Admin 监听 `127.0.0.1:8787`，通过 `Sys.startOp(action)` 调用 launchctl 控制 `com.aniapp.pokerdemo.server` 服务（start/stop/restart/rebuild）与 Cloudflare 隧道；`/api/status` 聚合 service/server/tunnel/public/op 状态；`/api/logs/{name}/stream` 通过 SSE 推送日志；通过 HTTP GET `http://127.0.0.1:8080/status` 拉取 server 房间快照。
12. **互联网联机部署脚本**：`scripts/host-online.sh` 负责本地构建 `:server:installDist`、等待 8080 端口就绪、依次尝试 Cloudflare 与 localhost.run 隧道，并把返回的 https URL 前缀替换为 `wss://` 供客户端连接。
13. **无运行时配置加载器**：源码中未发现任何 `SharedPreferences`、`DataStore`、`@ConfigurationProperties`、YAML/JSON 解析、环境变量读取或 Feature Flag 开关——UI 状态由 Compose 驱动，网络地址等硬编码在协议层。

## 约束与规则

- 新增依赖或升级版本时，应修改 `gradle/libs.versions.toml` 而非直接在任意模块的 build 脚本中写死版本号（由 Version Catalog 机制强制约束引用方式）。
- `settings.gradle` 通过 `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 禁止子模块自行声明仓库，所有依赖仓库必须在此集中配置。
- `gradle.properties` 中的 JVM 内存上限为 2048 MB，如需调整需在此处修改。
- 包名固定为 `com.aniapp.pokerdemo`（`app`）与 `com.aniapp.pokerdemo.shared`（`shared`），同时出现在各自模块的 `namespace` 字段中。
- 应用与服务均编译于 Java 11 兼容模式（`sourceCompatibility`/`targetCompatibility`/`jvmTarget = 11`）。
- Android 最低支持 API 28，目标与编译 SDK 均为 36。
- 发布构建默认不执行代码混淆与资源压缩（`minifyEnabled false`），需在正式发布前显式开启。
- 版本号由构建脚本自动生成，禁止手动编辑 `versionCode`/`versionName`；版本号基于自 `2026-11-17`（Asia/Shanghai 时区）起的分钟数单调递增。
- Release APK 签名默认复用用户主目录下的 `~/.android/debug.keystore`，正式分发前应替换为正式 keystore。
- 生成的 APK 文件名必须遵循 `LSTPoker-v<versionName>-<buildType>-yyyyMMdd-HHmm.apk` 格式。
- Server 端 E2E 客户端通过 `./gradlew :server:e2eClient -Pe2eArgs="<参数>" [-Pe2eJvmArgs="<JVM 参数>"]` 调用，参数通过 `project.findProperty("e2eArgs")` / `e2eJvmArgs` 解析。
- 互联网联机部署通过 `./scripts/host-online.sh` 执行；若本机未安装 `cloudflared` 或隧道不可达，将回退到 `localhost.run` SSH 反向代理；中国大陆网络可通过 `POKER_PROXY=127.0.0.1:7897` 指定代理地址。
- Admin 面板通过 `./scripts/install-admin.sh` 安装，使用 `launchctl bootstrap gui/$UID/com.aniapp.pokerdemo.admin` 启动，仅本机 `127.0.0.1:8787` 可访问；非预期 Host/IP 或非 `X-Admin-Request=1` POST 请求将被拒绝。
- Admin 服务通过 `Sys.stateDir = ~/.config/poker-daemon` 持久化 address 文件，通过 `plist` 文件内容判断 Cloudflare 或 localhost.run 隧道模式。