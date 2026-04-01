#!/usr/bin/env sh
# =============================================================================
# pull-model.sh
# Pulls the llama3.1:8b model into Ollama on first container start.
#
# The script is idempotent: it checks whether the model is already present
# and skips the download if so.  This avoids re-downloading several gigabytes
# on every container restart.
#
# Usage (called automatically by docker-compose entrypoint):
#   sh /pull-model.sh
# =============================================================================

set -e

OLLAMA_HOST="${OLLAMA_HOST:-http://localhost:11434}"
MODEL="${OLLAMA_MODEL:-llama3.1:8b}"
MAX_WAIT=120   # seconds to wait for the Ollama server to become ready
WAIT_INTERVAL=3

# ── Wait for Ollama to be ready ──────────────────────────────────────────────
echo "[pull-model] Waiting for Ollama server at ${OLLAMA_HOST} …"
elapsed=0
until curl -sf "${OLLAMA_HOST}/api/tags" > /dev/null 2>&1; do
    if [ "${elapsed}" -ge "${MAX_WAIT}" ]; then
        echo "[pull-model] ERROR: Ollama did not become ready within ${MAX_WAIT}s. Aborting." >&2
        exit 1
    fi
    sleep "${WAIT_INTERVAL}"
    elapsed=$((elapsed + WAIT_INTERVAL))
done
echo "[pull-model] Ollama is ready."

# ── Check whether the model is already present ──────────────────────────────
already_pulled() {
    curl -sf "${OLLAMA_HOST}/api/tags" \
        | grep -q "\"${MODEL}\""
}

if already_pulled; then
    echo "[pull-model] Model '${MODEL}' is already present. Skipping download."
    exit 0
fi

# ── Pull the model ───────────────────────────────────────────────────────────
echo "[pull-model] Pulling model '${MODEL}' …"
RESPONSE=$(curl -sf -X POST "${OLLAMA_HOST}/api/pull" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${MODEL}\", \"stream\": false}" \
    -w "\n%{http_code}" \
    --max-time 600)   # 10-minute timeout for large models

HTTP_STATUS=$(printf '%s' "${RESPONSE}" | tail -n1)
BODY=$(printf '%s' "${RESPONSE}" | head -n -1)

if [ "${HTTP_STATUS}" -ne 200 ]; then
    echo "[pull-model] ERROR: Pull request returned HTTP ${HTTP_STATUS}." >&2
    echo "[pull-model] Response body: ${BODY}" >&2
    exit 1
fi

# ── Verify the pull succeeded ────────────────────────────────────────────────
if already_pulled; then
    echo "[pull-model] Model '${MODEL}' successfully pulled."
else
    echo "[pull-model] WARNING: Pull completed but model not found in tag list." >&2
    echo "[pull-model] Response body: ${BODY}" >&2
    exit 1
fi
