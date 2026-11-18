# LSTPoker 联机部署指南

服务器权威架构：引擎只在服务端运行，客户端只收到自己的底牌。
互联网对战通过公网隧道把本机服务器暴露为 `wss://` 地址（TLS 由隧道边缘终止，服务器本身无需改造）。

## 方式零：常驻公共服务器（本机已部署，推荐）

本机已通过 launchd 常驻运行「服务器 + localhost.run 隧道 + 地址发布」，开机自启、断线自愈：

- 服务：`com.aniapp.pokerdemo.server`（`~/Library/LaunchAgents/com.aniapp.pokerdemo.server.plist`）
- 脚本：`scripts/poker-daemon.sh`；状态目录 `~/.config/poker-daemon/`（`address` 为当前地址，`daemon.log`/`server.log`/`tunnel.log` 为日志）
- 自愈：SSH 断线 5 秒重连；另每 20 秒探测公网 `/ws`，连续两次 502 判定边缘静默失效并强制重连（lhr 免费边缘会在 SSH 仍连接时停止转发）
- 地址发布：隧道域名变化时自动写入 Cloudflare DNS TXT 记录 `_poker.lain-priv.work`（TTL 120）
- 玩家加入：app → 联机对战 → **服务器地址留空** → 创建房间或填房间码加入。客户端经阿里 DoH（`dns.alidns.com`，失败回退 `doh.pub`）读取 TXT 得到当前 `wss://` 地址，无需手动分享地址
- 防休眠：脚本附带 `caffeinate -s`（仅接电源时阻止系统休眠）；合盖/断电仍会中断，唤醒后自动重连并发布新地址

管理命令：

```bash
cp scripts/com.aniapp.pokerdemo.server.plist ~/Library/LaunchAgents/   # 首次安装
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.aniapp.pokerdemo.server.plist
launchctl kickstart -k gui/$(id -u)/com.aniapp.pokerdemo.server   # 重启服务
launchctl bootout gui/$(id -u)/com.aniapp.pokerdemo.server       # 停止并卸载
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.aniapp.pokerdemo.server.plist  # 重新加载
cat ~/.config/poker-daemon/address                               # 查看当前公网地址
```

DNS 发布凭据（二选一）：

1. cf CLI 已登录（OAuth，含 refresh token）。若过期重新授权：
   `HTTPS_PROXY=http://127.0.0.1:7897 NODE_OPTIONS=--use-env-proxy cf auth login`
   （cf 的 node fetch 不认环境变量代理，必须加 `NODE_OPTIONS=--use-env-proxy`；脚本内已自动设置）
2. 或在 `~/.config/poker-daemon/cf-token` 放一枚 Zone DNS Edit 权限的 API token（脚本优先用它，经本地代理调用 CF API）

两者都不可用时隧道仍正常工作，只是 DNS 不更新（地址见 `address` 文件，需手动分享）。

注意：localhost.run 免费域名轮换后，DNS 缓存最多约 2 分钟内仍指向旧域名；客户端连接失败会自动重试两次，仍失败则重进一次即可。

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

- cloudflared **不走系统代理/HTTP_PROXY 环境变量**，直连 Cloudflare 常被阻断。若要用 Cloudflare 隧道，需在代理客户端开启 **TUN/增强模式**。
- 不开 TUN 时脚本会自动回退 localhost.run（SSH 隧道）。若 SSH 直连不通，指定本地代理端口：

  ```bash
  POKER_PROXY=127.0.0.1:7897 ./scripts/host-online.sh
  ```

  （7897 换成你的代理客户端混合端口；原理是 `ssh ProxyCommand=nc -X connect -x …`）
- localhost.run 是第三方免费服务：域名随机（`xxx.app.lhr.life`）、无可用性保证，仅适合熟人临时开局。
- 加入方若也在墙内：手机需在 Wi-Fi 设置里把 HTTP 代理指向可出海的代理（如房主电脑的 Clash 混合端口，需开启「允许局域网连接」），或手机自带代理；桌面端可用 `proxychains4` 包裹启动。

## 方式二：固定地址（命名隧道，需 Cloudflare 账号 + 托管在 CF 的域名）

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
| （留空） | 公共服务器：DoH 读取 `_poker.lain-priv.work` TXT 自动获取地址 |
| `wss://xxx.trycloudflare.com` | Cloudflare 隧道（默认端口 443） |
| `wss://xxx.app.lhr.life` | localhost.run 隧道（默认端口 443） |
| `ws://host:port` | 明文 WebSocket |
| `192.168.1.5:8080` 或 `192.168.1.5` | 局域网直连（默认端口 8080） |

## 已知限制

- 房主机器必须开机运行服务器；无云端托管（Cloudflare 免费层不支持 JVM）
- localhost.run 免费域名会周期性轮换：常驻服务（方式零）会自动重连并把新地址发布到 DNS；手动脚本（方式一）需重新分享地址
- localhost.run 免费边缘偶发瞬时 502，客户端连接已带两次自动重试
- 房间为内存态：服务器重启即丢房间，无断线重连（手牌中断线由 AI 托管打完本手）
- 4 位字母房间码在公网可被猜测，适合熟人局；勿用于涉赌场景
- Cloudflare 边缘会断开空闲 WebSocket，客户端/服务器已配置 15 秒 ping 保活