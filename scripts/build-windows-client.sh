#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION="${VERSION:-1.0.0}"
JRE_URL="${JRE_URL:-https://cdn.azul.com/zulu/bin/zulu17.70.201-ca-jre17.0.21.1-win_x64.zip}"
SEVEN_ZIP_URL="${SEVEN_ZIP_URL:-https://downloads.sourceforge.net/sevenzip/7z2301-x64.exe}"
CACHE_DIR="${CACHE_DIR:-$ROOT/build/windows-client-cache}"
OUT_DIR="${OUT_DIR:-$ROOT/build/windows-client}"
BASE_NAME="LSTPoker-$VERSION-windows-x64"
STAGING_DIR="$OUT_DIR/$BASE_NAME"

for tool in curl unzip zip 7z jar shasum; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        printf 'Missing required tool: %s\n' "$tool" >&2
        exit 1
    fi
done

mkdir -p "$CACHE_DIR" "$OUT_DIR"

printf 'Building Windows uber JAR...\n'
./gradlew :desktopApp:packageUberJarForCurrentOS -PdesktopPlatform=windows-x64
JAR_PATH="$(find "$ROOT/desktopApp/build/compose/jars" -maxdepth 1 -type f -name "LSTPoker-*-$VERSION.jar" -print -quit)"
if [[ -z "$JAR_PATH" ]]; then
    printf 'Unable to locate built LSTPoker JAR.\n' >&2
    exit 1
fi
if ! jar tf "$JAR_PATH" | grep -q '^skiko-windows-x64.dll$'; then
    printf 'Built JAR does not contain the Windows x64 Skiko runtime.\n' >&2
    exit 1
fi
if jar tf "$JAR_PATH" | grep -q '\.dylib$'; then
    printf 'Built JAR unexpectedly contains macOS native libraries.\n' >&2
    exit 1
fi

JRE_ZIP="$CACHE_DIR/zulu17-win-x64-jre.zip"
if [[ ! -s "$JRE_ZIP" ]]; then
    printf 'Downloading Windows x64 JRE...\n'
    curl --http1.1 -L --fail --retry 3 -o "$JRE_ZIP" "$JRE_URL"
fi
unzip -tq "$JRE_ZIP" >/dev/null

SEVEN_ZIP_INSTALLER="$CACHE_DIR/7z2301-x64.exe"
if [[ ! -s "$SEVEN_ZIP_INSTALLER" ]]; then
    printf 'Downloading 7-Zip SFX module source...\n'
    curl --http1.1 -L --fail --retry 3 -o "$SEVEN_ZIP_INSTALLER" "$SEVEN_ZIP_URL"
fi

rm -rf "$STAGING_DIR" "$CACHE_DIR/jre-extracted" "$CACHE_DIR/7z-extracted"
mkdir -p "$STAGING_DIR/jre" "$CACHE_DIR/jre-extracted" "$CACHE_DIR/7z-extracted"

printf 'Assembling portable client...\n'
cp "$JAR_PATH" "$STAGING_DIR/LSTPoker.jar"
unzip -q "$JRE_ZIP" -d "$CACHE_DIR/jre-extracted"
JRE_ROOT="$(find "$CACHE_DIR/jre-extracted" -mindepth 1 -maxdepth 1 -type d -print -quit)"
if [[ -z "$JRE_ROOT" || ! -f "$JRE_ROOT/bin/javaw.exe" ]]; then
    printf 'Windows JRE does not contain bin/javaw.exe.\n' >&2
    exit 1
fi
cp -R "$JRE_ROOT/." "$STAGING_DIR/jre/"

cat > "$STAGING_DIR/LSTPoker.cmd" <<'EOF'
@echo off
setlocal
cd /d "%~dp0"
start "" "%~dp0jre\bin\javaw.exe" -jar "%~dp0LSTPoker.jar"
EOF

printf 'Creating ZIP distribution...\n'
rm -f "$OUT_DIR/$BASE_NAME.zip"
(
    cd "$OUT_DIR"
    zip -q -r "$BASE_NAME.zip" "$BASE_NAME"
)

printf 'Creating self-extracting EXE...\n'
7z x -y -o"$CACHE_DIR/7z-extracted" "$SEVEN_ZIP_INSTALLER" >/dev/null
SFX_MODULE="$CACHE_DIR/7z-extracted/7z.sfx"
if [[ ! -f "$SFX_MODULE" ]]; then
    printf '7z.sfx module was not found in the 7-Zip installer.\n' >&2
    exit 1
fi

cat > "$CACHE_DIR/sfx-config.txt" <<'EOF'
;!@Install@!UTF-8!
Title="LSTPoker"
BeginPrompt="Extract and run LSTPoker?"
RunProgram="jre\\bin\\javaw.exe -jar LSTPoker.jar"
;!@InstallEnd@!
EOF

rm -f "$CACHE_DIR/payload.7z" "$OUT_DIR/$BASE_NAME.exe"
(
    cd "$OUT_DIR"
    7z a -t7z -mx=7 "$CACHE_DIR/payload.7z" "$BASE_NAME" >/dev/null
)
cat "$SFX_MODULE" "$CACHE_DIR/sfx-config.txt" "$CACHE_DIR/payload.7z" > "$OUT_DIR/$BASE_NAME.exe"
chmod +x "$OUT_DIR/$BASE_NAME.exe"

printf 'Verifying generated archives...\n'
7z t "$OUT_DIR/$BASE_NAME.exe" >/dev/null
unzip -tq "$OUT_DIR/$BASE_NAME.zip" >/dev/null

(
    cd "$OUT_DIR"
    shasum -a 256 "$BASE_NAME.exe" "$BASE_NAME.zip" > "$BASE_NAME.sha256"
)

printf '\nCreated:\n'
ls -lh "$OUT_DIR/$BASE_NAME.exe" "$OUT_DIR/$BASE_NAME.zip" "$OUT_DIR/$BASE_NAME.sha256"
