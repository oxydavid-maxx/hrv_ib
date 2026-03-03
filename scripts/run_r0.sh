#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SDK_PATH=""

if [[ -f "local.properties" ]]; then
  SDK_PATH_LINE="$(rg '^sdk\.dir=' local.properties || true)"
  if [[ -n "$SDK_PATH_LINE" ]]; then
    SDK_PATH="${SDK_PATH_LINE#sdk.dir=}"
  fi
fi

if [[ -z "$SDK_PATH" && -n "${ANDROID_SDK_ROOT:-}" ]]; then
  SDK_PATH="$ANDROID_SDK_ROOT"
fi
if [[ -z "$SDK_PATH" && -n "${ANDROID_HOME:-}" ]]; then
  SDK_PATH="$ANDROID_HOME"
fi

if [[ -z "$SDK_PATH" || ! -d "$SDK_PATH" ]]; then
  cat <<'EOF'
[R0 preflight] Android SDK not found.

To run R0 locally, configure one of:
1) local.properties in project root:
   sdk.dir=/absolute/path/to/Android/Sdk
2) env var:
   export ANDROID_SDK_ROOT=/absolute/path/to/Android/Sdk
   (or ANDROID_HOME)

If you do not want to install SDK locally, use GitHub Actions CI.
CI is the authoritative R0 gate in this repository.
EOF
  exit 2
fi

echo "[R0 preflight] Android SDK: $SDK_PATH"
chmod +x ./gradlew
./gradlew assembleDebug assembleRelease test lint connectedDebugAndroidTest --stacktrace
