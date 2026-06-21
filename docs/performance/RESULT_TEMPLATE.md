# k6 Result Template

Use this template when copying verified k6 results into a report. Do not invent latency, throughput, or improvement rates.

## Run Context

- Executed At:
- Timezone:
- k6 Version:
- Git Commit SHA:
- Git Dirty:
- Scenario:
- Executed: true / false
- Skipped: true / false
- Skip Reason:
- Threshold Failed: true / false
- BASE_URL Host:
- Target Environment: local / docker-local / staging
- Docker Network Mode:
- JVM Options:
- CPU:
- Memory:
- MongoDB Mode:
- Fixture Fingerprint:
- Fixture Courses:
- Fixture Assignments:
- Fixture Enrollments:
- Fixture Submission Statuses:
- Token Role Expected:
- Executor:
- VUs / Arrival Rate:
- Test Duration:
- Request Sleep Seconds:
- Assignment List Ratio:
- Assignment Detail Ratio:
- Warm-up Completed: true / false

## Metrics

Use `not measured` when the scenario did not execute.

| Metric | Value |
| :--- | ---: |
| Business Requests | |
| HTTP Requests | |
| Checks | |
| Success Rate | |
| Error Rate | |
| RPS | |
| P50 | |
| P90 | |
| P95 | |
| P99 | |
| Max Response Time | |
| Dropped Iterations | |
| Submission Status Row Count Max | |

## Failed Checks

- None

## Comparison Rules

Only calculate metric deltas when before and after runs use the same fixture fingerprint, fixture counts, JVM options, MongoDB mode, hardware, k6 version, executor, VUs or arrival rate, duration, request sleep, endpoint ratio, and warm-up status.

Do not label a delta as a performance improvement unless the experiment design separately justifies that wording.
