#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_ROOT="$(cd "$SCRIPT_DIR/../../../src" && pwd)"
OUTPUT_DIR="${OUTPUT_DIR:-/tmp/linkstash-e2e-web}"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-8081}"

cd "$SRC_ROOT"
./gradlew :webApp:jsBrowserDevelopmentWebpack

install -d "$OUTPUT_DIR"
cp webApp/build/processedResources/js/main/index.html \
  webApp/build/kotlin-webpack/js/developmentExecutable/linkstash-web.js \
  webApp/build/kotlin-webpack/js/developmentExecutable/node_modules_ws_browser_js.js \
  "$OUTPUT_DIR"/

cd "$OUTPUT_DIR"
exec python3 -m http.server "$PORT" --bind "$HOST"
