## 1. 使用的体系
- Android / Desktop UI：Jetpack Compose，通过 Kotlin Multiplatform `shared` 模块共享至 `:app`（Android）与 `:desktopApp`（Desktop）。
- 视觉风格：Material Design 3，通过 `androidx.compose.material3.darkColorScheme` 注入自定义深色色板；传统 Android XML Theme 仅保留最小占位。
- 本地管理面板 `:admin`：独立于 `:shared`，使用 Ktor CIO 在 `127.0.0.1:8787` 提供 HTTP API，通过 `launchctl` 控制 `com.aniapp.pokerdemo.server` 服务及 Cloudflare 隧道；其 Web 界面由内嵌 HTML（`dashboard.html`）+ 原生 CSS 实现，**未复用** `:shared/ui/theme` 的设计令牌。

## 2. 关键文件
- `shared/src/commonMain/kotlin/com/aniapp/pokerdemo/ui/theme/Color.kt`：定义 10 个领域语义设计令牌——桌布绿（`FeltGreen` / `FeltDark`）、桌面背景（`TableBackground` / `TableSurface`）、筹码金（`Gold`）、牌面白/红/黑（`CardWhite` / `CardRed` / `CardBlack`）、筹码红（`ChipRed`）和弃牌按钮色（`FoldButton`），全部以 `Color(0xFF...)` 字面量声明。
- `shared/src/commonMain/kotlin/com/aniapp/pokerdemo/ui/theme/Theme.kt`：导出根 Composable `PokerDemoTheme`，使用 `darkColorScheme` 将上述颜色映射到 Material 语义槽位（primary=Gold、secondary=FeltGreen、background=TableBackground、surface=TableSurface、surfaceVariant=0xFF1E3B33 等），并通过 `MaterialTheme(colorScheme = PokerColorScheme, typography = Typography)` 注入全局主题。
- `shared/src/commonMain/kotlin/com/aniapp/pokerdemo/ui/theme/Type.kt`：定义全局 `Typography`，当前只覆盖 `bodyLarge`（`FontFamily.Default`，`fontSize = 16.sp`，`lineHeight = 24.sp`，`letterSpacing = 0.5.sp`）。
- `desktopApp/src/main/kotlin/com/aniapp/pokerdemo/desktop/Main.kt`：Compose Desktop 入口，包裹 `PokerDemoTheme { App() }`。
- `app/src/main/res/values/themes.xml`：Android 侧占位主题，仅继承 `android:Theme.Material.Light.NoActionBar`，不包含任何自定义属性。
- `admin/src/main/resources/dashboard.html`：内嵌 `<style>` 块，使用 CSS 自定义属性（`--felt-900`、`--gold`、`--cream`、`--muted`、`--red`、`--green`、`--amber`、`--blue`、`--surface`、`--border`、`--mono` 等）构建独立的暗色仪表盘样式，与 `:shared/ui/theme` 完全解耦。
- `admin/src/main/kotlin/com/aniapp/pokerdemo/admin/Admin.kt`：Ktor CIO 应用，静态资源目录包含 `dashboard.html` 与 `logback.xml`。

## 3. 架构与约定
- 设计令牌统一位于 KMP `commonMain` 下的 `com.aniapp.pokerdemo.ui.theme` 包中，按职责拆分为 Color / Theme / Type 三个文件，供 `engine/session/net/action/card/lobby/overlay/start/table` 等子包复用。
- 宿主应用（Android 的 `MainActivity` 与 Desktop 的 `Main`）均通过包裹 `PokerDemoTheme { App() }` 作为根 Composable 暴露全局主题；业务界面不直接硬编码颜色/字体。
- 尺寸单位统一使用 Compose 的 `sp`，字体族固定为 `FontFamily.Default`，未引入自定义字体资源。
- 配色整体采用深色主题基调（`PokerColorScheme` 基于 `darkColorScheme`），贴合德州扑克暗色调桌面的视觉预期。
- `:admin` 模块是独立的 Ktor CIO Web 应用，不依赖 `:shared` 的 Compose 主题，而是通过 CSS 变量重新定义了一套暗绿色+金色的视觉语言，与主应用主题概念一致但实现完全分离。

## 4. 约定与约束
- **观察到的约定**：
  - 设计令牌以顶层 `val` 形式声明在 `Color.kt` / `Type.kt` 中，供 Composable 直接引用。
  - 颜色命名遵循领域语义（`Felt*`、`Table*`、`Card*`、`Chip*`、`FoldButton`），而非抽象语义（如 `Primary` / `Secondary`）。
  - 主题入口统一命名为 `PokerDemoTheme`，并在宿主中作为根 Composable 调用。
  - 所有颜色使用十六进制 ARGB 字面量（`Color(0xFF...)`），未在 `res/values/colors.xml` 中重复定义。
  - `:admin` 的仪表盘样式集中在单文件 `dashboard.html` 的 `<style>` 内，通过 CSS 自定义属性组织色彩系统，未使用 CSS 预处理或第三方样式框架。
- **可验证的规则**：
  - 主题始终基于 `darkColorScheme` 构建，未提供浅色 color scheme —— 该选择由 `Theme.kt` 中的 `private val PokerColorScheme = darkColorScheme(...)` 强制实现。
  - 传统 `res/values/themes.xml` 中的 `Theme.PokerDemo` 仅继承 `android:Theme.Material.Light.NoActionBar`，不包含任何自定义属性，说明应用的视觉定制完全由 Compose 层承担。
  - `:admin` 模块的 Web 页面通过 `dashboard.html` 内联样式渲染，不引用外部 CSS 文件或组件库，且与 `:shared/ui/theme` 无代码级依赖。