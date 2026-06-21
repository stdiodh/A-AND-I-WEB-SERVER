# k6 Test Guide

This guide describes the executable k6 assets under `performance/k6`. It does not replace `docs/MEASUREMENT.md` and does not add unverified performance numbers.

## Confirmed API Targets

Actual Controller code confirms these v2 read endpoints:

| Scenario | Method | Path | Role |
| :--- | :--- | :--- | :--- |
| Readiness | GET | `/actuator/health/readiness` | anonymous |
| Student assignment list | GET | `/v2/courses/{courseSlug}/assignments?status=PUBLISHED` | USER, ORGANIZER, or ADMIN |
| Student assignment detail | GET | `/v2/courses/{courseSlug}/assignments/{assignmentId}` | USER, ORGANIZER, or ADMIN |
| Admin submission statuses | GET | `/v2/admin/courses/{courseSlug}/assignments/{assignmentId}/submission-statuses` | ADMIN |

v2 requests include `Authenticate`, `Authorization`, `deviceOS`, and `timestamp` headers. JWTs are injected through environment variables or generated into ignored local files.

## Safety Guards

- `BASE_URL` defaults to `http://localhost:8080` for local k6.
- Docker Desktop defaults to `http://host.docker.internal:8080` with `DOCKER_LOCAL_MODE=true`.
- Local hosts are limited to `localhost`, `127.0.0.1`, and `[::1]`.
- `host.docker.internal` is allowed only with `DOCKER_LOCAL_MODE=true`.
- Remote targets require all of: `ALLOW_REMOTE_LOAD_TEST=true`, `TARGET_ENVIRONMENT=staging`, exact hostname in `REMOTE_TARGET_ALLOWLIST`, and HTTPS.
- `TARGET_ENVIRONMENT=prod` or `TARGET_ENVIRONMENT=production` is always blocked.
- URL credentials, query strings, fragments, and malformed ports are rejected.
- Write API load tests are not implemented and are not part of the default run.
- SNS/SQS event throughput is not measured by these HTTP scripts.

## Local Fixture

Seed synthetic local data only:

```bash
performance/fixtures/run-fixture.sh seed
performance/fixtures/run-fixture.sh verify
python3 performance/k6/tools/generate_test_jwt.py --output performance/k6/env.local
```

Run the application against the fixture database:

```bash
MONGO_DB_URL=mongodb://localhost:27017/aandi_performance ./gradlew bootRun
```

## Run Commands

Local k6 binary:

```bash
performance/k6/run-local.sh preflight performance/k6/env.local
performance/k6/run-local.sh smoke performance/k6/env.local
performance/k6/run-local.sh assignment-read performance/k6/env.local
performance/k6/run-local.sh submission-status-read performance/k6/env.local
performance/k6/run-local.sh all performance/k6/env.local
```

Dockerized k6:

```bash
performance/k6/run-docker.sh smoke performance/k6/env.local
performance/k6/run-docker.sh assignment-read performance/k6/env.local
performance/k6/run-docker.sh submission-status-read performance/k6/env.local
```

`all` fails if a required scenario is skipped, records zero business requests, or fails thresholds. Set `SKIP_PREFLIGHT=true` only for explicit troubleshooting.

## Admin Scenario

`submission-status-read.js` requires `ADMIN_ACCESS_TOKEN` by default. It skips only when `ALLOW_SCENARIO_SKIP=true`, and skipped summaries must not be treated as executed load tests.

## Thresholds

No repository SLO is currently defined. Default failure conditions are:

- `http_req_failed` rate below 1%.
- `checks` rate greater than 99%.

P95 latency is recorded in the generated summary but is not a default failure condition. Set `P95_THRESHOLD_MS` only when there is an agreed threshold for the same dataset and environment.

## Outputs

`handleSummary` writes scenario-specific files:

- `performance/results/{scenario}-{executedAt}-{gitSha}.summary.json`
- `performance/results/{scenario}-{executedAt}-{gitSha}.summary.md`

Metric values that were not produced are rendered as `n/a`, `not executed`, or `skipped`, never as synthetic `0 ms` latency.

## Comparison

Use:

```bash
python3 performance/compare/compare_results.py before.summary.json after.summary.json
```

The tool refuses comparison when fixture fingerprint, runtime shape, JVM/Mongo/hardware/k6 metadata, warm-up status, checks, thresholds, or request counts do not match. It reports metric deltas only and does not call them performance improvements.
