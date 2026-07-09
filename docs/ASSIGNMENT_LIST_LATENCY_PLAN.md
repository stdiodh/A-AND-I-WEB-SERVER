# 과제 목록 API 응답 시간 측정 계획

> Repository call 감소가 응답 시간에 영향을 주는지 확인하기 위한 재현 절차입니다. 결과 수치는 실제 실행 후에만 기록합니다.

[README로 돌아가기](../README.md)

## Status

30개 assignment list-only before/after 측정은 `docs/performance/results/2026-07-09-assignment-list-before-after.md`에 기록했습니다.

남은 작업은 같은 절차를 300/1000개 fixture before/after로 확장하거나, 최대 처리량이 필요할 때 별도 capacity scenario를 정의하는 것입니다.

## Target API

| 항목 | 값 |
| :--- | :--- |
| API | `GET /v2/courses/{courseSlug}/assignments?status=PUBLISHED` |
| k6 endpoint tag | `student_assignment_list` |
| k6 script | `performance/k6/assignment-read.js` |
| list-only 조건 | `ASSIGNMENT_LIST_RATIO=100`, `ASSIGNMENT_DETAIL_RATIO=0` |
| 인증 | local HS256 USER JWT, `performance/k6/tools/generate_test_jwt.py`로 생성 |

v1 경로도 같은 `CourseQueryService.getAssignments(...)`를 사용하지만, 측정 대상은 v2 public read API로 고정합니다.

## Test Data Setup

local fixture database만 사용합니다. 스크립트는 `aandi_performance` 외 DB와 non-local MongoDB host를 거부합니다.

| Fixture | 목적 | Feasible 여부 |
| :--- | :--- | :--- |
| 1 assignment | 작은 응답에서 기본 overhead 확인 | 가능 |
| 30 assignments | 기존 repository call metric과 같은 데이터 크기 | 완료, `docs/performance/results/2026-07-09-assignment-list-before-after.md` |
| 300 assignments | 응답 payload와 child lookup 규모 증가 시 latency 확인 | 가능, local machine 상태 기록 필요 |

각 assignment는 requirement 1개, PUBLIC testcase 2개, HIDDEN testcase 1개를 생성합니다. 300 assignments fixture는 testcase 900개를 생성합니다.

## k6 Script Plan

새 k6 script는 만들지 않습니다. 기존 `assignment-read.js`가 endpoint별 custom trend를 이미 기록하므로 ratio만 list-only로 고정합니다.

기록할 지표:

| 지표 | k6 source |
| :--- | :--- |
| RPS | `http_reqs.rate` |
| duration | summary context `Configured Duration`, `Actual Test Duration` |
| VUs / maxVUs | summary context `Pre Allocated VUs`, `Max VUs` |
| P50 | `assignment_list_duration.med` |
| P95 | `assignment_list_duration.p(95)` |
| P99 | `assignment_list_duration.p(99)` |
| http_req_failed | `http_req_failed.rate` |
| checks | `checks.rate`, `checks.passes`, `checks.fails` |
| dropped_iterations | `dropped_iterations.count` |

Latency 비교는 같은 fixture size, 같은 commit pair, 같은 JVM/MongoDB/k6/hardware/load 조건에서만 계산합니다.

## Commands

공통 준비:

```bash
python3 performance/k6/tools/generate_test_jwt.py --output performance/k6/env.local
MONGO_DB_URL=mongodb://localhost:27017/aandi_performance ./gradlew bootRun
```

다른 터미널에서 fixture size별로 실행합니다.

```bash
SIZE=30
export FIXTURE_ASSIGNMENTS="$SIZE"
export FIXTURE_ENROLLMENTS=100
export FIXTURE_SUBMISSION_STATUSES=60

performance/fixtures/run-fixture.sh cleanup
performance/fixtures/run-fixture.sh seed
fixture_json="$(performance/fixtures/run-fixture.sh verify)"
export FIXTURE_FINGERPRINT="$(printf '%s\n' "$fixture_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["fixtureFingerprint"])')"

for run in 1 2 3; do
  RUN_LABEL="assignment-list-${SIZE}-r${run}" \
  LOAD_MODEL=arrival-rate \
  TARGET_RPS=100 \
  TEST_DURATION=2m \
  PRE_ALLOCATED_VUS=30 \
  MAX_VUS=200 \
  REQUEST_SLEEP_SECONDS=0 \
  ASSIGNMENT_LIST_RATIO=100 \
  ASSIGNMENT_DETAIL_RATIO=0 \
  FIXTURE_ASSIGNMENTS="$FIXTURE_ASSIGNMENTS" \
  FIXTURE_ENROLLMENTS="$FIXTURE_ENROLLMENTS" \
  FIXTURE_SUBMISSION_STATUSES="$FIXTURE_SUBMISSION_STATUSES" \
  FIXTURE_FINGERPRINT="$FIXTURE_FINGERPRINT" \
  performance/k6/run-local.sh assignment-read performance/k6/env.local
done
```

`SIZE=1`, `SIZE=30`, `SIZE=300`을 각각 같은 방식으로 실행합니다.

Summary export:

```bash
python3 performance/aggregate/summarize_runs.py \
  performance/results/assignment-read-<run1>.summary.json \
  performance/results/assignment-read-<run2>.summary.json \
  performance/results/assignment-read-<run3>.summary.json \
  > performance/results/assignment-list-${SIZE}.aggregate.json
```

before/after 비교가 필요하면 두 aggregate를 생성한 뒤 비교합니다.

```bash
python3 performance/aggregate/compare_aggregates.py \
  performance/results/assignment-list-${SIZE}-before.aggregate.json \
  performance/results/assignment-list-${SIZE}-after.aggregate.json \
  --json-output performance/results/assignment-list-${SIZE}-comparison.json \
  --markdown-output performance/results/assignment-list-${SIZE}-comparison.md
```

최종 resume/README 근거로 쓰려면 `gitDirty=false`인 clean checkout에서 실행한 summary만 사용합니다.

## Result Template

| Fixture assignments | Commit | Run | RPS | Duration | preAllocatedVUs | maxVUs | P50 | P95 | P99 | http_req_failed | checks | dropped_iterations | Summary JSON |
| :--- | :--- | :--- | ---: | :--- | ---: | ---: | ---: | ---: | ---: | ---: | :--- | ---: | :--- |
| 1 | `[commit]` | 1 | `[측정 후 입력]` | `2m` | 30 | 200 | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `performance/results/...` |
| 30 | `[commit]` | 1 | `[측정 후 입력]` | `2m` | 30 | 200 | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `performance/results/...` |
| 300 | `[commit]` | 1 | `[측정 후 입력]` | `2m` | 30 | 200 | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `[측정 후 입력]` | `performance/results/...` |

## Interpretation Rules

- Repository method call 감소와 API latency 감소를 같은 표현으로 합치지 않습니다.
- P95/P99 개선율은 before/after aggregate가 accepted=true일 때만 계산합니다.
- fixed 100 RPS 결과는 최대 처리량 또는 capacity 증가 근거로 사용하지 않습니다.
- run range가 겹치면 latency 개선으로 단정하지 않고 `range overlap`으로 기록합니다.
- HTTP 실패율, check 성공률, dropped iterations가 정상이어도 latency 개선을 자동으로 의미하지 않습니다.
