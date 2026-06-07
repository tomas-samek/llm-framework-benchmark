#!/usr/bin/env bash
# Grade one Stage-1 trial.
# Usage: ./run-conformance.sh <framework> <trial> "<app-start-cmd>" [trial-dir]
set -euo pipefail

FRAMEWORK="$1"; TRIAL="$2"; APP_START_CMD="$3"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TRIAL_DIR="${4:-$ROOT/runs/stage-1/$FRAMEWORK/trial-$TRIAL}"
SCENARIOS="$ROOT/fixtures/stage-1/scenarios.json"
ORACLE_JAR="$ROOT/conformance/oracle/target/conformance-oracle.jar"
OUT_JSON="$ROOT/results/stage-1-$FRAMEWORK-$TRIAL.json"
COMPOSE="$ROOT/conformance/docker-compose.yml"

echo "Bringing up broker..."
docker compose -f "$COMPOSE" up -d
sleep 8

echo "Starting contestant app in $TRIAL_DIR ..."
STARTED_AT=$(date +%s)
( cd "$TRIAL_DIR" && eval "$APP_START_CMD" ) &
APP_PID=$!
sleep 20

cleanup() {
  kill "$APP_PID" 2>/dev/null || true
  docker compose -f "$COMPOSE" down
}
trap cleanup EXIT

echo "Running oracle..."
set +e
java -jar "$ORACLE_JAR" "localhost:9092" "$SCENARIOS" "$OUT_JSON"
CODE=$?
set -e

ELAPSED=$(( $(date +%s) - STARTED_AT ))
COMPLIANCE=$(grep -o '"compliance"[^,]*' "$OUT_JSON" | head -1 | grep -o '[0-9.]*')
echo "$(date +%FT%T),$FRAMEWORK,stage-1,$TRIAL,$COMPLIANCE,$ELAPSED,,," >> "$ROOT/results/metrics.csv"
echo "Compliance=$COMPLIANCE elapsed=${ELAPSED}s (oracle exit $CODE)"
exit $CODE
