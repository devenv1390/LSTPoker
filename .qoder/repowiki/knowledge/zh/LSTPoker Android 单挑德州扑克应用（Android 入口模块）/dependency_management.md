## 1. 使用的工具与方案

项目为 **Kotlin Multiplatform** 多模块工程，基于 **AGP 8.13.2** + **Kotlin 2.0.21** + **Compose Multiplatform 1.7.3**。所有第三方库的版本通过 **Gradle Version Catalog（`gradle/libs.versions.toml`）** 集中声明；Android/Compose 相关依赖统一走 `androidx.compose:compose-bom`。仓库使用阿里云 Maven 镜像作为主要源，未配置任何私有 registry。

根工程包含五个子模块：
- `:app` — Android 应用入口，仅依赖 `:shared`
- `:shared` — Kotlin Multiplatform 公共库（engine / protocol / session / ui）
- `:server` — Ktor WebSocket 房间服务（JVM），依赖 `:shared`
- `:admin` — 本地管理面板（JVM），独立于 `:shared`，通过 Ktor CIO 在 127.0.0.1:8787 提供 HTTP API，负责通过 launchctl 控制 `com.aniapp.pokerdemo.server` 系统服务及 Cloudflare 隧道，并读取 `/status` 获取房间快照
- `:desktopApp` — Compose Desktop 客户端（JVM），依赖 `:shared`

## 2. 关键文件

- `settings.gradle` — 定义五个子模块 `include ':app' | ':shared' | ':server' | ':admin' | ':desktopApp'`，并在 `pluginManagement.repositories` 与 `dependencyResolutionManagement.repositories` 中集中声明仓库，启用 `RepositoriesMode.FAIL_ON_PROJECT_REPOS`。
- `gradle/libs.versions.toml` — 版本目录：集中定义 AGP、Kotlin、AndroidX、JUnit、Espresso、Compose BOM、Lifecycle、Coroutines、Compose Multiplatform、Ktor、Serialization、Logback 等版本号；`[libraries]` 段按平台分组暴露别名（如 `ktor-server-core`、`ktor-client-websockets`、`kotlinx-coroutines-swing`、`logback-classic`）；`[plugins]` 段定义 Android/Kotlin/JVM/Multiplatform/Compose/Serialization 插件 ID。
- `app/build.gradle` — Android 应用构建脚本，通过 `implementation project(':shared')` 消费共享库，并通过 `libs.*` 引用版本目录中的依赖。
- `shared/build.gradle.kts` — Kotlin Multiplatform 库，声明 `androidTarget` + `jvm("desktop")` 两个目标，在 `commonMain` 中引入 Compose Runtime/Foundation/Material3/UI、Ktor Client（core/cio/websockets/content-negotiation/serialization-json）、kotlinx-serialization、coroutines；在 `androidMain` 引入 coroutines-android，在 `desktopMain` 引入 coroutines-swing。
- `server/build.gradle.kts` — JVM 应用，依赖 `:shared` 及 Ktor Server（core/cio/websockets/content-negotiation）、logback、test-host，并注册 `e2eClient` JavaExec task，mainClass 指向 `com.aniapp.pokerdemo.server.ApplicationKt`。
- `admin/build.gradle.kts` — JVM 管理面板应用，依赖 Ktor Server（core/cio/content-negotiation）、kotlinx-serialization、logback，不依赖 `:shared`；mainClass 为 `com.aniapp.pokerdemo.admin.AdminKt`。
- `desktopApp/build.gradle.kts` — Compose Desktop 应用，依赖 `:shared` 与 `compose.desktop.currentOs`、`kotlinx-coroutines-swing`。

## 3. 架构与约定

### 3.1 版本集中化（Version Catalog）

所有第三方库版本集中在 `gradle/libs.versions.toml` 的 `[versions]` 段，例如：

```
agp = "8.13.2"
kotlin = "2.0.21"
composeMultiplatform = "1.7.3"
ktor = "3.0.1"
serialization = "1.7.3"
logback = "1.5.12"
composeBom = "2024.09.00"
coroutines = "1.10.2"
```

具体依赖在 `[libraries]` 中以 `version.ref = "..."` 引用这些版本号，并按用途分组命名（如 `ktor-client-websockets`、`ktor-server-websockets`、`kotlinx-coroutines-swing`）。

### 3.2 Compose 通过 BOM 管理版本

Compose 相关依赖不单独指定版本，而是通过 `platform(libs.androidx.compose.bom)` 引入 BOM（由 app 模块消费），后续 Compose 组件（ui、material3、tooling*）不再带版本号。

### 3.3 插件通过 alias 引用

所有模块都通过 `alias(libs.plugins.*)` 应用插件（如 `kotlin-multiplatform`、`kotlin-jvm`、`compose-multiplatform`、`kotlin-serialization`），插件版本集中在 `[plugins]` 段，避免硬编码。

### 3.4 多模块依赖边界

- `:app` → `:shared`（Android 入口）
- `:server` → `:shared`（Ktor 服务端）
- `:desktopApp` → `:shared`（桌面客户端）
- `:admin` → 仅依赖 Ktor server + logback + serialization，**不依赖 `:shared`**，作为独立的本地管理进程存在
- `:shared` 是唯一的业务与 UI 逻辑中心，对外暴露 Compose 组件与网络协议。

### 3.5 仓库集中配置，禁止子项目覆盖

`settings.gradle` 中同时配置 `pluginManagement.repositories` 与 `dependencyResolutionManagement.repositories`，并设置：

```groovy
repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
```

这意味着 **任何子项目的 `build.gradle` 里不能再声明自己的 `repositories { ... }`**，否则构建失败——这是仓库级强制约束。

### 3.6 仓库顺序与分组

- 插件仓库优先阿里云镜像（`maven.aliyun.com/repository/google|central|gradle-plugin`），然后 `google()`（仅匹配 `com.android.*`、`com.google.*`、`androidx.*`）、`mavenCentral()`、`gradlePluginPortal()`。
- 运行时依赖仓库为阿里云镜像 + `google()` + `mavenCentral()`，未配置任何私有 registry。

## 4. 约定与约束

- **依赖版本必须通过 `gradle/libs.versions.toml` 声明**，不得在模块构建脚本中直接写死版本号。
- **Compose 依赖必须通过 BOM**：`platform(libs.androidx.compose.bom)` 出现在 `dependencies` 块中，后续 Compose 依赖不再带版本号。
- **子模块不能自定义仓库源**：`RepositoriesMode.FAIL_ON_PROJECT_REPOS` 会在检测到子项目有 `repositories { ... }` 时抛出错误，属于硬性约束。
- **插件 ID 通过 `alias(libs.plugins.*)` 引用**，插件版本集中在 `[plugins]` 段。
- **KMP 模块通过 `commonMain` 共享逻辑**，平台差异通过 `androidMain` / `desktopMain` sourceSet 分别引入对应协程实现（`kotlinx-coroutines-android` vs `kotlinx-coroutines-swing`）。
- **版本号自动生成**：`app/build.gradle` 使用距项目起点（2026-11-17 Asia/Shanghai）的分钟数作为 `autoVersion`，`versionName` 格式为 `1.0.<autoVersion>`，`versionCode` 取相同数值，并通过 `resValue` 暴露 `app_version` 字符串。
- **APK 文件名格式固定**：`LSTPoker-v<versionName>-<buildType>-yyyyMMdd-HHmm.apk`。
- **Release 签名复用 debug.keystore**：路径为 `${user.home}/.android/debug.keystore`，密码均为 `android`，注释说明“演示阶段复用 debug 证书使 release 包可直接安装；正式分发前替换为正式 keystore”。
- **Java/Kotlin 编译目标统一为 11**：各模块均设置 `sourceCompatibility`/`targetCompatibility` = `VERSION_11` 或 `JvmTarget.JVM_11`。

## 5. 观察到的依赖清单

| 类别 | 依赖 | 说明 |
|---|---|---|
| Android 基础 | `androidx.core:core-ktx` | 核心扩展（app 模块） |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-ktx`、`lifecycle-runtime-compose`、`lifecycle-viewmodel-compose` | 生命周期与 Compose 集成 |
| Activity | `androidx.activity:activity-compose` | Compose 集成 |
| Coroutines | `kotlinx-coroutines-*`（core/android/swing/test） | 多平台协程引擎与测试支持 |
| Compose | `androidx.compose:*` (ui, graphics, material3, tooling*) | 通过 BOM 管理 |
| Ktor Client | `ktor-client-*`（client 侧） | shared 模块网络客户端 |
| Ktor Server | `ktor-server-*`（core/cio/content-negotiation/serialization-json/websockets） | server 与 admin 模块服务端 |
| Serialization | `kotlinx-serialization-json` + `ktor-serialization-kotlinx-json` | JSON 序列化 |
| 日志 | `ch.qos.logback:logback-classic` | server 与 admin 模块日志 |
| 测试 | `junit:junit`, `androidx.test.ext:junit`, `espresso-core`, `ktor-server-test-host` | 单元/UI/E2E 测试 |
| 构建插件 | AGP、Kotlin (Android/JVM/Multiplatform)、Compose、Serialization | 通过版本目录 alias |

## 6. 缺失项

- 无锁文件（如 `*.lock`、`poetry.lock` 等），因为 Gradle 默认按需解析版本，不生成全局 lockfile。
- 无私有仓库配置（无 `GRADLE_PLUGIN_REGISTRY_URL` 环境变量注入）。
- 无自动升级工具（如 Dependabot、Renovate）配置文件可见。