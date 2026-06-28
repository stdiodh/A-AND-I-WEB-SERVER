# CI/CD Optimization

## Web CI/CD Remeasurement

Source of truth: `docs/metrics/web-cicd-remeasure.json`

Measurement status: `completed`

The official 5-run measurement batch completed in GitHub Actions run `28313842657`. The table below is copied from `docs/metrics/web-cicd-remeasure.json`; use only rows marked `사용 가능` as improvement evidence.

| 항목 | Before median | After median | 개선율 | Run 수 | 신뢰도 | 사용 여부 |
| :--- | ---: | ---: | ---: | :--- | :--- | :--- |
| CI same-scope total | 140.0s | 109.0s | 22.143% | 5/5 | high | 사용 가능 |
| CI full-gate total | 140.0s | 109.0s | 22.143% | 5/5 | high | 사용 가능 |
| Backend test | 121.0s | 109.0s | 9.917% | 5/5 | high | 사용 가능 |
| Performance assets | 22.0s | 20.0s | 9.091% | 5/5 | high | 사용 가능 |
| Build JAR | 52.0s | 63.0s | -21.154% | 5/5 | low | 사용 불가: 개선 없음 |
| CD dry-run full path | 211.0s | 82.0s | 61.137% | 5/5 | high | 사용 가능 |
| CD image build only | 161.0s | 29.0s | 81.988% | 5/5 | high | 사용 가능 |
| CD image build warm cache | N/A | N/A | N/A | 0/0 | low | 사용 불가: warm cache 측정 없음 |

## Measurement Notes

- Usable rows have at least five successful before/after runs and zero failures.
- No failed, cancelled, or skipped run was counted as a successful sample.
- CD image jobs are configured with `push: false`.
- AWS, ECR, EC2 SSH, and production URL paths were not used.
- BuildKit cache is configured for CD image jobs, but cache-hit improvement is not claimed because `cd_image_build_warm_cache` has no completed before/after samples.
