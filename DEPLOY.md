# LSTPoker 联机部署指南

服务器权威架构：引擎只在服务端运行，客户端只收到自己的底牌。
互联网对战通过公网隧道把本机服务器暴露为 `wss://` 地址（TLS 由隧道边缘终止，服务器本身无需改造）。

## 方式零：常驻公共服务器（本机已部署，推荐）

本机已通过 launchd 常驻运行「服务器 + Cloudflare 命名隧道 + TXT 锚维护」，开机自启、断线自愈。**公网地址固定为 `wss://poker.lain-priv.work`，永不轮换**：

- 服务：`com.aniapp.pokerdemo.server`（`~/Library/LaunchAgents/com.aniapp.pokerdemo.server.plist`）
- 脚本：`scripts/poker-daemon.sh`；状态目录 `~/.config/poker-daemon/`（`address` 为当前地址，`daemon.log`/`server.log`/`cloudflared.log` 为日志）
- 隧道：Cloudflare remotely-managed 命名隧道 `lstpoker`（ingress 在 CF 云端配置：`poker.lain-priv.work` → `http://localhost:8080`，CNAME 指向 `<隧道ID>.cfargotunnel.com`）。凭据 `tunnel-token`（600 权限，勿入 git）、`tunnel-id` 存于状态目录
- **控制面必须走代理**：argotunnel 边缘大陆直连被阻断，脚本以 `--protocol http2`（TCP，Go 认 `HTTPS_PROXY`）+ 本地代理（默认 127.0.0.1:7897，`POKER_PROXY` 可改）启动 cloudflared。默认 QUIC/UDP 过不了 HTTP 代理，勿改回。玩家数据面走 CF 边缘 443，大陆直连无需代理（2026-11-18 手机流量实测通过）
- 自愈：cloudflared 退出 5 秒重启；每 20 秒探测公网 `/ws`，连续两次失败重启 cloudflared；8080 掉线补拉服务器
- TXT 锚：启动时 + 每 5 分钟确保 DNS TXT `_poker.lain-priv.work`（TTL 120）值为 `wss://poker.lain-priv.work`，兼容旧 APK 的 DoH 发现
- 玩家加入：app → 联机对战 → **服务器地址留空** → 创建房间或填房间码加入。客户端经阿里 DoH（`dns.alidns.com`，失败回退 `doh.pub`）读 TXT；DoH 全失败时兜底直连固定域名
- 防休眠：脚本附带 `caffeinate -s`（仅接电源时阻止系统休眠）；合盖/断电仍会中断，唤醒后自动重连（地址不变）

管理命令：

```bash
cp scripts/com.aniapp.pokerdemo.server.plist ~/Library/LaunchAgents/   # 首次安装
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.aniapp.pokerdemo.server.plist
launchctl kickstart -k gui/$(id -u)/com.aniapp.pokerdemo.server   # 重启服务
launchctl bootout gui/$(id -u)/com.aniapp.pokerdemo.server       # 停止并卸载
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.aniapp.pokerdemo.server.plist  # 重新加载
cat ~/.config/poker-daemon/address                               # 查看当前公网地址
```

TXT 维护凭据（二选一）：

1. cf CLI 已登录（OAuth，含 refresh token）。若过期重新授权：
   `HTTPS_PROXY=http://127.0.0.1:7897 NODE_OPTIONS=--use-env-proxy cf auth login`
   （cf 的 node fetch 不认环境变量代理，必须加 `NODE_OPTIONS=--use-env-proxy`；脚本内已自动设置）
2. 或在 `~/.config/poker-daemon/cf-token` 放一枚 Zone DNS Edit 权限的 API token（脚本优先用它，经本地代理调用 CF API）

两者都不可用时隧道仍正常工作（固定域名不受影响），只是 TXT 不维护。

### 回退 localhost.run 旧链路

若 CF 边缘在大陆不可达（如运营商策略变化），可整体回退旧的 lhr 隧道：

```bash
# 在 plist 的 EnvironmentVariables 里加 POKER_TUNNEL_MODE=lhr，然后：
launchctl kickstart -k gui/$(id -u)/com.aniapp.pokerdemo.server
```

新脚本会委托 `scripts/poker-daemon-lhr.sh`（旧版留档），自动重连 lhr 并把 TXT 改回轮换地址。恢复 CF 模式：删掉该环境变量再 kickstart。TXT TTL 120，切换约 2 分钟内对客户端生效。

## 方式一：互联网开房（一键脚本，临时局）

房主（需要一台保持开机的电脑，macOS/Linux）：

```bash
brew install cloudflared        # 可选；不装则直接走 localhost.run 回退
./scripts/host-online.sh        # 构建并启动服务器 + 隧道
```

脚本会打印形如 `wss://xxx.trycloudflare.com` 或 `wss://xxx.app.lhr.life` 的地址。把它发给朋友：

1. 朋友打开 app → 联机对战 → 服务器地址填 `wss://xxx.trycloudflare.com`
2. 房主创建房间，朋友用 4 位房间码加入
3. 房主点开始游戏（空位可选 AI 补位）

注意：
- **每次重启脚本地址都会变**，需重新分享
- 房主电脑休眠/关机或 Ctrl+C 后，全桌断开
- 桌面端同样可玩：`./gradlew :desktopApp:run`，填同一地址

### 中国大陆网络说明

- cloudflared 默认 QUIC/UDP **不走 HTTP_PROXY 环境变量**，直连 Cloudflare 常被阻断；但 `--protocol http2`（TCP）认 `HTTPS_PROXY`，方式零即用此法经本地代理连控制面，**无需 TUN**。临时 quick tunnel（trycloudflare）同样可加 `--protocol http2` + 代理尝试。
- 不开代理时脚本会自动回退 localhost.run（SSH 隧道）。若 SSH 直连不通，指定本地代理端口：

  ```bash
  POKER_PROXY=127.0.0.1:7897 ./scripts/host-online.sh
  ```

  （7897 换成你的代理客户端混合端口；原理是 `ssh ProxyCommand=nc -X connect -x …`）
- localhost.run 是第三方免费服务：域名随机（`xxx.app.lhr.life`）、无可用性保证，仅适合熟人临时开局。
- 加入方若也在墙内：手机需在 Wi-Fi 设置里把 HTTP 代理指向可出海的代理（如房主电脑的 Clash 混合端口，需开启「允许局域网连接」），或手机自带代理；桌面端可用 `proxychains4` 包裹启动。

## 方式二：固定地址（命名隧道，需 Cloudflare 账号 + 托管在 CF 的域名）

> 方式零已用此方案部署（remotely-managed + token，无需 `tunnel login` 浏览器授权）。以下本地凭据流程仅作备选参考。

适合长期固定开局，地址不随重启变化：

```bash
cloudflared tunnel login                 # 浏览器授权，选择域名
cloudflared tunnel create lstpoker       # 创建隧道，记下隧道 ID
cloudflared tunnel route dns lstpoker poker.你的域名.com
```

写 `~/.cloudflared/config.yml`：

```yaml
tunnel: <隧道ID>
credentials-file: /Users/你/.cloudflared/<隧道ID>.json
ingress:
  - hostname: poker.你的域名.com
    service: http://localhost:8080
  - service: http_status:404
```

运行：

```bash
./gradlew :server:run                    # 或 installDist 后跑 bin/server
cloudflared tunnel run lstpoker
```

玩家填 `wss://poker.你的域名.com`。可选：`cloudflared service install` 注册为系统服务常驻（此时服务器也需常驻）。

## 方式三：局域网（原有玩法，不变）

```bash
./gradlew :server:run
```

同一 Wi-Fi 下的玩家填 `192.168.x.x:8080`（房主本机局域网 IP）。

## 地址格式说明

客户端「服务器地址」输入框支持：

| 输入 | 含义 |
|---|---|
| （留空） | 公共服务器：DoH 读取 `_poker.lain-priv.work` TXT 自动获取地址，DoH 全失败兜底 `wss://poker.lain-priv.work` |
| `wss://poker.lain-priv.work` | 常驻公共服务器固定地址（方式零） |
| `wss://xxx.trycloudflare.com` | Cloudflare 临时隧道（默认端口 443） |
| `wss://xxx.app.lhr.life` | localhost.run 隧道（默认端口 443） |
| `ws://host:port` | 明文 WebSocket |
| `192.168.1.5:8080` 或 `192.168.1.5` | 局域网直连（默认端口 8080） |

## 已知限制

- 房主机器必须开机运行服务器；无云端托管（Cloudflare 免费层不支持 JVM）
- 方式零依赖本机代理进程连隧道控制面：代理挂则隧道断，launchd 自愈等代理恢复；玩家侧不受影响（走 CF 边缘）
- 回退 lhr 模式时免费域名会周期性轮换：旧脚本自动重连并把新地址发布到 DNS；客户端连接失败自动换 DoH 源重试
- 房间为内存态：服务器重启即丢房间，无断线重连（手牌中断线由 AI 托管打完本手）
- 4 位字母房间码在公网可被猜测，适合熟人局；勿用于涉赌场景
- Cloudflare 边缘会断开空闲 WebSocket，客户端/服务器已配置 15 秒 ping 保活