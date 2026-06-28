# Resume Metrics

이력서에 사용할 수 있는 수치와 근거 artifact를 한 문서로 모읍니다.

## Sources

- Web CI/CD: `docs/metrics/web-cicd-remeasure.json`
- Assignment scale: `docs/performance/results/2026-06-29-assignment-scale.json`
- Resume metrics generator output: `docs/metrics/resume-metrics.json`

## Web CI/CD

Measurement status: `completed`

Use only metrics marked `사용 가능` in `docs/metrics/web-cicd-remeasure.json`.

Approved sentence candidates:

- 같은 검증 범위 기준으로 GitHub Actions CI를 `backend-test`와 `performance-assets` job으로 분리하고 candidate critical path를 집계해 same-scope CI median을 140.0s에서 109.0s로 22.143% 단축
- GitHub Actions full-gate CI critical path 기준으로 median을 140.0s에서 109.0s로 22.143% 단축
- GitHub Actions backend test와 JaCoCo coverage verification을 단일 Gradle invocation으로 묶어 backend test median을 121.0s에서 109.0s로 9.917% 단축
- k6 version을 고정하고 performance asset 검증을 별도 job으로 분리해 performance assets median을 22.0s에서 20.0s로 9.091% 단축
- 운영 배포가 아닌 CD dry-run full path에서 prebuilt JAR 기반 image build 경로를 사용해 dry-run median을 211.0s에서 82.0s로 61.137% 단축
- 운영 배포가 아닌 CD dry-run image build 단계에서 prebuilt JAR 기반 `Dockerfile.runtime`을 사용해 image build median을 161.0s에서 29.0s로 81.988% 단축

Do not use:

- Build JAR timing as an improvement claim; the completed metric worsened from 52.0s to 63.0s.
- CD image build warm-cache claims; no completed warm-cache before/after samples exist.
- Production deployment time reduction claims; CD measurements used dry-run image build paths with no deploy, AWS, ECR, SSH, or docker push.
- Cache-hit improvement claims; cache-hit evidence was not marked usable in the source JSON.

## Assignment Scale

이 수치는 운영 환경 최대 처리량이 아니라 로컬/고정 부하 회귀 검증 기준입니다.

| Scenario | Fixture | Target RPS | P95 | P99 | HTTP failed | Checks | Throughput | Iterations | Dropped |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| assignment-1000 | 1000 | 50 | 125.51 ms | 133.48 ms | 0.00% | 100.00% | 49.96 req/s | 6000 | 0 |
| assignment-300 | 300 | 50 | 129.03 ms | 136.78 ms | 0.00% | 100.00% | 49.95 req/s | 6000 | 0 |

Approved sentence candidates:

- 로컬 고정 부하 회귀 기준에서 1000개 과제 fixture P95 125.51 ms, P99 133.48 ms / 300개 과제 fixture P95 129.03 ms, P99 136.78 ms, HTTP failure 0.00%, dropped iterations 0을 관리
- JUnit 277개 테스트와 JaCoCo line 85.04%, branch 62.59%를 로컬 리포트에서 자동 집계

Do not use:

- Assignment scale 결과를 운영 최대 처리량이나 production 성능으로 표현하지 않습니다.
- before/after 조건이 동일하지 않으므로 assignment scale latency 개선율을 계산하지 않습니다.
- `측정 필요`, `확인 필요`, `사용 비추천`으로 표시된 generator 항목은 이력서 수치로 사용하지 않습니다.
