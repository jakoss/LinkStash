#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_ROOT="$(cd "$SCRIPT_DIR/../../../src" && pwd)"

if [[ -z "${DB_URL:-}" ]]; then
  temp_db_file="$(mktemp /tmp/linkstash-e2e.XXXXXX.sqlite)"
  export DB_URL="jdbc:sqlite:$temp_db_file"
  trap 'rm -f "$temp_db_file"' EXIT
else
  export DB_URL
fi
export SESSION_SECRET="${SESSION_SECRET:-linkstash-e2e-session-secret}"
export TOKEN_HASHING_SECRET="${TOKEN_HASHING_SECRET:-linkstash-e2e-token-secret}"
export RAINDROP_TOKEN_ENCRYPTION_KEY="${RAINDROP_TOKEN_ENCRYPTION_KEY:-linkstash-e2e-encryption-secret}"
export LINKSTASH_ROOT_COLLECTION_TITLE="${LINKSTASH_ROOT_COLLECTION_TITLE:-LinkStash}"
export LINKSTASH_DEFAULT_SPACE_TITLE="${LINKSTASH_DEFAULT_SPACE_TITLE:-Inbox}"
export HOST="${HOST:-127.0.0.1}"
export PORT="${PORT:-8080}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-http://127.0.0.1:8081,http://localhost:8081}"

cd "$SRC_ROOT"
exec ./gradlew :server:run
