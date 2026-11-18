#!/bin/bash
# LSTPoker 常驻服务：本机服务器 + Cloudflare 命名隧道（固定域名 poker.lain-priv.work）+ TXT 锚维护。
# 由 launchd (com.aniapp.pokerdemo.server) 拉起并保活；也可手动运行。
# 回退旧 localhost.run 链路：POKER_TUNNEL_MODE=lhr（委托 scripts/poker-daemon-lhr.sh）。
# 隧道凭据：~/.config/poker-daemon/tunnel-token（cloudflared remotely-managed tunnel token）。
# TXT 维护凭据：优先 ~/.config/poker-daemon/cf-token（Zone DNS Edit token），否则已登录的 cf CLI。
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ "${POKER_TUNNEL_MODE:-cf}" = "lhr" ]; then
    exec "$ROOT/scripts/poker-daemon-lhr.sh"
fi

STATE_DIR="$HOME/.config/poker-daemon"
mkdir -p "$STATE_DIR"
LOG="$STATE_DIR/daemon.log"
CF_ZONE_ID="a54b33481e9563a601f841df1688a5f0"
CF_RECORD_ID="1c916e4e3b6c7ab12cf54755ca5c16e9"
PUBLIC_HOST="poker.lain-priv.work"
WSS="wss://$PUBLIC_HOST"
PROXY_HOST="${POKER_PROXY:-127.0.0.1:7897}"
PROXY_URL="http://$PROXY_HOST"
CLOUDFLARED="${CLOUDFLARED_BIN:-/opt/homebrew/bin/cloudflared}"

log() { echo "[$(date '+%F %T')] $*" >> "$LOG"; }

if [ ! -f "$STATE_DIR/tunnel-token" ]; then
    log "ERROR: missing $STATE_DIR/tunnel-token"
    exit 1
fi

caffeinate -s -w $$ &

SERVER_PID=""
CFD_PID=""
cleanup() {
    [ -n "$CFD_PID" ] && kill "$CFD_PID" 2>/dev/null
    [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null
    log "daemon stopped"
}
trap cleanup EXIT INT TERM

proxy_env() {
    if nc -z 127.0.0.1 "${PROXY_HOST##*:}" 2>/dev/null; then
        export HTTPS_PROXY="$PROXY_URL" HTTP_PROXY="$PROXY_URL" NODE_OPTIONS="--use-env-proxy"
    else
        unset HTTPS_PROXY HTTP_PROXY NODE_OPTIONS
    fi
}

# 固定域名下 TXT 只需保持正确值（兼容旧 APK 的 DoH 发现、纠正回退残留），不再随隧道轮换发布
ensure_txt() {
    echo "$WSS" > "$STATE_DIR/address"
    proxy_env
    local body="{\"type\":\"TXT\",\"name\":\"_poker\",\"content\":\"\\\"$WSS\\\"\",\"ttl\":120}"
    if [ -f "$STATE_DIR/cf-token" ]; then
        local resp
        resp="$(curl -s --max-time 20 -x "$PROXY_URL" \
            -H "Authorization: Bearer $(cat "$STATE_DIR/cf-token")" \
            "https://api.cloudflare.com/client/v4/zones/$CF_ZONE_ID/dns_records/$CF_RECORD_ID")"
        if printf '%s' "$resp" | grep -qF '"content":"\"'"$WSS"'\""'; then
            return
        fi
        if curl -s --max-time 20 -x "$PROXY_URL" -X PATCH \
            -H "Authorization: Bearer $(cat "$STATE_DIR/cf-token")" \
            -H "Content-Type: application/json" \
            --data "$body" \
            "https://api.cloudflare.com/client/v4/zones/$CF_ZONE_ID/dns_records/$CF_RECORD_ID" \
            | grep -q '"success":true'; then
            log "TXT ensured -> $WSS (token)"
            return
        fi
        log "token ensure failed, trying cf CLI"
    fi
    if cf auth whoami 2>/dev/null | grep -q '"authenticated": true'; then
        if cf dns records update "$CF_RECORD_ID" -z "$CF_ZONE_ID" --body "$body" -q >> "$LOG" 2>&1; then
            log "TXT ensured -> $WSS (cf CLI)"
            return
        fi
        log "cf CLI ensure failed"
    fi
    log "TXT ensure skipped (no cf-token and cf not logged in)"
}

start_server() {
    if nc -z 127.0.0.1 8080 2>/dev/null; then
        log "server already listening on 8080"
        return
    fi
    if [ ! -x "$ROOT/server/build/install/server/bin/server" ]; then
        log "building server…"
        (cd "$ROOT" && ./gradlew :server:installDist -q) >> "$LOG" 2>&1
    fi
    "$ROOT/server/build/install/server/bin/server" >> "$STATE_DIR/server.log" 2>&1 &
    SERVER_PID=$!
    for _ in $(seq 1 30); do
        nc -z 127.0.0.1 8080 2>/dev/null && break
        sleep 1
    done
    if nc -z 127.0.0.1 8080 2>/dev/null; then
        log "server started (pid $SERVER_PID)"
    else
        log "ERROR: server failed to listen on 8080"
    fi
}

# argotunnel 控制面大陆直连被阻断，必须 http2（TCP）+ 进程级代理；QUIC/UDP 过不了 HTTP 代理
start_tunnel() {
    proxy_env
    : > "$STATE_DIR/cloudflared.log"
    "$CLOUDFLARED" tunnel --no-autoupdate --protocol http2 --edge-ip-version 4 \
        run --token "$(cat "$STATE_DIR/tunnel-token")" >> "$STATE_DIR/cloudflared.log" 2>&1 &
    CFD_PID=$!
    log "cloudflared started (pid $CFD_PID)"
}

probe_ok() {
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "https://$PUBLIC_HOST/ws" 2>/dev/null)"
    [ "$code" = "400" ] || [ "$code" = "426" ] || [ "$code" = "101" ]
}

start_server
ensure_txt
start_tunnel
BAD=0
TICK=0
while true; do
    # 上一实例的服务器可能稍后才退出，端口空闲后需补拉
    nc -z 127.0.0.1 8080 2>/dev/null || start_server
    if ! kill -0 "$CFD_PID" 2>/dev/null; then
        log "cloudflared exited, restarting in 5s"
        wait "$CFD_PID" 2>/dev/null
        sleep 5
        start_tunnel
        BAD=0
    fi
    sleep 5
    TICK=$((TICK + 1))
    if [ $((TICK % 4)) -eq 0 ]; then
        if probe_ok; then
            BAD=0
        else
            BAD=$((BAD + 1))
            log "public probe bad ($BAD/2)"
        fi
        if [ "$BAD" -ge 2 ]; then
            log "public path dead, restarting cloudflared"
            kill "$CFD_PID" 2>/dev/null
        fi
    fi
    if [ $((TICK % 60)) -eq 0 ]; then
        ensure_txt
    fi
done