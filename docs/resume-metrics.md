# Resume Metrics

이력서에 사용할 수 있는 수치와 근거 artifact를 한 문서로 모읍니다.

## Sources

- Web CI/CD: `docs/metrics/web-cicd-remeasure.json`
- Assignment scale: `docs/performance/results/2026-06-29-assignment-scale.json`
- Assignment list before/after: `docs/performance/results/2026-07-09-assignment-list-before-after.json`
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

## Assignment List Before/After

이 수치는 과제 목록 조회만 대상으로 한 local fixed-load before/after 측정입니다. 운영 최대 처리량이나 MongoDB command count 감소로 표현하지 않습니다.

| Metric | Before | After | Condition | Source |
| :--- | ---: | ---: | :--- | :--- |
| Assignment list P95 | 9.297 ms | 7.565 ms | 30 assignments, list-only, 100 RPS, 2분 x 3회 | `docs/performance/results/2026-07-09-assignment-list-before-after.json` |
| Assignment list P99 | 11.512 ms | 9.546 ms | 30 assignments, list-only, 100 RPS, 2분 x 3회 | `docs/performance/results/2026-07-09-assignment-list-before-after.json` |
| HTTP failure / checks / dropped | 0.00% / 100.00% / 0 | 0.00% / 100.00% / 0 | same local fixed-load run | `docs/performance/results/2026-07-09-assignment-list-before-after.json` |

Approved sentence candidates:

- 30개 과제 list-only local fixed-load 조건에서 과제 목록 API P95를 9.297 ms에서 7.565 ms로 측정하고, HTTP failure 0.00%, checks 100.00%, dropped iterations 0을 확인
- 과제 목록 조회의 반복 child repository 조회를 batch 조회로 변경하고, 30개 과제 기준 service-level child repository call을 60회에서 2회로 감소

Do not use:

- 이 결과를 운영 최대 처리량, production latency, MongoDB command count 감소로 표현하지 않습니다.
- k6 report의 `gitCommitSha=6682802`는 runner repo 기준이므로 서버 commit `9b678b6 -> 642cfd3`와 분리해서 설명합니다.

## Event-driven Structure

Web Server와 Online Judge Server 사이의 이벤트 구조는 현재 repo에서 다음 범위까지 확인했습니다.

| 흐름 | 확인된 동작 | 근거 | 사용 여부 |
| :--- | :--- | :--- | :--- |
| Assignment problem sync publish | assignment 생성/수정/삭제 후 `PROBLEM_CREATED`, `PROBLEM_UPDATED`, `PROBLEM_DELETED` snapshot을 SNS로 발행 | `AssignmentReportTestCaseEvent.kt`, `AssignmentReportTestCaseEventMapper.kt`, `SnsAssignmentReportTestCaseEventPublisher.kt`, `CourseCommandService.kt` | 사용 가능 |
| Problem sync schema | `eventType`, `problemId`, `testCases[]`, `caseId`, `input`, `output` | `AssignmentReportTestCaseEvent.kt` | 사용 가능 |
| Judge completed consume | raw JSON 또는 SNS envelope의 `JUDGE_COMPLETED`를 파싱해 projection upsert 후 SQS message 삭제 | `JudgeCompletedEventParser.kt`, `SqsJudgeSubmissionEventConsumer.kt`, `SqsJudgeSubmissionEventConsumerTest` | 사용 가능 |
| Submission projection | `assignmentId + publicCode` unique index 기반으로 projection 저장, 최고 점수 기준 필드 갱신 | `AssignmentSubmissionStatusProjection.kt`, `AssignmentSubmissionStatusProjectionService.kt`, `AssignmentSubmissionStatusProjectionServiceTest` | 사용 가능 |
| Admin read path | 관리자 제출 현황은 Online Judge 동기 호출 대신 MongoDB projection과 enrollment를 join | `AdminAssignmentSubmissionStatusesV2Service.kt`, `CourseAdminAssignmentSubmissionStatusesV2Controller.kt`, `README.md` | 사용 가능 |

Approved sentence candidates:

- 과제 변경 시 Online Judge problem snapshot을 SNS event로 발행하고, `JUDGE_COMPLETED` SQS event를 MongoDB submission projection으로 upsert하는 구조를 구현
- 관리자 제출 현황 조회를 Online Judge 동기 호출 대신 `assignmentId + publicCode` projection read path로 분리

Do not use:

- Event-driven 구조를 고가용성 보장, exactly-once 처리, 장애 무손실 처리로 표현하지 않습니다.
- SNS/SQS DLQ redrive policy는 애플리케이션 코드가 아니라 실제 AWS Queue 속성에서 확인해야 하므로 현재 repo 기준으로 성과 수치로 쓰지 않습니다.
- Assignment problem sync가 Online Judge API 동기 호출을 제거한 구조라는 점은 말할 수 있지만, SNS publish 자체는 command path에서 완료를 기다리므로 전체 요청이 완전 비동기라고 표현하지 않습니다.

## 확인 필요

| 항목 | 현재 상태 | 확인 방법 | README 포함 |
| :--- | :--- | :--- | :--- |
| MongoDB command count 감소 | 확인 필요 | command listener 또는 profiler 기반 before/after 측정 | 제외 |
| 300/1000 assignment list-only before/after latency | 확인 필요 | 30개 fixture 외에 큰 fixture에서 같은 commit pair로 재측정 | 제외 |
| SNS/SQS DLQ redrive policy | 확인 필요 | AWS console/IaC/queue attribute 확인 | 제외 |
| Outbox 기반 no-lost-event | 미구현 | outbox table/collection 및 publisher relay 구현 확인 | 제외 |
| eventId deduplication/exactly-once | 미구현 | eventId, processed-event store, dedup test 확인 | 제외 |
| Online Judge consumer | 확인 필요 | Online Judge Server repo에서 SQS consumer 확인 | 제외 |
| CloudWatch 운영 지표 | 확인 필요 | log group, alarm, metric filter, sample logs 확인 | 제외 |
