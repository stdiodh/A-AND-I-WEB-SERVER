# Resume Evidence

> 메인 README로 돌아가기: [README](../README.md)

본 문서는 이력서 문장을 README와 구현 근거, 테스트 결과에 연결하기 위한 증빙 문서입니다.

## 이력서 연결 요약

| 이력서 문장 | README 위치 | 상세 근거 | 검증 상태 |
| :--- | :--- | :--- | :--- |
| A&I 과제 운영 백엔드에서 과제 공개, 테스트케이스 관리, Online Judge 동기화, 제출 결과 반영 흐름을 설계·운영했습니다. | [핵심 기능](../README.md#features-demo) | [Assignment Lifecycle](./api-flows/assignment-lifecycle.md), [Problem Sync](./api-flows/problem-sync.md), [Judge Completed](./api-flows/judge-completed.md) | 코드와 테스트 근거 확인 |
| Assignment 변경 이벤트를 SNS/SQS 기반 problem sync 흐름으로 분리해 WEB-SERVER와 ONLINE-JUDGE-SERVER 간 동기화 경로를 명확히 했습니다. | [Online Judge problem sync](../README.md#feature-problem-sync) | `AssignmentReportTestCaseEventMapper`, `SnsAssignmentReportTestCaseEventPublisher`, [Event-driven Sync](./event-driven-sync.md) | 코드와 설정 근거 확인 |
| OJ의 judge completed event를 소비하고, 처리 대상 이벤트와 비대상 이벤트 처리 기준을 분리해 제출 결과 반영 흐름의 안정성을 높였습니다. | [Judge completed event 소비](../README.md#feature-judge-completed) | `JudgeCompletedEventParser`, `SqsJudgeSubmissionEventConsumer`, `AssignmentSubmissionStatusProjectionService` | parser/consumer/service 테스트 확인 |
| API 요청·오류 로그를 traceId/requestId/statusCode/latencyMs 중심으로 구조화해 운영 중 원인 추적이 가능하도록 정리했습니다. | [구조화 로그와 운영 알림](../README.md#feature-structured-logging) | `V2StructuredLoggingWebFilter`, `V2StructuredLogFormatter`, [Structured Logging](./structured-logging.md) | logging 테스트와 CloudWatch 문서 확인 |

## 바로 사용할 수 있는 문장

- A&I 과제 운영 백엔드에서 과제 공개, 테스트케이스 관리, Online Judge 동기화, 제출 결과 반영 흐름을 설계·운영했습니다.
- Assignment 변경 이벤트를 SNS/SQS 기반 problem sync 흐름으로 분리해 WEB-SERVER와 ONLINE-JUDGE-SERVER 간 동기화 경로를 명확히 했습니다.
- OJ의 `JUDGE_COMPLETED` 이벤트를 소비하고, 비대상 이벤트와 유효 이벤트의 처리 기준을 분리했습니다.
- API 요청·오류 로그를 `traceId`, `requestId`, `statusCode`, `latencyMs` 중심의 JSON으로 구조화했습니다.
- 로컬 검증 기준 Gradle 테스트 188개가 통과했고, JaCoCo XML 기준 line coverage 78.07%, branch coverage 54.71%를 확인했습니다.
- MongoDB 쿼리 튜닝 후보를 repository method와 기존 index 기준으로 분류하고, 대표 데이터셋 부재로 인한 측정 보류 사유를 문서화했습니다.

## 수치 검증 후 사용할 문장

- [x] line coverage 78.07% - `docs/test.md` 기준
- [x] branch coverage 54.71% - `docs/test.md` 기준
- [ ] p95 latency N ms -> N ms
- [ ] query count N회 -> N회
- [ ] alert detection time N분 -> N분
- [ ] SQS 처리량 N배 개선

## 아직 쓰면 안 되는 표현

- 테스트 커버리지 N% 달성이라고만 쓰는 표현. line/branch와 측정 기준을 분리해야 합니다.
- 쿼리 성능 N% 개선
- 채점 결과 반영 시간 N% 단축
- 장애 대응 시간 N분 단축
- SQS 처리량 N배 개선
- CI에서 coverage verification으로 PR을 차단한다는 표현. 현재 CI workflow는 `./gradlew test --no-daemon`만 직접 실행합니다.
- MongoDB 쿼리 튜닝으로 성능을 개선했다는 표현. 현재 대표 데이터셋과 before/after `executionStats`가 없습니다.

## 근거 링크

| 구분 | 위치 | 설명 |
| :--- | :--- | :--- |
| 코드 | `src/main/kotlin/com/example/aandi_post_web_server/course/application/service/CourseQueryService.kt` | 공개 상태 계산과 사용자 조회 testcase 필터링 |
| 코드 | `src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/event/AssignmentReportTestCaseEventMapper.kt` | problem sync 이벤트 타입과 testcase snapshot 매핑 |
| 코드 | `src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/event/SnsAssignmentReportTestCaseEventPublisher.kt` | SNS publish와 FIFO message group 처리 |
| 코드 | `src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/submission/event/JudgeCompletedEventParser.kt` | direct JSON/SNS envelope 파싱과 ignored reason |
| 코드 | `src/main/kotlin/com/example/aandi_post_web_server/user/application/service/ReportUserSyncService.kt` | Auth user upsert/delete와 stale event 처리 |
| 코드 | `src/main/kotlin/com/example/aandi_post_web_server/common/logging/v2/V2StructuredLoggingWebFilter.kt` | v2 요청/응답 구조화 로그 수집 |
| 테스트 | `src/test/kotlin/com/example/aandi_post_web_server` | 188개 테스트 통과 |
| CI | `.github/workflows/ci-test.yml` | push/PR 기준 `./gradlew test --no-daemon` |
| 문서 | [Test](./test.md) | 실제 명령 실행 결과와 coverage |
| 문서 | [Query Tuning](./query-tuning.md) | 쿼리 튜닝 후보와 측정 보류 사유 |
| 문서 | [Event-driven Sync](./event-driven-sync.md) | SNS/SQS 이벤트 흐름 |
| 문서 | [Structured Logging](./structured-logging.md) | 로그/알림 기준 |
| 커밋/PR | [확인 필요] | 이번 문서 정리 commit/PR 생성 후 링크 추가 |
