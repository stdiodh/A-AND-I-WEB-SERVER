#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
SCENARIO_FILES="${SCENARIO_FILES:-performance/fixtures/scenarios/assignment-300.env performance/fixtures/scenarios/assignment-1000.env}"
TOKEN_ENV_FILE="${TOKEN_ENV_FILE:-performance/k6/env.local}"
RUNS="${RUNS:-3}"
RESULT_DATE="${RESULT_DATE:-$(date +%F)}"
LOCAL_RESULT_ROOT="${LOCAL_RESULT_ROOT:-performance/results/assignment-scale/${RESULT_DATE}}"
DOC_RESULT_DIR="${DOC_RESULT_DIR:-docs/performance/results}"
DOC_BASENAME="${DOC_BASENAME:-${RESULT_DATE}-assignment-scale}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "$1 is required for local assignment scale measurement." >&2
    exit 127
  fi
}

load_env_file() {
  file="$1"
  case "$file" in
    /*) resolved="$file" ;;
    *) resolved="$ROOT_DIR/$file" ;;
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

assert_positive_int() {
  name="$1"
  value="$2"
  case "$value" in
    ''|*[!0-9]*)
      echo "$name must be a positive integer." >&2
      exit 2
      ;;
    0)
      echo "$name must be greater than 0." >&2
      exit 2
      ;;
  esac
}

assert_local_target() {
  python3 - "$BASE_URL" "${TARGET_ENVIRONMENT:-local}" <<'PY'
import sys
from urllib.parse import urlparse

base_url, target_environment = sys.argv[1], sys.argv[2].strip().lower()
if target_environment in {"prod", "production", "staging"}:
    raise SystemExit(f"Refusing TARGET_ENVIRONMENT={target_environment} for local scale measurement.")
parsed = urlparse(base_url)
if parsed.scheme not in {"http", "https"}:
    raise SystemExit("BASE_URL must use http or https.")
if parsed.username or parsed.password:
    raise SystemExit("BASE_URL credentials are not allowed.")
if parsed.hostname not in {"localhost", "127.0.0.1"}:
    raise SystemExit(f"BASE_URL must target localhost or 127.0.0.1, got {parsed.hostname or 'unknown'}.")
PY
}

assert_local_mongo_uri() {
  name="$1"
  value="$2"
  python3 - "$name" "$value" <<'PY'
import sys
from urllib.parse import urlparse

name, value = sys.argv[1], sys.argv[2]
parsed = urlparse(value)
if parsed.scheme != "mongodb":
    raise SystemExit(f"{name} must start with mongodb://")
if parsed.username or parsed.password:
    raise SystemExit(f"{name} credentials are not allowed.")
if parsed.hostname not in {"localhost", "127.0.0.1"}:
    raise SystemExit(f"{name} must target localhost or 127.0.0.1, got {parsed.hostname or 'unknown'}.")
database = parsed.path.lstrip("/").split("/", 1)[0]
if database != "aandi_performance":
    raise SystemExit(f"{name} must use aandi_performance database, got {database or 'missing'}.")
PY
}

fixture_fingerprint() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as f:
    payload = json.load(f)
print(payload["fixtureFingerprint"])
PY
}

latest_summary_json() {
  scenario_result_dir="$1"
  latest="$(ls -t "$scenario_result_dir"/assignment-read-*.summary.json 2>/dev/null | head -n 1 || true)"
  if [ -z "$latest" ]; then
    echo "No assignment-read summary JSON found in $scenario_result_dir" >&2
    exit 1
  fi
  printf '%s\n' "$latest"
}

run_scenario() {
  scenario_file="$1"
  (
    load_env_file "$TOKEN_ENV_FILE"
    load_env_file "$scenario_file"

    : "${SCENARIO_NAME:=$(basename "$scenario_file" .env)}"
    : "${MONGO_URI:=mongodb://localhost:27017/aandi_performance}"
    : "${MONGO_DB_URL:=$MONGO_URI}"
    : "${BASE_URL:=http://localhost:8080}"
    : "${TARGET_ENVIRONMENT:=local}"
    : "${REQUIRE_LOCAL_BASE_URL:=true}"
    : "${FIXTURE_COURSES:=1}"
    : "${FIXTURE_ASSIGNMENTS:=30}"
    : "${FIXTURE_ENROLLMENTS:=100}"
    : "${FIXTURE_SUBMISSION_STATUSES:=60}"
    : "${LOAD_MODEL:=arrival-rate}"
    : "${TARGET_RPS:=100}"
    : "${TEST_DURATION:=2m}"
    : "${PRE_ALLOCATED_VUS:=30}"
    : "${MAX_VUS:=200}"
    : "${MONGODB_MODE:=local-docker-compose}"

    export SCENARIO_NAME MONGO_URI MONGO_DB_URL BASE_URL TARGET_ENVIRONMENT REQUIRE_LOCAL_BASE_URL
    export FIXTURE_COURSES FIXTURE_ASSIGNMENTS FIXTURE_ENROLLMENTS FIXTURE_SUBMISSION_STATUSES
    export LOAD_MODEL TARGET_RPS TEST_DURATION PRE_ALLOCATED_VUS MAX_VUS MONGODB_MODE
    export K6_DOCKER_NETWORK_MODE="${K6_DOCKER_NETWORK_MODE:-local-binary}"
    export RESULT_DIR="$LOCAL_RESULT_ROOT/$SCENARIO_NAME/k6"
    export SCALE_SCENARIO_DIR="$LOCAL_RESULT_ROOT/$SCENARIO_NAME"
    export SCALE_PROFILE_DIR="$SCALE_SCENARIO_DIR/mongo"

    assert_positive_int RUNS "$RUNS"
    assert_local_target
    assert_local_mongo_uri MONGO_URI "$MONGO_URI"
    assert_local_mongo_uri MONGO_DB_URL "$MONGO_DB_URL"

    mkdir -p "$RESULT_DIR" "$SCALE_PROFILE_DIR"

    performance/fixtures/run-fixture.sh cleanup >/dev/null
    performance/fixtures/run-fixture.sh seed >/dev/null
    performance/fixtures/run-fixture.sh verify > "$SCALE_SCENARIO_DIR/fixture-verify.json"
    export FIXTURE_FINGERPRINT="$(fixture_fingerprint "$SCALE_SCENARIO_DIR/fixture-verify.json")"

    summary_files=""
    profile_files=""
    run_index=1
    while [ "$run_index" -le "$RUNS" ]; do
      export RUN_LABEL="${SCENARIO_NAME}-run-${run_index}"
      MONGO_PROFILE_MODE=start mongosh "$MONGO_URI" --quiet performance/mongo/collect_profile.js > "$SCALE_PROFILE_DIR/run-${run_index}.start.json"
      performance/k6/run-local.sh assignment-read
      MONGO_PROFILE_MODE=collect mongosh "$MONGO_URI" --quiet performance/mongo/collect_profile.js > "$SCALE_PROFILE_DIR/run-${run_index}.profile.json"

      latest="$(latest_summary_json "$RESULT_DIR")"
      summary_files="$summary_files $latest"
      profile_files="$profile_files $SCALE_PROFILE_DIR/run-${run_index}.profile.json"
      run_index=$((run_index + 1))
    done

    python3 performance/aggregate/summarize_runs.py $summary_files \
      --json-output "$SCALE_SCENARIO_DIR/${SCENARIO_NAME}.aggregate.json" \
      --markdown-output "$SCALE_SCENARIO_DIR/${SCENARIO_NAME}.aggregate.md" \
      > "$SCALE_SCENARIO_DIR/${SCENARIO_NAME}.aggregate.stdout.json"

    node performance/mongo/summarize_profile.js $profile_files \
      --json-output "$SCALE_SCENARIO_DIR/${SCENARIO_NAME}.mongo-profile.json" \
      --markdown-output "$SCALE_SCENARIO_DIR/${SCENARIO_NAME}.mongo-profile.md" \
      > "$SCALE_SCENARIO_DIR/${SCENARIO_NAME}.mongo-profile.stdout.json"

    if [ "${CLEANUP_AFTER:-true}" = "true" ]; then
      performance/fixtures/run-fixture.sh cleanup >/dev/null
    fi

    python3 - "$MANIFEST_FILE" "$SCENARIO_NAME" "$SCALE_SCENARIO_DIR" <<'PY'
import json
import sys

manifest, scenario, scenario_dir = sys.argv[1:]
record = {
    "scenario": scenario,
    "scenarioDir": scenario_dir,
    "fixtureVerifyJson": f"{scenario_dir}/fixture-verify.json",
    "aggregateJson": f"{scenario_dir}/{scenario}.aggregate.json",
    "aggregateMarkdown": f"{scenario_dir}/{scenario}.aggregate.md",
    "mongoProfileJson": f"{scenario_dir}/{scenario}.mongo-profile.json",
    "mongoProfileMarkdown": f"{scenario_dir}/{scenario}.mongo-profile.md",
}
with open(manifest, "a", encoding="utf-8") as f:
    f.write(json.dumps(record, ensure_ascii=False) + "\n")
PY
  )
}

render_docs() {
  python3 - "$MANIFEST_FILE" "$DOC_JSON" "$DOC_MD" "$RESULT_DATE" "$JAVA_VERSION_INFO" <<'PY'
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

manifest_path, json_path, md_path, result_date, java_version = sys.argv[1:]
records = [json.loads(line) for line in Path(manifest_path).read_text(encoding="utf-8").splitlines() if line.strip()]
scenarios = []
for record in records:
    aggregate = json.loads(Path(record["aggregateJson"]).read_text(encoding="utf-8"))
    profile = json.loads(Path(record["mongoProfileJson"]).read_text(encoding="utf-8"))
    fixture = json.loads(Path(record["fixtureVerifyJson"]).read_text(encoding="utf-8"))
    scenarios.append({
        "name": record["scenario"],
        "files": record,
        "fixture": fixture,
        "k6": {
            "context": aggregate["context"],
            "summary": aggregate["summary"],
        },
        "mongo": {
            "context": profile["context"],
            "summary": profile["summary"],
        },
    })

payload = {
    "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    "resultDate": result_date,
    "scope": "local-only assignment read scale fixture",
    "safety": {
        "baseUrl": "localhost or 127.0.0.1 only",
        "mongoDatabase": "aandi_performance only",
        "remoteTargetsAllowed": False,
        "productionAccessAllowed": False,
    },
    "comparison": {
        "status": "[비교 불가]",
        "reason": "before/after 조건을 완전히 동일하게 맞춘 비교 입력이 아니므로 latency 개선율을 계산하지 않습니다.",
    },
    "javaVersion": java_version,
    "scenarios": scenarios,
    "resumeSentence": {
        "confirmed": "300/1000개 과제 fixture에서 k6 고정 부하와 MongoDB profile로 읽기 API P95/P99와 DB 접근 효율을 로컬 재현 환경에서 검증",
        "beforeMeasurement": "과제 조회 API의 scale fixture 성능 측정 환경을 구축해 P95/P99와 DB 접근 패턴을 회귀 기준으로 관리",
    },
}

def render_markdown(payload):
    lines = [
        "# Assignment Scale Performance",
        "",
        "> 로컬 MongoDB `aandi_performance`와 localhost API만 대상으로 한 고정 부하 회귀 검증 결과입니다. 운영 최대 처리량으로 해석하지 않습니다.",
        "",
        f"- Generated At: `{payload['generatedAt']}`",
        f"- Java/JVM: `{payload['javaVersion']}`",
        "- Scope: local-only assignment read scale fixture",
        "- Comparison: `[비교 불가]` - before/after 조건을 완전히 동일하게 맞춘 비교 입력이 아니므로 latency 개선율을 계산하지 않습니다.",
        "",
        "## Safety",
        "",
        "- BASE_URL은 `localhost` 또는 `127.0.0.1`만 허용합니다.",
        "- MongoDB는 `mongodb://localhost:27017/aandi_performance` 또는 `127.0.0.1`의 동일 DB만 허용합니다.",
        "- `TARGET_ENVIRONMENT=prod`, `production`, `staging`은 실행 실패 처리합니다.",
        "- `aandiclub.com`, `api.aandiclub.com`, AWS SNS/SQS, CloudWatch, production Discord webhook, production DB, EC2 public IP는 사용하지 않습니다.",
        "",
        "## k6 Median",
        "",
        "| Scenario | Commit | Fixture | k6 | Machine | P50 | P90 | P95 | P99 | HTTP failed | Checks | Throughput | Iterations | Dropped |",
        "| :--- | :--- | :--- | :--- | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for scenario in payload["scenarios"]:
        context = scenario["k6"]["context"]
        summary = scenario["k6"]["summary"]
        machine = f"{context.get('cpu')} / {context.get('memory')}"
        lines.append(
            "| "
            + " | ".join([
                scenario["name"],
                f"`{context.get('gitCommitSha')}`",
                f"`{context.get('fixtureFingerprint')}`",
                f"`{context.get('k6Version')}`",
                machine,
                fmt(summary["business_p50_ms"]["median"], " ms"),
                fmt(summary["business_p90_ms"]["median"], " ms"),
                fmt(summary["business_p95_ms"]["median"], " ms"),
                fmt(summary["business_p99_ms"]["median"], " ms"),
                percent(summary["http_failure_rate"]["median"]),
                percent(summary["check_success_rate"]["median"]),
                fmt(summary["business_success_throughput"]["median"], " req/s"),
                fmt(summary["iterations"]["median"], ""),
                fmt(summary["dropped_iterations"]["median"], ""),
            ])
            + " |"
        )
    lines.extend(["", "## MongoDB Profile Median", "", "| Scenario | MongoDB | Command count | Docs examined | Keys examined | Explain docs | Explain keys |", "| :--- | :--- | ---: | ---: | ---: | ---: | ---: |"])
    for scenario in payload["scenarios"]:
        mongo = scenario["mongo"]
        summary = mongo["summary"]
        lines.append(
            "| "
            + " | ".join([
                scenario["name"],
                str(mongo["context"].get("mongoVersion")),
                fmt(summary["commandCount"]["median"], ""),
                fmt(summary["totalDocsExamined"]["median"], ""),
                fmt(summary["totalKeysExamined"]["median"], ""),
                fmt(summary["explainTotalDocsExamined"]["median"], ""),
                fmt(summary["explainTotalKeysExamined"]["median"], ""),
            ])
            + " |"
        )
    lines.extend(["", "## Resume Sentence", "", f"- 확인된 경우: {payload['resumeSentence']['confirmed']}", f"- 측정 전: {payload['resumeSentence']['beforeMeasurement']}", ""])
    return "\n".join(lines)

def fmt(value, suffix):
    number = float(value)
    if number.is_integer():
        return f"{int(number)}{suffix}"
    return f"{number:.2f}{suffix}"

def percent(value):
    return f"{float(value) * 100:.2f}%"

Path(json_path).parent.mkdir(parents=True, exist_ok=True)
Path(json_path).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
Path(md_path).write_text(render_markdown(payload), encoding="utf-8")
PY
}

cd "$ROOT_DIR"
require_command python3
require_command node
require_command mongosh
require_command k6

assert_positive_int RUNS "$RUNS"
if [ "$RUNS" -lt 3 ]; then
  echo "RUNS must be at least 3 for median scale measurement." >&2
  exit 2
fi

if [ ! -f "$TOKEN_ENV_FILE" ]; then
  echo "Token env file not found: $TOKEN_ENV_FILE" >&2
  echo "Run: python3 performance/k6/tools/generate_test_jwt.py --output $TOKEN_ENV_FILE" >&2
  exit 2
fi

JAVA_VERSION_INFO="$(java -version 2>&1 | head -n 1 || echo unknown)"
export JAVA_VERSION_INFO

mkdir -p "$LOCAL_RESULT_ROOT" "$DOC_RESULT_DIR"
MANIFEST_FILE="$LOCAL_RESULT_ROOT/manifest.jsonl"
DOC_JSON="$DOC_RESULT_DIR/${DOC_BASENAME}.json"
DOC_MD="$DOC_RESULT_DIR/${DOC_BASENAME}.md"
export MANIFEST_FILE DOC_JSON DOC_MD
: > "$MANIFEST_FILE"

for scenario_file in $SCENARIO_FILES; do
  run_scenario "$scenario_file"
done

render_docs

echo "Wrote local scale JSON: $DOC_JSON"
echo "Wrote local scale Markdown: $DOC_MD"
