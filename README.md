# LSTPoker

基于 Kotlin Multiplatform 与 Compose 的德州扑克应用，支持 Android、桌面端单机对战，以及通过 Ktor WebSocket 服务器进行局域网或互联网联机。

联机模式采用服务端权威架构：牌局引擎只在服务端运行，客户端只能看到自己的底牌和允许公开的牌桌信息。

## 功能

- 单机模式：1 名玩家对战 1–5 名电脑对手
- 联机模式：创建或加入 4 位字母房间码的房间
- 空位 AI 补位：房主可选择是否让电脑填补空位
- 多档 AI 难度，包含规则策略与蒙特卡洛胜率模拟
- 本地 AI 决策辅助：展示胜率、听牌信息和行动建议
- Android 应用内检查更新、下载并安装 APK
- 本地管理面板：查看房间与日志，控制服务器和隧道服务

## 技术栈

- Kotlin Multiplatform
- Jetpack Compose / Compose Multiplatform
- Ktor Client / Server
- kotlinx.serialization
- kotlinx.coroutines
- Gradle Version Catalog
- Android Gradle Plugin

## 模块

| 模块 | 职责 |
|---|---|
| `app` | Android 应用入口、资源、更新与安装流程 |
| `shared` | 共享牌局引擎、AI、网络协议、会话状态和 Compose UI |
| `server` | Ktor WebSocket 联机服务器 |
| `admin` | 本机管理面板和 APK 分发服务 |
| `desktopApp` | Compose Multiplatform 桌面客户端 |

## 环境要求

- JDK 17
- Android Studio，安装 Android SDK 36
- Gradle Wrapper 已包含，无需单独安装 Gradle
- macOS/Linux 命令行可使用 `./gradlew`；Windows 使用 `gradlew.bat`

首次构建需要下载 Gradle、Kotlin、Compose 和 Android 依赖，请保持网络畅通。

## 快速开始

### 克隆项目

```bash
git clone https://github.com/devenv1390/LSTPoker.git
cd LSTPoker
```

### 运行 Android 客户端

使用 Android Studio 打开项目根目录，等待 Gradle 同步完成后，选择 `app` 配置并启动到模拟器或真机。

命令行构建 Debug APK：

```bash
./gradlew :app:assembleDebug
```

构建产物位于 Android 模块的 `build/outputs/apk/debug/` 目录，APK 文件名包含自动生成的版本号和时间戳。

### 运行桌面客户端

```bash
./gradlew :desktopApp:run
```

### 本地单机

启动 Android 或桌面客户端后，在首页选择“单机游戏”，设置 AI 难度并开始牌局，无需启动服务器。

## 联机对战

### 局域网

启动服务器：

```bash
./gradlew :server:run
```

服务器默认监听 `0.0.0.0:8080`。同一局域网内的客户端在“联机对战”中填写服务器电脑的局域网地址，例如：

```text
192.168.1.5:8080
```

其中一名玩家创建房间，其他玩家使用 4 位房间码加入。

### 互联网临时开局

macOS/Linux 可运行一键脚本，自动构建服务器并尝试建立 Cloudflare 临时隧道；失败时回退到 localhost.run：

```bash
./scripts/host-online.sh
```

脚本会输出 `wss://` 地址。将该地址发给其他玩家，在“联机对战”中填写即可。临时地址会在脚本重启后变化，且服务器电脑必须保持运行。

如需通过本地代理连接隧道：

```bash
POKER_PROXY=127.0.0.1:7897 ./scripts/host-online.sh
```

长期部署、固定域名、Cloudflare 命名隧道和 macOS 常驻服务请参阅 [部署指南](DEPLOY.md)。

### 本机管理面板

```bash
./gradlew :admin:run
```

默认访问地址：<http://127.0.0.1:8787>

macOS 可通过安装脚本将面板注册为 launchd 服务：

```bash
./scripts/install-admin.sh
```

## 测试

```bash
./gradlew :shared:desktopTest
./gradlew :server:test
./gradlew :admin:test
./gradlew :app:testDebugUnitTest
```

也可以在 Android Studio 中直接运行相应模块的测试。

## Release APK 签名

项目不会读取或提交签名密钥。构建 Release APK 前，请通过环境变量或本机 Gradle 配置提供：

```text
POKER_RELEASE_STORE_FILE
POKER_RELEASE_STORE_PASSWORD
POKER_RELEASE_KEY_ALIAS
POKER_RELEASE_KEY_PASSWORD
```

配置完成后执行：

```bash
./gradlew :app:assembleRelease
```

未配置签名时，Release 构建会主动失败，避免生成无法安装的 APK。

## 使用边界

- 房间保存在服务器内存中，服务器重启会丢失房间。
- 当前不提供断线重连；手中牌局断线后会由 AI 托管。
- 4 位字母房间码适合熟人娱乐，不适合作为私密房间凭证。
- 项目仅用于娱乐和技术学习，不涉及真实货币或赌博服务。

## 许可

当前仓库尚未包含开源许可证。公开代码不代表已授予再分发或商业使用许可；如需使用，请先联系仓库所有者。
