# Assignment List Before/After Performance

로컬 MongoDB `aandi_performance`와 localhost API만 대상으로 한 과제 목록 조회 before/after 고정 부하 측정입니다. 운영 최대 처리량으로 해석하지 않습니다.

- 측정일: `2026-07-09 KST`
- Target API: `GET /v2/courses/{courseSlug}/assignments?status=PUBLISHED`
- Endpoint source: `performance/k6/config.js`
- Before server commit: `9b678b6`
- After server commit: `642cfd3`
- k6 runner commit: `6682802`, `gitDirty=false`
- Java/JVM: `openjdk version "21.0.6" 2025-01-21`
- MongoDB: local standalone, `7.0.16`
- k6: `v0.52.0`
- Machine: Apple M5 Pro, 48.00 GiB

## Conditions

| 항목 | 값 |
| :--- | :--- |
| Fixture fingerprint | `005fc5cdb43a402413b263ae38b5565a73634b25bc7532cef346320ad295cf1a` |
| Fixture | Course 1, Course week 3, Assignment 30, Enrollment 100, Submission projection 60 |
| 부하 모델 | constant-arrival-rate |
| 부하 | 100 RPS, 2분, 3회 |
| 요청 비율 | 목록 100%, 상세 0% |
| VU allocation | preAllocatedVUs 30, maxVUs 200 |
| Request sleep | 0초 |
| Safety | localhost API와 local MongoDB만 사용 |

## Median Comparison

| Metric | Before `9b678b6` | After `642cfd3` | Change |
| :--- | ---: | ---: | :--- |
| Assignment list P50 | 7.280 ms | 6.273 ms | 13.83% lower |
| Assignment list P90 | 8.606 ms | 7.142 ms | 17.01% lower |
| Assignment list P95 | 9.297 ms | 7.565 ms | 18.63% lower |
| Assignment list P99 | 11.512 ms | 9.546 ms | 17.08% lower |
| Business success throughput | 100.002 req/s | 100.000 req/s | fixed-rate reference |
| HTTP failure rate | 0.00% | 0.00% | 유지 |
| Check success rate | 100.00% | 100.00% | 유지 |
| Dropped iterations | 0 | 0 | 유지 |

## Run Range

| Metric | Before range | After range |
| :--- | :--- | :--- |
| Assignment list P50 | 7.111-7.490 ms | 6.144-6.376 ms |
| Assignment list P95 | 9.168-9.419 ms | 7.422-7.730 ms |
| Assignment list P99 | 11.270-13.025 ms | 9.340-9.581 ms |

## Interpretation

같은 local fixed-load 조건에서 list-only 과제 목록 API P95 중앙값은 `9.297 ms`에서 `7.565 ms`로 측정되었습니다.

이 결과는 `9b678b6` 서버와 `642cfd3` 서버를 각각 실행해 측정했습니다. 생성된 k6 report의 `gitCommitSha=6682802`는 k6 실행 스크립트를 보유한 현재 repo commit 기준이므로, 서버 commit과 분리해서 해석합니다.

MongoDB `system.profile` command count는 profile cap/retention 영향이 있어 이 문서의 resume-safe 지표에서 제외합니다. Repository call `60 -> 2`는 service-level repository interaction 기준이며 MongoDB command count로 표현하지 않습니다.

## Reproduction Commands

```bash
git worktree add --detach /tmp/aandi-before-9b678b6 9b678b6
git worktree add --detach /tmp/aandi-after-642cfd3 642cfd3
docker compose up -d mongodb
python3 performance/k6/tools/generate_test_jwt.py --output performance/k6/env.local
```

Before server:

```bash
cd /tmp/aandi-before-9b678b6
MONGO_DB_URL=mongodb://localhost:27017/aandi_performance \
APP_V2_LOG_EXCLUDE_PATH_PREFIXES=/actuator,/swagger-ui,/v3/api-docs,/favicon.ico,/static,/assets,/webjars,/v2 \
./gradlew bootRun
```

Before k6:

```bash
PATH="/tmp/aandi-k6-0.52.0:$PATH" \
RESULT_DATE=2026-07-09-before-9b678b6-list-only-30 \
SCENARIO_FILES="/tmp/aandi-assignment-30-before-after-list-only.env" \
RUNS=3 \
LOCAL_RESULT_ROOT=/tmp/aandi-web-metrics/before-after/before \
DOC_RESULT_DIR=/tmp/aandi-web-metrics/docs \
DOC_BASENAME=assignment-list-before-9b678b6 \
CLEANUP_AFTER=true \
performance/scripts/run-assignment-scale-local.sh
```

After server:

```bash
cd /tmp/aandi-after-642cfd3
MONGO_DB_URL=mongodb://localhost:27017/aandi_performance \
APP_V2_LOG_EXCLUDE_PATH_PREFIXES=/actuator,/swagger-ui,/v3/api-docs,/favicon.ico,/static,/assets,/webjars,/v2 \
./gradlew bootRun
```

After k6:

```bash
PATH="/tmp/aandi-k6-0.52.0:$PATH" \
RESULT_DATE=2026-07-09-after-642cfd3-list-only-30 \
SCENARIO_FILES="/tmp/aandi-assignment-30-before-after-list-only.env" \
RUNS=3 \
LOCAL_RESULT_ROOT=/tmp/aandi-web-metrics/before-after/after \
DOC_RESULT_DIR=/tmp/aandi-web-metrics/docs \
DOC_BASENAME=assignment-list-after-642cfd3 \
CLEANUP_AFTER=true \
performance/scripts/run-assignment-scale-local.sh
```

Repository interaction characterization test:

```bash
./gradlew test --tests 'com.example.aandi_post_web_server.course.application.service.CourseQueryServiceTest'
```
