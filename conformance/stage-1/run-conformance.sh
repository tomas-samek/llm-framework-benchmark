#!/usr/bin/env bash
# Grade one Stage-1 trial (portable single-trial variant of grade-batch.ps1).
# Usage: BENCH_MODEL=claude-opus-4-8 ./run-conformance.sh <framework> <trial> "<app-start-cmd>" [trial-dir]
# Brings up a fresh broker (down -v), pre-creates the topics, starts the app in the
# background, grades, then stops the app's whole process group and tears the broker down.
# BENCH_FIXTURES=v2 grades with scenarios.v2.json -> results/*.v2.json + results/metrics-v2.csv
# (default v1: scenarios.json -> results/metrics.csv; see fixtures/stage-1/README.md).
set -euo pipefail

FRAMEWORK="$1"; TRIAL="$2"; APP_START_CMD="$3"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TRIAL_DIR="${4:-$ROOT/runs/stage-1/$FRAMEWORK/trial-$TRIAL}"
case "${BENCH_FIXTURES:-v1}" in
  v1) SUFFIX=""; METRICS="$ROOT/results/metrics.csv" ;;
  v2) SUFFIX=".v2"; METRICS="$ROOT/results/metrics-v2.csv" ;;
  *) echo "BENCH_FIXTURES must be v1 or v2"; exit 2 ;;
esac
SCENARIOS="$ROOT/fixtures/stage-1/scenarios$SUFFIX.json"
ORACLE_JAR="$ROOT/conformance/oracle/target/conformance-oracle.jar"
OUT_JSON="$ROOT/results/stage-1-$FRAMEWORK-$TRIAL$SUFFIX.json"
COMPOSE="$ROOT/conformance/docker-compose.yml"
MODEL="${BENCH_MODEL:-unknown}"

[ -f "$ORACLE_JAR" ] || { echo "Oracle jar missing at $ORACLE_JAR - build it first."; exit 2; }
[ -d "$TRIAL_DIR" ]  || { echo "Trial dir missing: $TRIAL_DIR"; exit 2; }

# Fresh broker per trial: apache/kafka keeps topic data in an anonymous volume, so -v is mandatory.
echo "Bringing up a fresh broker..."
docker compose -f "$COMPOSE" down -v >/dev/null 2>&1 || true
docker compose -f "$COMPOSE" up -d
sleep 10
for T in product-updates user-updates price-updates purchases notifications; do
  docker exec bench-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
    --create --if-not-exists --topic "$T" --partitions 1 --replication-factor 1 >/dev/null
done

APP_PID=""
cleanup() {
  # Kill the app's whole process group (shell -> mvn -> java): a surviving JVM would
  # reconnect to the next trial's broker and contaminate its topics.
  [ -n "$APP_PID" ] && kill -- "-$APP_PID" 2>/dev/null || true
  docker compose -f "$COMPOSE" down -v >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "Starting contestant app in $TRIAL_DIR ..."
STARTED_AT=$(date +%s)
set -m  # give the background job its own process group so cleanup can kill the tree
( cd "$TRIAL_DIR" && eval "$APP_START_CMD" ) >"$TRIAL_DIR/app.log" 2>"$TRIAL_DIR/app.err.log" &
APP_PID=$!
set +m
sleep 40  # let the app boot and its consumers join before publishing

echo "Running oracle..."
set +e
java -jar "$ORACLE_JAR" "localhost:9092" "$SCENARIOS" "$OUT_JSON"
CODE=$?
set -e

ELAPSED=$(( $(date +%s) - STARTED_AT ))
COMPLIANCE=0
if [ -f "$OUT_JSON" ]; then
  FRACTION=$(grep -o '"compliance"[^,]*' "$OUT_JSON" | head -1 | grep -o '[0-9.]*' || echo 0)
  COMPLIANCE=$(awk -v f="$FRACTION" 'BEGIN { printf "%d", f * 100 + 0.5 }')
fi
# metrics row: timestamp,model,framework,stage,trial,compliance,wall_clock_seconds,output_tokens,agent_turns,tool_calls,cost_usd
# Token/turn/cost columns are left blank; backfill them with conformance/token-accounting.py.
echo "$(date -u +%FT%TZ),$MODEL,$FRAMEWORK,stage-1,$TRIAL,$COMPLIANCE,$ELAPSED,,,," >> "$METRICS"
echo "Compliance=${COMPLIANCE}% elapsed=${ELAPSED}s (oracle exit $CODE)"
exit $CODE
