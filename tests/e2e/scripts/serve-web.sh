#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_ROOT="$(cd "$SCRIPT_DIR/../../../src" && pwd)"
OUTPUT_DIR="${OUTPUT_DIR:-/tmp/linkstash-e2e-web}"
HOST="${HOST:-127.0.0.1}"
PORT="${PORT:-8081}"

cd "$SRC_ROOT"
./gradlew :webApp:wasmJsBrowserDevelopmentWebpack

install -d "$OUTPUT_DIR"
cp webApp/build/processedResources/wasmJs/main/index.html "$OUTPUT_DIR"/
cp webApp/build/kotlin-webpack/wasmJs/developmentExecutable/* "$OUTPUT_DIR"/

cd "$OUTPUT_DIR"
exec python3 -m http.server "$PORT" --bind "$HOST"
