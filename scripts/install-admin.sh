#!/bin/bash
# 安装/更新 LSTPoker 本地管理面板（http://127.0.0.1:8787，仅本机可访问）。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LABEL=com.aniapp.pokerdemo.admin
PLIST="$HOME/Library/LaunchAgents/$LABEL.plist"
UID_NUM="$(id -u)"

cd "$ROOT"
./gradlew :admin:installDist -q
cp "scripts/$LABEL.plist" "$PLIST"

if launchctl print "gui/$UID_NUM/$LABEL" >/dev/null 2>&1; then
    launchctl bootout "gui/$UID_NUM/$LABEL" || true
    sleep 1
fi
launchctl bootstrap "gui/$UID_NUM" "$PLIST"

for _ in $(seq 1 30); do
    if curl -s -o /dev/null --max-time 2 http://127.0.0.1:8787/; then break; fi
    sleep 1
done
curl -s -o /dev/null -w "admin http:%{http_code}\n" http://127.0.0.1:8787/
echo "管理面板: http://127.0.0.1:8787"