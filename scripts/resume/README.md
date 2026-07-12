# Resume Metrics Generator

`generate_resume_metrics.py` creates resume-safe metrics from local or committed artifacts only.

```bash
python3 scripts/resume/generate_resume_metrics.py \
  --junit-glob "build/test-results/test/TEST-*.xml" \
  --jacoco-xml "build/reports/jacoco/test/jacocoTestReport.xml" \
  --assignment-scale-report "docs/performance/results/2026-06-29-assignment-scale.json" \
  --ci-summary "docs/metrics/ci-summary.json"
```

기본 생성 결과는 `build/reports/resume-metrics/`에 저장됩니다.
`docs/resume-metrics.md`는 수동으로 정리한 근거 문서이고
`docs/metrics/resume-metrics.json`은 커밋된 snapshot이므로 기본 실행에서 덮어쓰지 않습니다.

`--ci-summary`를 포함한 입력 파일은 선택 사항입니다. 기본 경로에 파일이 없으면 생성기는 실패하지 않고 해당 값을 `측정 필요`로 기록합니다.

Validation-only mode checks the schema file and committed JSON outputs:

```bash
python3 -m py_compile scripts/resume/generate_resume_metrics.py
python3 -m unittest discover -s scripts/resume -p 'test_*.py' -v
python3 scripts/resume/generate_resume_metrics.py --validate-only
```

Each metric includes a top-level confidence and per-value `valueConfidences` so confirmed and unconfirmed numbers are not mixed in one claim.

The generator does not run load tests, trigger GitHub Actions, call the GitHub API, or access AWS, production databases, production APIs, logs, Redis, MongoDB, SNS/SQS, or Discord. Missing artifacts are recorded as `측정 필요` instead of failing generation.
