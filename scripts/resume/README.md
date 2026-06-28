# Resume Metrics Generator

`generate_resume_metrics.py` creates resume-safe metrics from local or committed artifacts only.

```bash
python3 scripts/resume/generate_resume_metrics.py \
  --junit-glob "build/test-results/test/TEST-*.xml" \
  --jacoco-xml "build/reports/jacoco/test/jacocoTestReport.xml" \
  --assignment-scale-report "docs/performance/results/2026-06-29-assignment-scale.json" \
  --ci-summary "docs/metrics/ci-summary.json" \
  --out-json "docs/metrics/resume-metrics.json" \
  --out-md "docs/resume-metrics.md"
```

Validation-only mode checks the schema file and committed JSON outputs:

```bash
python3 -m py_compile scripts/resume/generate_resume_metrics.py
python3 scripts/resume/generate_resume_metrics.py --validate-only
```

Each metric includes a top-level confidence and per-value `valueConfidences` so confirmed and unconfirmed numbers are not mixed in one claim.

The generator does not run load tests, trigger GitHub Actions, call the GitHub API, or access AWS, production databases, production APIs, logs, Redis, MongoDB, SNS/SQS, or Discord. Missing artifacts are recorded as `측정 필요` instead of failing generation.
