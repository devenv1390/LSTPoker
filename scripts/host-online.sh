#!/usr/bin/env bash
# 一键互联网开房：本机扑克服务器 + 公网隧道
# 首选 Cloudflare 快速隧道；失败自动回退 localhost.run（SSH 隧道）
# 中国大陆网络若走本地代理：POKER_PROXY=127.0.0.1:7897 ./scripts/host-online.sh
# （cloudflared 不走系统代理，需代理客户端开 TUN 模式；localhost.run 回退支持 POKER_PROXY）
set -euo pipefail
cd "$(dirname "$0")/.."

SERVER_PID=""
TUNNEL_PID=""
cleanup() {
  [ -n "$TUNNEL_PID" ] && kill "$TUNNEL_PID" 2>/dev/null || true
  [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "==> 构建并启动扑克服务器 (localhost:8080)…"
./gradlew :server:installDist -q
server/build/install/server/bin/server &
SERVER_PID=$!

for _ in $(seq 1 30); do
  nc -z localhost 8080 2>/dev/null && break
  sleep 1
done
if ! nc -z localhost 8080 2>/dev/null; then
  echo "服务器 30 秒内未就绪" >&2
  exit 1
fi

LOG=$(mktemp)
URL=""

start_cloudflared() {
  command -v cloudflared >/dev/null 2>&1 || return 1
  echo "==> 尝试 Cloudflare 快速隧道…"
  local attempt
  for attempt in 1 2; do
    cloudflared tunnel --url http://localhost:8080 --no-autoupdate >"$LOG" 2>&1 &
    TUNNEL_PID=$!
    local i
    for i in $(seq 1 25); do
      # 必须等到边缘连接真正注册成功，仅有 URL 不代表隧道可用
      if grep -q "Registered tunnel connection" "$LOG" 2>/dev/null; then
        URL=$(grep -Eo 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOG" | grep -v '^https://api\.' | head -1 || true)
        [ -n "$URL" ] && return 0
      fi
      kill -0 "$TUNNEL_PID" 2>/dev/null || break
      sleep 1
    done
    kill "$TUNNEL_PID" 2>/dev/null || true
    TUNNEL_PID=""
    echo "Cloudflare 隧道失败（第 $attempt 次）：$(tail -1 "$LOG" | cut -c1-160)" >&2
  done
  return 1
}

start_localhost_run() {
  echo "==> 回退 localhost.run SSH 隧道…"
  local proxy_opts=()
  if [ -n "${POKER_PROXY:-}" ]; then
    proxy_opts=(-o "ProxyCommand=nc -X connect -x $POKER_PROXY %h %p")
    echo "    经代理 $POKER_PROXY"
  fi
  ssh -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -o ServerAliveInterval=30 \
    "${proxy_opts[@]}" -R 80:localhost:8080 nokey@localhost.run >"$LOG" 2>&1 &
  TUNNEL_PID=$!
  local i
  for i in $(seq 1 30); do
    URL=$(sed 's/\x1b\[[0-9;]*m//g' "$LOG" | grep -Eo 'https://[a-z0-9.]+lhr\.life' | head -1 || true)
    [ -n "$URL" ] && return 0
    kill -0 "$TUNNEL_PID" 2>/dev/null || break
    sleep 1
  done
  kill "$TUNNEL_PID" 2>/dev/null || true
  TUNNEL_PID=""
  return 1
}

if ! start_cloudflared && ! start_localhost_run; then
  echo "无法建立隧道。中国大陆网络请设置 POKER_PROXY=代理地址:端口 重试，" >&2
  echo "或在代理客户端开启 TUN 模式（cloudflared 需要）。日志: $LOG" >&2
  exit 1
fi

echo ""
echo "======================================================"
echo " 互联网开房就绪！"
echo " 服务器地址: ${URL/https:/wss:}"
echo " 朋友在 app「联机对战」填入该地址，即可创建/加入房间"
echo " 注意: 每次重启本脚本地址都会变化；保持本窗口运行"
echo " 按 Ctrl+C 结束开房"
echo "======================================================"
wait "$TUNNEL_PID"