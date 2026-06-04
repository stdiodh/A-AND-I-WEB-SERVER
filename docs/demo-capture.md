# Demo Capture

> 메인 README로 돌아가기: [README](../README.md)

본 문서는 README에 연결할 GIF/이미지 생성 상태와 수동 촬영 기준을 정리합니다.

## 생성된 데모 파일

| 기능 | 파일 | 생성 방식 | 상태 |
| :--- | :--- | :--- | :--- |
| v2 Swagger/OpenAPI 문서 | `docs/assets/images/swagger-ui-v2.jpg` | Browser screenshot, `SWAGGER_URL=http://localhost:28080` | 생성 |
| 과제 공개 및 테스트케이스 관리 | `docs/assets/gifs/assignment-publish-demo.gif` | [확인 필요] | 미생성 |
| Online Judge problem sync | `docs/assets/gifs/problem-sync-demo.gif` | [확인 필요] | 미생성 |
| Judge completed event 소비 | `docs/assets/gifs/judge-completed-demo.gif` | [확인 필요] | 미생성 |
| Auth user sync | `docs/assets/gifs/auth-user-sync-demo.gif` | [확인 필요] | 미생성 |
| 구조화 로그와 운영 알림 | `docs/assets/images/cloudwatch-log-example.png` | [확인 필요] | 미생성 |

## 자동 생성 시도 결과

| 항목 | 결과 |
| :--- | :--- |
| 로컬 실행 | 부분 성공. `docker compose up -d mongodb`는 `docker` 명령 없음으로 실패했지만, 로컬 MongoDB가 이미 `localhost:27017`에서 실행 중이어서 `SWAGGER_URL=http://localhost:28080 ./gradlew bootRun --args='--server.port=28080'`로 서버 기동 성공. 검증 후 프로세스는 수동 종료했으며, 종료 시 `bootRun` 세션은 exit 143으로 끝났습니다. |
| 브라우저 실행 | 성공. `http://localhost:28080/swagger-ui/index.html?urls.primaryName=report-service-v2` 접속 후 v2 Swagger 화면 확인 |
| GIF 생성 | 미생성 |
| 이미지 생성 | `docs/assets/images/swagger-ui-v2.jpg` 생성 |
| 실패/미생성 원인 | 기능별 성공 흐름 GIF에는 MongoDB seed data, 관리자/사용자 JWT, AWS SNS/SQS 또는 localstack 대체 구성이 필요합니다. 이번 작업에서는 민감정보 없는 Swagger/OpenAPI 화면만 자동 캡처했고, 실제 과제 생성·SQS 메시지 처리 GIF는 데모 데이터와 이벤트 인프라 구성이 없어 만들지 않았습니다. |
| 추가 실패 기록 | 기본 8080 포트와 18080 포트는 이미 사용 중이어서 실패했고, 28080 포트로 재시도했습니다. 최초 Swagger 캡처에는 운영 URL이 표시될 수 있어 `SWAGGER_URL=http://localhost:28080`으로 재기동한 뒤 안전한 화면으로 덮어썼습니다. |

## 수동 촬영 기준

| 기능 | 권장 파일명 | 촬영 범위 | 반드시 보여줄 액션 |
| :--- | :--- | :--- | :--- |
| v2 Swagger/OpenAPI 문서 | `docs/assets/images/swagger-ui-v2.jpg` | Swagger UI | v2 API 문서 제목, 로컬 server URL, 주요 v2 endpoint 그룹 |
| 과제 공개 및 테스트케이스 관리 | `docs/assets/gifs/assignment-publish-demo.gif` | Swagger 또는 API client | 관리자 과제 생성/수정 -> 사용자 과제 조회 -> `PUBLIC` testcase만 응답되는 장면 |
| Online Judge problem sync | `docs/assets/gifs/problem-sync-demo.gif` | API client, application log, AWS console 또는 localstack | 과제 변경 -> `Publishing assignment problem sync event` 로그 -> SNS/SQS 메시지 확인 |
| Judge completed event 소비 | `docs/assets/gifs/judge-completed-demo.gif` | SQS message injection, application log, 관리자 제출 현황 API | `JUDGE_COMPLETED` 메시지 입력 -> projection upsert 로그 -> submitted 상태 조회 |
| Auth user sync | `docs/assets/gifs/auth-user-sync-demo.gif` | SQS message injection, application log, MongoDB 또는 API 응답 | `UserProfileUpdated`/`UserDeleted` 메시지 입력 -> report user upsert/delete 확인 |
| 구조화 로그와 운영 알림 | `docs/assets/images/cloudwatch-log-example.png` | CloudWatch Logs Insights | `trace.traceId`, `http.statusCode`, `http.latencyMs`, `response.error.code` 필드가 보이는 조회 결과 |
| Discord alert | `docs/assets/images/discord-alert-example.png` | Discord test channel | 5xx alert가 allowlist 필드만 포함하는 장면 |

## 민감정보 제거 기준

- JWT, Authorization, Authenticate, salt, cookie, secret 값은 캡처하지 않습니다.
- 실제 userId, publicCode, 이메일, 운영 queue URL, account ID는 데모 값으로 치환합니다.
- private testcase input/output, user submitted code 원문은 보여주지 않습니다.
- CloudWatch screenshot은 traceId/requestId와 status/latency 중심으로 자릅니다.
