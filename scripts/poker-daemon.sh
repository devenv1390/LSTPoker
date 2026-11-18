#!/bin/bash
# LSTPoker 常驻服务：本机服务器 + localhost.run 公网隧道 + 地址发布到 CF DNS TXT。
# 由 launchd (com.aniapp.pokerdemo.server) 拉起并保活；也可手动运行。
# 地址发布凭据：优先 ~/.config/poker-daemon/cf-token（Zone DNS Edit token），
# 否则用已登录的 cf CLI。两者都没有时只写本地 address 文件。
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STATE_DIR="$HOME/.config/poker-daemon"
mkdir -p "$STATE_DIR"
LOG="$STATE_DIR/daemon.log"
CF_ZONE_ID="a54b33481e9563a601f841df1688a5f0"
CF_RECORD_ID="1c916e4e3b6c7ab12cf54755ca5c16e9"
PROXY_HOST="${POKER_PROXY:-127.0.0.1:7897}"
PROXY_URL="http://$PROXY_HOST"

log() { echo "[$(date '+%F %T')] $*" >> "$LOG"; }

caffeinate -s -w $$ &

SERVER_PID=""
SSH_PID=""
cleanup() {
    [ -n "$SSH_PID" ] && kill "$SSH_PID" 2>/dev/null
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

publish() {
    local url="$1" body
    echo "$url" > "$STATE_DIR/address"
    body="{\"type\":\"TXT\",\"name\":\"_poker\",\"content\":\"\\\"$url\\\"\",\"ttl\":120}"
    proxy_env
    if [ -f "$STATE_DIR/cf-token" ]; then
        if curl -s --max-time 20 -x "$PROXY_URL" -X PATCH \
            -H "Authorization: Bearer $(cat "$STATE_DIR/cf-token")" \
            -H "Content-Type: application/json" \
            --data "$body" \
            "https://api.cloudflare.com/client/v4/zones/$CF_ZONE_ID/dns_records/$CF_RECORD_ID" \
            | grep -q '"success":true'; then
            log "published $url (token)"
            return
        fi
        log "token publish failed, trying cf CLI"
    fi
    if cf auth whoami 2>/dev/null | grep -q '"authenticated": true'; then
        if cf dns records update "$CF_RECORD_ID" -z "$CF_ZONE_ID" --body "$body" -q >> "$LOG" 2>&1; then
            log "published $url (cf CLI)"
            return
        fi
        log "cf CLI publish failed"
    fi
    log "published $url (local only; DNS not updated: no cf-token and cf not logged in)"
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

strip_ansi() { sed 's/\x1b\[[0-9;]*m//g'; }

# lhr 边缘可能静默失效（SSH 仍连着但公网 502），靠探测发现并强制重连
probe_ok() {
    local code
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$1/ws" 2>/dev/null)"
    [ "$code" = "400" ] || [ "$code" = "426" ] || [ "$code" = "101" ]
}

start_server
LAST_URL=""
while true; do
    # 上一实例的服务器可能稍后才退出，端口空闲后需补拉
    nc -z 127.0.0.1 8080 2>/dev/null || start_server
    TUNNEL_LOG="$STATE_DIR/tunnel.log"
    : > "$TUNNEL_LOG"
    ssh -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -o ServerAliveInterval=30 \
        -o ExitOnForwardFailure=yes -R 80:localhost:8080 nokey@localhost.run >> "$TUNNEL_LOG" 2>&1 &
    SSH_PID=$!
    URL=""
    for _ in $(seq 1 40); do
        URL="$(strip_ansi < "$TUNNEL_LOG" | grep -Eo 'https://[a-z0-9.]+lhr\.life' | tail -1)"
        [ -n "$URL" ] && break
        kill -0 "$SSH_PID" 2>/dev/null || break
        sleep 1
    done
    if [ -n "$URL" ]; then
        WSS="${URL/https:/wss:}"
        log "tunnel up: $WSS"
        if [ "$WSS" != "$LAST_URL" ]; then
            publish "$WSS"
            LAST_URL="$WSS"
        fi
        BAD=0
        TICK=0
        while kill -0 "$SSH_PID" 2>/dev/null; do
            sleep 5
            TICK=$((TICK + 1))
            if [ $((TICK % 4)) -eq 0 ]; then
                if probe_ok "$URL"; then
                    BAD=0
                else
                    BAD=$((BAD + 1))
                    log "public probe bad ($BAD/2)"
                fi
                if [ "$BAD" -ge 2 ]; then
                    log "edge silent-dead, forcing tunnel reconnect"
                    kill "$SSH_PID" 2>/dev/null
                    break
                fi
            fi
        done
        wait "$SSH_PID" 2>/dev/null
        log "tunnel exited, restarting in 5s"
    else
        log "ERROR: no tunnel URL within 40s"
        kill "$SSH_PID" 2>/dev/null
        wait "$SSH_PID" 2>/dev/null
    fi
    SSH_PID=""
    sleep 5
done