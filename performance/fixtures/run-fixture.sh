#!/usr/bin/env sh
set -eu

MODE="${1:-verify}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
MONGO_URI="${MONGO_URI:-mongodb://localhost:27017/aandi_performance}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "$1 is required for fixture $MODE." >&2
    exit 127
  fi
}

parse_mongo_uri() {
  case "$MONGO_URI" in
    mongodb://*) rest="${MONGO_URI#mongodb://}" ;;
    *)
      echo "MONGO_URI must start with mongodb:// for local fixture scripts." >&2
      exit 2
      ;;
  esac
  if printf '%s' "$rest" | grep -q '@'; then
    echo "MONGO_URI credentials are not allowed for local fixture scripts." >&2
    exit 2
  fi
  host_port="${rest%%/*}"
  db_part="${rest#*/}"
  db_name="${db_part%%\?*}"
  db_name="${db_name%%/*}"
  host="${host_port%%:*}"
  if [ -z "$host" ] || [ -z "$db_name" ]; then
    echo "MONGO_URI must include host and database name." >&2
    exit 2
  fi
  case "$host" in
    localhost|127.0.0.1|mongodb)
      ;;
    *)
      echo "Refusing non-local MongoDB host for fixture scripts: $host" >&2
      exit 2
      ;;
  esac
  if [ "$db_name" != "aandi_performance" ]; then
    echo "Refusing non-fixture MongoDB database: $db_name" >&2
    exit 2
  fi
  MONGO_HOST="$host"
  MONGO_DB="$db_name"
  export MONGO_HOST MONGO_DB
}

script_for_mode() {
  case "$1" in
    seed)
      echo "performance/fixtures/seed.js"
      ;;
    verify)
      echo "performance/fixtures/verify.js"
      ;;
    cleanup)
      echo "performance/fixtures/cleanup.js"
      ;;
    *)
      echo "Unknown fixture mode: $1" >&2
      echo "Usage: $0 [seed|verify|cleanup]" >&2
      exit 2
      ;;
  esac
}

cd "$ROOT_DIR"
require_command mongosh
parse_mongo_uri

: "${FIXTURE_ASSIGNMENTS:=30}"
: "${FIXTURE_ENROLLMENTS:=100}"
: "${FIXTURE_SUBMISSION_STATUSES:=60}"
: "${PRIVATE_TESTCASE_MARKER:=PERF_PRIVATE_MUST_NOT_LEAK_001}"
: "${PRIVATE_TESTCASE_SEQ:=9001}"

export FIXTURE_ASSIGNMENTS FIXTURE_ENROLLMENTS FIXTURE_SUBMISSION_STATUSES
export PRIVATE_TESTCASE_MARKER PRIVATE_TESTCASE_SEQ

mongosh "$MONGO_URI" --quiet "$(script_for_mode "$MODE")"
