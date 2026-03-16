#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/migrate_legacy_reports.sh \
    --backup /absolute/path/aandi_backup.tar.gz \
    --api-base http://localhost:8080 \
    [--token <ACCESS_TOKEN>] \
    [--user-id legacy-migrator] \
    [--user-role ADMIN] \
    --course-slug back-basic \
    --course-title "BACK 기초" \
    [--course-description "legacy 3기 이관"] \
    [--course-phase BASIC] \
    [--course-track FL] \
    [--title-suffix "-3rd"] \
    [--deliver]

Description:
  Legacy Report(Report.bson) 데이터를 v1 코스/과제 API로 이관합니다.
  - 코스 생성(이미 있으면 유지)
  - 주차 생성/업데이트
  - 과제 생성
  - 과제 게시(PUBLISHED)
  - (옵션) 배포 트리거

Required commands:
  tar, bsondump, jq, curl
EOF
}

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "missing command: $1" >&2
    exit 1
  fi
}

http_call() {
  local method="$1"
  local url="$2"
  local payload="${3:-}"
  local response
  local -a headers

  headers=(
    -H "X-User-Role: ${USER_ROLE}"
    -H "X-User-Id: ${USER_ID}"
  )
  if [[ -n "$TOKEN" ]]; then
    headers+=(-H "Authorization: Bearer ${TOKEN}")
  fi

  if [[ -n "$payload" ]]; then
    response="$(curl -sS -w $'\n%{http_code}' -X "$method" \
      "${headers[@]}" \
      -H "Content-Type: application/json" \
      -d "$payload" \
      "$url")"
  else
    response="$(curl -sS -w $'\n%{http_code}' -X "$method" \
      "${headers[@]}" \
      "$url")"
  fi

  HTTP_BODY="$(printf '%s\n' "$response" | sed '$d')"
  HTTP_CODE="$(printf '%s\n' "$response" | tail -n1)"
}

BACKUP_PATH=""
API_BASE=""
TOKEN=""
USER_ID="legacy-migrator"
USER_ROLE="ADMIN"
COURSE_SLUG=""
COURSE_TITLE=""
COURSE_DESCRIPTION="legacy migration"
COURSE_PHASE="BASIC"
COURSE_TRACK="FL"
TITLE_SUFFIX=""
DELIVER="false"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) BACKUP_PATH="$2"; shift 2 ;;
    --api-base) API_BASE="$2"; shift 2 ;;
    --token) TOKEN="$2"; shift 2 ;;
    --user-id) USER_ID="$2"; shift 2 ;;
    --user-role) USER_ROLE="$2"; shift 2 ;;
    --course-slug) COURSE_SLUG="$2"; shift 2 ;;
    --course-title) COURSE_TITLE="$2"; shift 2 ;;
    --course-description) COURSE_DESCRIPTION="$2"; shift 2 ;;
    --course-phase) COURSE_PHASE="$2"; shift 2 ;;
    --course-track) COURSE_TRACK="$2"; shift 2 ;;
    --title-suffix) TITLE_SUFFIX="$2"; shift 2 ;;
    --deliver) DELIVER="true"; shift 1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "$BACKUP_PATH" || -z "$API_BASE" || -z "$COURSE_SLUG" || -z "$COURSE_TITLE" || -z "$USER_ID" || -z "$USER_ROLE" ]]; then
  usage
  exit 1
fi

normalized_user_role="$(printf '%s' "$USER_ROLE" | tr '[:lower:]' '[:upper:]')"
if [[ "$normalized_user_role" != "ADMIN" ]]; then
  echo "legacy migration requires ADMIN role. --user-role must be ADMIN." >&2
  exit 1
fi

if [[ ! -f "$BACKUP_PATH" ]]; then
  echo "backup file not found: $BACKUP_PATH" >&2
  exit 1
fi

require_cmd tar
require_cmd bsondump
require_cmd jq
require_cmd curl

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

report_path="$(tar -tzf "$BACKUP_PATH" | grep -E 'aandi_backup/aandi_db/Report\.bson$|aandi_backup/backup_aandi/aandi_db/Report\.bson$' | head -n1 || true)"
if [[ -z "$report_path" ]]; then
  echo "Report.bson not found in backup archive." >&2
  exit 1
fi

tar -xzf "$BACKUP_PATH" -C "$tmpdir" "$report_path"
bsondump "$tmpdir/$report_path" > "$tmpdir/legacy_report_raw.jsonl"

jq -c '
  {
    weekNo: ((.week."$numberInt" // .week) | tonumber),
    seqInWeek: ((.seq."$numberInt" // .seq) | tonumber),
    title: .title,
    content: .content,
    requirements: (
      (.requirement // [])
      | sort_by((.seq."$numberInt" // .seq | tonumber))
      | map({
          sortOrder: ((.seq."$numberInt" // .seq) | tonumber),
          requirementText: .content
        })
    ),
    goals: (
      (.objects // [])
      | sort_by((.seq."$numberInt" // .seq | tonumber))
      | map(.content)
    ),
    examples: (
      (.exampleIO // [])
      | sort_by((.seq."$numberInt" // .seq | tonumber))
      | map({
          seq: ((.seq."$numberInt" // .seq) | tonumber),
          inputText: (.input // ""),
          outputText: (.output // ""),
          description: null
        })
    ),
    level: .level,
    openAt: (((.startAt."$date"."$numberLong" // .startAt."$date" // .startAt) | tonumber) / 1000 | todateiso8601),
    dueAt: (((.endAt."$date"."$numberLong" // .endAt."$date" // .endAt) | tonumber) / 1000 | todateiso8601)
  }
' "$tmpdir/legacy_report_raw.jsonl" > "$tmpdir/legacy_report_normalized_unsorted.jsonl"

jq -cs 'sort_by(.weekNo, .seqInWeek)[]' "$tmpdir/legacy_report_normalized_unsorted.jsonl" > "$tmpdir/legacy_report_normalized.jsonl"

echo "[1/4] ensure course: $COURSE_SLUG"
course_payload="$(jq -n \
  --arg slug "$COURSE_SLUG" \
  --arg title "$COURSE_TITLE" \
  --arg description "$COURSE_DESCRIPTION" \
  --arg phase "$COURSE_PHASE" \
  --arg targetTrack "$COURSE_TRACK" \
  '{slug:$slug,title:$title,description:$description,phase:$phase,targetTrack:$targetTrack}')"

http_call POST "${API_BASE}/v1/admin/courses" "$course_payload"
if [[ "$HTTP_CODE" != "200" && "$HTTP_CODE" != "201" && "$HTTP_CODE" != "409" ]]; then
  echo "course create failed: HTTP $HTTP_CODE" >&2
  echo "$HTTP_BODY" >&2
  exit 1
fi

total=0
created=0
published=0
delivered=0
skipped=0

echo "[2/4] migrate assignments"
while IFS= read -r line; do
  total=$((total + 1))
  weekNo="$(jq -r '.weekNo' <<<"$line")"
  seqInWeek="$(jq -r '.seqInWeek' <<<"$line")"
  title="$(jq -r '.title' <<<"$line")"
  if [[ -n "$TITLE_SUFFIX" ]]; then
    title="${title}${TITLE_SUFFIX}"
  fi
  content="$(jq -r '.content' <<<"$line")"
  openAt="$(jq -r '.openAt' <<<"$line")"
  dueAt="$(jq -r '.dueAt' <<<"$line")"
  level="$(jq -r '.level // "MEDIUM"' <<<"$line")"
  report_type="$(jq -r '.reportType // "CS"' <<<"$line")"
  requirements_json="$(jq -c '.requirements | map({seq:.sortOrder,content:.requirementText})' <<<"$line")"
  objects_json="$(jq -c '.goals | to_entries | map({seq:(.key + 1), content:.value})' <<<"$line")"
  examples_json="$(jq -c '.examples | map({seq:.seq,input:.inputText,output:.outputText})' <<<"$line")"

  week_title="${weekNo}주차"
  week_payload="$(jq -n \
    --argjson weekNo "$weekNo" \
    --arg title "$week_title" \
    '{weekNo:$weekNo,title:$title}')"
  http_call POST "${API_BASE}/v1/admin/courses/${COURSE_SLUG}/weeks" "$week_payload"
  if [[ "$HTTP_CODE" != "200" && "$HTTP_CODE" != "201" ]]; then
    echo "week upsert failed (week=${weekNo}): HTTP $HTTP_CODE" >&2
    echo "$HTTP_BODY" >&2
    exit 1
  fi

  assignment_payload="$(jq -n \
    --argjson week "$weekNo" \
    --argjson seq "$seqInWeek" \
    --arg title "$title" \
    --arg content "$content" \
    --arg reportType "$report_type" \
    --arg startAt "$openAt" \
    --arg endAt "$dueAt" \
    --arg level "$level" \
    --argjson requirement "$requirements_json" \
    --argjson objects "$objects_json" \
    --argjson exampleIO "$examples_json" \
    '{
      week:$week,
      seq:$seq,
      title:$title,
      content:$content,
      requirement:$requirement,
      objects:$objects,
      exampleIO:$exampleIO,
      reportType:$reportType,
      startAt:$startAt,
      endAt:$endAt,
      level:$level,
      timeLimitMinutes:60
    }')"

  http_call POST "${API_BASE}/v1/admin/courses/${COURSE_SLUG}/assignments" "$assignment_payload"
  assignment_id=""
  if [[ "$HTTP_CODE" == "200" || "$HTTP_CODE" == "201" ]]; then
    assignment_id="$(jq -r '.id // empty' <<<"$HTTP_BODY")"
    created=$((created + 1))
  elif [[ "$HTTP_CODE" == "409" ]]; then
    skipped=$((skipped + 1))
    http_call GET "${API_BASE}/v1/courses/${COURSE_SLUG}/assignments?week=${weekNo}" ""
    if [[ "$HTTP_CODE" != "200" ]]; then
      echo "failed to load existing assignment (week=${weekNo}, seq=${seqInWeek}): HTTP $HTTP_CODE" >&2
      echo "$HTTP_BODY" >&2
      exit 1
    fi
    assignment_id="$(jq -r --argjson seq "$seqInWeek" '.[] | select((.seq // .seqInWeek) == $seq) | .id' <<<"$HTTP_BODY" | head -n1)"
  else
    echo "assignment create failed (week=${weekNo}, seq=${seqInWeek}): HTTP $HTTP_CODE" >&2
    echo "$HTTP_BODY" >&2
    exit 1
  fi

  if [[ -z "$assignment_id" ]]; then
    echo "assignment id not found (week=${weekNo}, seq=${seqInWeek})" >&2
    exit 1
  fi

  echo "  - migrated week=${weekNo} seq=${seqInWeek} id=${assignment_id}"
done < "$tmpdir/legacy_report_normalized.jsonl"

echo "[3/4] done"
echo "summary:"
echo "  total:      $total"
echo "  created:    $created"
echo "  duplicated: $skipped"
echo "  published:  $published"
echo "  delivered:  $delivered"
echo "[4/4] complete"
