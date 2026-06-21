# Local k6 Baseline - 2026-06-20

## Scope

- Purpose: record a clean local baseline for future before/after comparison.
- Result type: local single-machine measurement only. This is not a production capacity claim.
- Improvement rate: not calculated because no application-code before/after comparison was run.
- Application code changes during measurement: none.

## Final Evidence

- Measurement Target SHA: `718ff0ee2ad25a323ea8fb9120151c6845ea7ea1`
- Documentation commit: docs-only commit after the measurement target
- Git Dirty during accepted runs: `false`
- Executed at: 2026-06-20 KST
- Base URL: `http://localhost:8080`
- Scenario: `assignment-read`
- Load model: `constant-arrival-rate`
- Target RPS: 100
- Duration: 2 minutes per run
- Repeats: 3 accepted runs
- Warm-up: completed before each accepted run
- Ratio: assignment list 60%, assignment detail 40%

## Environment

- Java: OpenJDK 21.0.8, Eclipse OpenJ9 VM 0.53.0
- Gradle JVM: 21.0.8, Eclipse OpenJ9 VM 0.53.0
- k6: `k6 v0.52.0 (commit/20f8febb5b, go1.22.4, darwin/arm64)`
- Docker: Client 29.6.0 / Server 29.5.3, Compose 5.1.4
- MongoDB: Docker Compose standalone, MongoDB 7.0.37, database `aandi_performance`
- Machine: macOS 26.5.1, Apple M5 Pro, 48.00 GiB memory
- JVM options: not set (`JAVA_TOOL_OPTIONS`, `GRADLE_OPTS`, `JVM_OPTS` were empty)

## Fixture

- Fingerprint: `005fc5cdb43a402413b263ae38b5565a73634b25bc7532cef346320ad295cf1a`
- Counts: courses 1, course weeks 3, assignments 30, enrollments 100, submission status projections 60
- Target assignment: `10000000-0000-4000-8000-000000000001`
- Private testcase marker: `PERF_PRIVATE_MUST_NOT_LEAK_001`
- Fixture verification: `performance/fixtures/run-fixture.sh cleanup`, `seed`, and `verify` passed before measurement

## Preflight And Smoke

- Readiness: 200 / `UP`
- USER assignment list: 200
- USER assignment detail: 200
- ADMIN submission status: 200
- Target assignment existed in list
- Detail `assignmentId` and `courseSlug` matched the request
- Response testcases exposed only `PUBLIC` visibility
- Private marker was not exposed
- Submission status counts matched `totalEnrolled`
- Check failures: 0
- Scenario skips: none

## Accepted 100 RPS Runs

| Run | Summary File | List P95 | Detail P95 | HTTP RPS | Success Throughput | HTTP Failure | Check Success | Dropped |
| :--- | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | `assignment-read-2026-06-20T14-20-59-631Z-718ff0ee2ad25a323ea8fb9120151c6845ea7ea1.summary.json` | 6.945 ms | 3.62385 ms | 100.0053 | 100.0053 req/s | 0.00% | 100.00% | 0 |
| 2 | `assignment-read-2026-06-20T14-23-35-159Z-718ff0ee2ad25a323ea8fb9120151c6845ea7ea1.summary.json` | 6.4296 ms | 3.0853 ms | 99.9993 | 99.9993 req/s | 0.00% | 100.00% | 0 |
| 3 | `assignment-read-2026-06-20T14-26-10-725Z-718ff0ee2ad25a323ea8fb9120151c6845ea7ea1.summary.json` | 6.559 ms | 3.0878 ms | 100.0022 | 100.0022 req/s | 0.00% | 100.00% | 0 |

## Three-Run Median

| Metric | Median | Min | Max |
| :--- | ---: | ---: | ---: |
| Assignment list P50 | 4.912 ms | 4.859 ms | 4.977 ms |
| Assignment list P95 | 6.559 ms | 6.4296 ms | 6.945 ms |
| Assignment list P99 | 8.997 ms | 8.7743 ms | 10.1608 ms |
| Assignment detail P50 | 2.209 ms | 2.209 ms | 2.359 ms |
| Assignment detail P95 | 3.0878 ms | 3.0853 ms | 3.62385 ms |
| Assignment detail P99 | 4.4197 ms | 3.95464 ms | 5.51077 ms |
| HTTP RPS | 100.0022 | 99.9993 | 100.0053 |
| Business success throughput | 100.0022 req/s | 99.9993 req/s | 100.0053 req/s |
| Assignment list success throughput | 60.4731 req/s | 59.9797 req/s | 60.5412 req/s |
| Assignment detail success throughput | 39.5321 req/s | 39.4580 req/s | 40.0226 req/s |
| HTTP failure rate | 0.00% | 0.00% | 0.00% |
| Check success rate | 100.00% | 100.00% | 100.00% |
| Dropped iterations | 0 | 0 | 0 |

Private testcase guard was 100% in all three accepted runs.

## Discarded Runs

- Earlier `dcf4592` results were exploratory only: they used a dirty working tree and local `k6 v2.0.0 (commit/devel)`.
- Earlier single-run 10/25/50/75/100 RPS results were exploratory only and are not used as final README evidence.
- An earlier 75 RPS warm-up failed because a 1-hour local JWT expired during the long run sequence.
- An earlier `1dfda70` 100 RPS three-run sequence was discarded because the first aggregate tool version treated missing `dropped_iterations` as failure. The aggregate tool was fixed, committed, and the three accepted runs above were rerun from a clean tree.

## Comparison Guidance

Future before/after comparison may calculate improvement only when these conditions match: fixture fingerprint and counts, JVM options, MongoDB mode, CPU and memory, k6 version, executor, target RPS, duration, request sleep seconds, endpoint ratio, warm-up completion, and accepted-run checks.

Formulas for a future after-run:

- P95 improvement rate = `(Before P95 - After P95) / Before P95 * 100`
- Success throughput improvement rate = `(After Success RPS - Before Success RPS) / Before Success RPS * 100`

Do not calculate those rates if any condition differs.
