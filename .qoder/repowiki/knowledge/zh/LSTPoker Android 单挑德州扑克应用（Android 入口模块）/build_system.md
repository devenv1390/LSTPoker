## 1. 使用的构建体系

项目采用 **Android Gradle Plugin (AGP) 8.13.2** + **Kotlin 2.0.21** + **Compose Multiplatform 1.7.3** 的多模块工程，`./gradlew`（Wrapper）是唯一的构建与打包入口。仓库根没有 Makefile、Dockerfile、CI pipeline；`scripts/` 目录下的脚本用于服务器进程守护、公网隧道与本地管理面板的 macOS launchd 部署，不参与 Gradle 构建流程。

## 2. 关键文件

- `settings.gradle`：声明五个子模块 `:app`、`:shared`、`:server`、`:admin`、`:desktopApp`；通过 `pluginManagement.repositories` 限定插件来源（阿里云镜像 + google/mavenCentral/gradlePluginPortal），并通过 `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 禁止子模块再声明仓库。
- `gradle/libs.versions.toml`：Version Catalog，集中定义 AGP、Kotlin、Compose BOM、Lifecycle、Coroutines、JUnit、Espresso、Ktor、Logback 等版本号以及所有 library alias 和 plugin alias。
- `build.gradle`：根构建脚本仅用 `alias(libs.plugins.*) apply false` 预声明插件别名，不启用任何插件。
- `app/build.gradle`：Android 应用模块脚本，包含 namespace、compileSdk/targetSdk/minSdk、动态 `autoVersion`、release 签名、APK 命名规则、compileOptions、kotlinOptions、buildFeatures 及全部 dependencies。
- `shared/build.gradle.kts`：Kotlin Multiplatform 库模块，同时发布 Android Library 与 JVM desktop 目标；commonMain 暴露 Compose UI + Ktor Client + Coroutines。
- `server/build.gradle.kts`：Ktor 服务端模块（application 插件），依赖 `:shared`，提供 WebSocket 房间服务；注册 `e2eClient` JavaExec 任务运行端到端客户端测试。
- `admin/build.gradle.kts`：本地管理面板模块，使用 `kotlin.jvm` + `kotlin.serialization` + `application` 插件；依赖 `ktor-server-core`、`ktor-server-cio`、`ktor-server-content-negotiation`、`ktor-serialization-kotlinx-json`、`logback-classic`；主类 `com.aniapp.pokerdemo.admin.AdminKt`，独立于 `:shared`，在 `127.0.0.1:8787` 提供 HTTP API。
- `desktopApp/build.gradle.kts`：Compose Desktop 客户端模块，依赖 `:shared`，主类 `com.aniapp.pokerdemo.desktop.MainKt`。
- `gradle.properties`：JVM args (`-Xmx2048m -Dfile.encoding=UTF-8`)、`android.useAndroidX=true`、`kotlin.code.style=official`、`android.nonTransitiveRClass=true`。
- `app/proguard-rules.pro`：release 模式下挂载的混淆规则。
- `gradle/wrapper/gradle-wrapper.properties`：由 Android Studio 生成的 Gradle Wrapper。
- `scripts/install-admin.sh`：调用 `./gradlew :admin:installDist` 安装 admin 模块，并安装/启动 `com.aniapp.pokerdemo.admin` launchd plist。
- `scripts/com.aniapp.pokerdemo.admin.plist`：macOS launchd 配置，监听 `/Users/lain/AndroidStudioProjects/PokerDemo/admin/build/install/admin/bin/admin`，输出日志到 `~/Library/Logs/poker-admin.{out,err}.log`。
- `scripts/poker-daemon.sh`、`scripts/poker-daemon-lhr.sh`、`scripts/host-online.sh`、`scripts/com.aniapp.pokerdemo.server.plist`、`scripts/proxychains.conf`：服务器进程守护、公网隧道与代理链部署脚本。

## 3. 架构与约定

- **五模块结构**：`include ':app'`, `':shared'`, `':server'`, `':admin'`, `':desktopApp'`；`app` 仅作为 Android 入口，业务逻辑集中在 `:shared`。
- **版本统一管理**：所有第三方库版本集中在 `gradle/libs.versions.toml`，模块内通过 `libs.<alias>` 引用。
- **Plugin 别名化**：根 `build.gradle` 用 `alias(libs.plugins.*) apply false` 声明插件，再由各模块按需启用。
- **Compile SDK = 36**：`compileSdk { version = release(36) }`，`targetSdk = 36`，`minSdk = 28`。
- **Java/Kotlin 兼容性统一为 11**：每个模块均设置 `sourceCompatibility` / `targetCompatibility` 为 `VERSION_11`，`kotlinOptions.jvmTarget = '11'`（或 Kotlin DSL 中的 `JvmTarget.JVM_11`）。
- **Compose 启用**：`buildFeatures.compose = true`（app），`compose.multiplatform`（shared/desktopApp）。
- **命名空间**：`app` 使用 `com.aniapp.pokerdemo`，`shared` 使用 `com.aniapp.pokerdemo.shared`，配合 `android.nonTransitiveRClass=true`。
- **构建变体**：仅 `debug` 与 `release` 两种 buildType；`release` 中 `minifyEnabled=false`，但已挂载 `proguard-android-optimize.txt` 与 `proguard-rules.pro`。
- **动态版本号（autoVersion）**：在 `app/build.gradle` 中以固定 epoch（2026-11-17 00:00 Asia/Shanghai）计算距该时间点的分钟数作为 `autoVersion`（至少为 1），并据此设置 `versionCode`、`versionName` 与资源字符串 `app_version`。
- **Release 签名复用 debug.keystore**：签名配置指向宿主用户目录 `~/.android/debug.keystore`，密码 `android`，keyAlias `androiddebugkey`，注释说明正式分发前需替换。
- **APK 文件名格式**：`LSTPoker-v${variant.versionName}-${variant.buildType.name}-${yyyyMMdd-HHmm}.apk`，时间戳基于 `Asia/Shanghai` 时区生成。
- **Multiplatform 目标**：`shared` 模块暴露 `commonMain`（Compose + Ktor Client + Coroutines）、`androidMain`（协程 Android 实现）、`desktopMain`（协程 Swing 实现）三个 source set。
- **模块依赖方向**：`app → :shared`；`server → :shared`；`desktopApp → :shared`；`admin` **独立于**:shared，直接依赖 Ktor CIO 与 kotlinx.serialization；`server`、`desktopApp`、`admin` 之间无相互依赖。
- **Admin 模块职责**：基于 Ktor CIO 的独立 JVM application，在 `127.0.0.1:8787` 提供本地 HTTP API；通过 `launchctl` 控制 `com.aniapp.pokerdemo.server` 服务及 Cloudflare 隧道，并读取 `/status` 获取房间快照，供开发者在本机调试。
- **Server 测试**：注册 `e2eClient` JavaExec 任务，可从命令行传入 `e2eArgs` 参数启动端到端客户端。
- **服务器部署脚本**：`scripts/poker-daemon.sh`、`scripts/poker-daemon-lhr.sh` 用于进程守护，`scripts/host-online.sh` 启动公网隧道，`scripts/com.aniapp.pokerdemo.server.plist` 为 macOS launchd 配置文件，`scripts/proxychains.conf` 配置代理链。

## 4. 约定与约束

- **仓库源集中化**：`settings.gradle` 中 `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` 阻止任何子模块重新声明自己的仓库——这是由 Gradle 强执行的约束。
- **插件来源白名单**：`pluginManagement.repositories` 中的 `includeGroupByRegex("com\\.android.*" | "com\\.google.*" | "androidx.*")` 限制从 google 仓库拉取的插件 group。
- **依赖版本单一来源**：所有 `implementation libs.*` 均指向 `libs.versions.toml` 中定义的 alias，未在模块构建脚本中出现裸版本号。
- **测试 runner 固定**：`testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"`。
- **无 CI / 无 Docker / 无自动化发布**：未发现 `.github/workflows`、`.gitlab-ci.yml`、`Dockerfile` 或 shell 构建脚本（`scripts/` 下仅有服务器与管理面板部署辅助脚本，不参与 Gradle 构建流程）。
- **ProGuard 默认关闭**：`release.minifyEnabled = false`，当前 release 构建不会执行代码压缩与混淆。
- **版本号单调自增**：`autoVersion` 基于距固定 epoch（2026-11-17 00:00 Asia/Shanghai）的分钟数，随时间单调递增，无需手动修改版本号。
- **Kotlin/JVM 版本锁定 11**：所有模块（Android、JVM server、JVM admin、Compose Desktop、MP shared）统一将 JVM target 设为 `VERSION_11` / `JvmTarget.JVM_11`，形成跨平台一致的运行时基线。
- **E2E 客户端可插拔参数**：`server/e2eClient` 任务支持 `e2eArgs` 与 `e2eJvmArgs` 两个可选 project property，按空格分割后传递给被测 E2E 客户端。
- **Admin 本地绑定约束**：`admin` 模块通过 Ktor CIO 仅监听 `127.0.0.1:8787`，仅本机可访问；其安装与运行完全依赖 macOS `launchctl` + plist，不适用于 Linux/Windows 环境。