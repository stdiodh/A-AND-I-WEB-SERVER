#!/usr/bin/env sh
set -eu

MODE="${1:-smoke}"
ENV_FILE="${2:-}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
K6_VERSION_FILE="$ROOT_DIR/performance/k6/K6_VERSION"

expected_k6_version() {
  if [ ! -f "$K6_VERSION_FILE" ]; then
    echo "Pinned k6 version file not found: $K6_VERSION_FILE" >&2
    exit 2
  fi
  tr -d '[:space:]' < "$K6_VERSION_FILE"
}

K6_PINNED_VERSION="$(expected_k6_version)"
K6_IMAGE="${K6_IMAGE:-grafana/k6:${K6_PINNED_VERSION}}"

cpu_info() {
  if command -v sysctl >/dev/null 2>&1; then
    sysctl -n machdep.cpu.brand_string 2>/dev/null && return
  fi
  if command -v nproc >/dev/null 2>&1; then
    echo "$(nproc) CPUs" && return
  fi
  echo "unknown"
}

memory_info() {
  if command -v sysctl >/dev/null 2>&1; then
    bytes="$(sysctl -n hw.memsize 2>/dev/null || true)"
    if [ -n "$bytes" ]; then
      awk "BEGIN { printf \"%.2f GiB\", $bytes / 1024 / 1024 / 1024 }" && return
    fi
  fi
  if command -v free >/dev/null 2>&1; then
    free -h | awk '/Mem:/ { print $2 }' && return
  fi
  echo "unknown"
}

script_path_for_mode() {
  case "$1" in
    preflight|preflight.js)
      echo "performance/k6/preflight.js"
      ;;
    smoke|smoke.js)
      echo "performance/k6/smoke.js"
      ;;
    warmup|warmup.js)
      echo "performance/k6/warmup.js"
      ;;
    assignment-read|assignment-read.js)
      echo "performance/k6/assignment-read.js"
      ;;
    submission-status-read|submission-status-read.js)
      echo "performance/k6/submission-status-read.js"
      ;;
    *)
      return 1
      ;;
  esac
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "$1 is required." >&2
    exit 127
  fi
}

extract_k6_version() {
  printf '%s\n' "$1" | sed -n 's/.*v\([0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\).*/\1/p' | head -n 1
}

ensure_k6_image_version() {
  expected="$K6_PINNED_VERSION"
  actual_line="$(docker run --rm "$K6_IMAGE" version 2>/dev/null | head -n 1 || true)"
  actual="$(extract_k6_version "$actual_line")"
  if [ -z "$actual" ] || [ "$actual" != "$expected" ]; then
    echo "Expected k6 version: $expected" >&2
    echo "Actual k6 version: ${actual_line:-unknown}" >&2
    echo "Use grafana/k6:${expected} or another image with the pinned official release." >&2
    if [ "${ALLOW_K6_VERSION_MISMATCH:-false}" != "true" ]; then
      exit 2
    fi
    echo "ALLOW_K6_VERSION_MISMATCH=true is set; this run must not be used as final baseline evidence." >&2
  fi
  K6_VERSION="$actual_line"
  export K6_VERSION
}

load_env_file() {
  if [ -z "$ENV_FILE" ]; then
    return
  fi
  case "$ENV_FILE" in
    /*) resolved="$ENV_FILE" ;;
    *) resolved="$ROOT_DIR/$ENV_FILE" ;;
  esac
  if [ ! -f "$resolved" ]; then
    echo "Environment file not found: $resolved" >&2
    exit 2
  fi
  set -a
  # shellcheck disable=SC1090
  . "$resolved"
  set +a
}

git_dirty() {
  if [ -n "$(git status --porcelain 2>/dev/null || true)" ]; then
    echo "true"
  else
    echo "false"
  fi
}

wait_readiness() {
  if ! command -v curl >/dev/null 2>&1; then
    echo "curl is not available; readiness will be checked by k6 preflight." >&2
    return
  fi
  health_url="${READINESS_URL:-${BASE_URL%/}/actuator/health/readiness}"
  attempts="${READINESS_WAIT_ATTEMPTS:-30}"
  i=1
  while [ "$i" -le "$attempts" ]; do
    if curl -fsS "$health_url" >/dev/null 2>&1; then
      return
    fi
    sleep 1
    i=$((i + 1))
  done
  echo "Readiness did not become available at ${health_url}." >&2
  exit 1
}

docker_network_default() {
  if [ "$(uname -s)" = "Darwin" ]; then
    echo ""
  else
    echo "host"
  fi
}

run_k6() {
  scenario="$1"
  script_path="$(script_path_for_mode "$scenario")"
  export PREFLIGHT_INCLUDE_ADMIN WARMUP_INCLUDE_ADMIN WARMUP_COMPLETED

  set -- docker run --rm
  if [ -n "${K6_DOCKER_NETWORK:-}" ]; then
    set -- "$@" --network "$K6_DOCKER_NETWORK"
  fi

  "$@" \
    -v "$ROOT_DIR:/work" \
    -w /work \
    -e BASE_URL \
    -e ACCESS_TOKEN \
    -e ADMIN_ACCESS_TOKEN \
    -e COURSE_SLUG \
    -e ASSIGNMENT_ID \
    -e ALLOW_REMOTE_LOAD_TEST \
    -e REMOTE_TARGET_ALLOWLIST \
    -e LOAD_MODEL \
    -e TARGET_RPS \
    -e PRE_ALLOCATED_VUS \
    -e MAX_VUS \
    -e SMOKE_VUS \
    -e LOAD_VUS \
    -e TEST_DURATION \
    -e P95_THRESHOLD_MS \
    -e RESULT_DIR \
    -e ASSIGNMENT_LIST_RATIO \
    -e ASSIGNMENT_DETAIL_RATIO \
    -e REQUEST_SLEEP_SECONDS \
    -e DEVICE_OS \
    -e REQUEST_TIMEOUT \
    -e TARGET_ENVIRONMENT \
    -e DOCKER_LOCAL_MODE \
    -e ALLOW_SCENARIO_SKIP \
    -e PREFLIGHT_INCLUDE_ADMIN \
    -e WARMUP_INCLUDE_ADMIN \
    -e WARMUP_ITERATIONS \
    -e WARMUP_MAX_DURATION \
    -e WARMUP_COMPLETED \
    -e PRIVATE_TESTCASE_MARKER \
    -e PRIVATE_TESTCASE_SEQ \
    -e JVM_OPTS \
    -e JAVA_TOOL_OPTIONS \
    -e K6_VERSION \
    -e RUN_LABEL \
    -e ALLOW_K6_VERSION_MISMATCH \
    -e GIT_COMMIT_SHA \
    -e GIT_DIRTY \
    -e CPU_INFO \
    -e MEMORY_INFO \
    -e MONGODB_MODE \
    -e K6_DOCKER_NETWORK_MODE \
    -e FIXTURE_COURSES \
    -e FIXTURE_ASSIGNMENTS \
    -e FIXTURE_ENROLLMENTS \
    -e FIXTURE_SUBMISSION_STATUSES \
    -e FIXTURE_FINGERPRINT \
    "$K6_IMAGE" run "$script_path"
}

run_preflight() {
  if [ "${SKIP_PREFLIGHT:-false}" = "true" ]; then
    echo "Preflight skipped because SKIP_PREFLIGHT=true." >&2
    return
  fi
  PREFLIGHT_INCLUDE_ADMIN="${1:-true}"
  export PREFLIGHT_INCLUDE_ADMIN
  run_k6 preflight
}

run_warmup() {
  WARMUP_INCLUDE_ADMIN="${1:-false}"
  export WARMUP_INCLUDE_ADMIN
  run_k6 warmup
}

verify_summary() {
  scenario="$1"
  allow_skip="$2"
  latest="$(ls -t "$RESULT_DIR"/"$scenario"-*.summary.json 2>/dev/null | head -n 1 || true)"
  if [ -z "$latest" ]; then
    echo "Summary JSON not found for scenario: $scenario" >&2
    exit 1
  fi
  python3 - "$latest" "$allow_skip" <<'PY'
import json
import sys

path, allow_skip = sys.argv[1], sys.argv[2] == "true"
with open(path, "r", encoding="utf-8") as f:
    payload = json.load(f)
context = payload.get("context", {})
if context.get("skipped") and not allow_skip:
    print(f"Scenario skipped unexpectedly: {path} ({context.get('skipReason')})", file=sys.stderr)
    sys.exit(1)
if not context.get("executed") and not context.get("skipped"):
    print(f"Scenario did not execute business requests: {path}", file=sys.stderr)
    sys.exit(1)
if context.get("thresholdFailed"):
    print(f"Scenario threshold or zero-request guard failed: {path}", file=sys.stderr)
    sys.exit(1)
if not context.get("skipped") and int(context.get("businessRequestCount", 0)) <= 0:
    print(f"Scenario recorded zero business requests: {path}", file=sys.stderr)
    sys.exit(1)
PY
}

cd "$ROOT_DIR"
load_env_file
require_command docker
ensure_k6_image_version

if [ "$(uname -s)" = "Darwin" ]; then
  : "${BASE_URL:=http://host.docker.internal:8080}"
  : "${DOCKER_LOCAL_MODE:=true}"
else
  : "${BASE_URL:=http://localhost:8080}"
  : "${DOCKER_LOCAL_MODE:=false}"
fi
: "${K6_DOCKER_NETWORK:=$(docker_network_default)}"
: "${RESULT_DIR:=performance/results}"
: "${MONGODB_MODE:=local-docker-compose}"
: "${TARGET_ENVIRONMENT:=local}"

if [ -n "$K6_DOCKER_NETWORK" ]; then
  network_label="$K6_DOCKER_NETWORK"
else
  network_label="default"
fi

export BASE_URL DOCKER_LOCAL_MODE K6_DOCKER_NETWORK RESULT_DIR MONGODB_MODE TARGET_ENVIRONMENT
export GIT_COMMIT_SHA="${GIT_COMMIT_SHA:-$(git rev-parse --short HEAD 2>/dev/null || echo unknown)}"
export GIT_DIRTY="${GIT_DIRTY:-$(git_dirty)}"
export CPU_INFO="${CPU_INFO:-$(cpu_info)}"
export MEMORY_INFO="${MEMORY_INFO:-$(memory_info)}"
export K6_DOCKER_NETWORK_MODE="$network_label"

mkdir -p "$RESULT_DIR"

case "$MODE" in
  preflight|preflight.js)
    run_preflight "${PREFLIGHT_INCLUDE_ADMIN:-true}"
    ;;
  smoke|smoke.js)
    run_k6 smoke
    ;;
  warmup|warmup.js)
    run_warmup "${WARMUP_INCLUDE_ADMIN:-false}"
    ;;
  assignment-read|assignment-read.js)
    run_preflight "false"
    run_warmup "false"
    WARMUP_COMPLETED=true
    export WARMUP_COMPLETED
    run_k6 assignment-read
    ;;
  submission-status-read|submission-status-read.js)
    run_preflight "true"
    run_warmup "true"
    WARMUP_COMPLETED=true
    export WARMUP_COMPLETED
    run_k6 submission-status-read
    ;;
  all)
    require_command python3
    performance/fixtures/run-fixture.sh verify
    wait_readiness
    run_preflight "true"
    run_k6 smoke
    run_warmup "true"
    WARMUP_COMPLETED=true
    export WARMUP_COMPLETED
    run_k6 assignment-read
    run_k6 submission-status-read
    verify_summary smoke false
    verify_summary assignment-read false
    verify_summary submission-status-read false
    ;;
  *)
    echo "Unknown mode: $MODE" >&2
    echo "Usage: $0 [preflight|smoke|warmup|assignment-read|submission-status-read|all] [env-file]" >&2
    exit 2
    ;;
esac
