# A-AND-I-WEB-SERVER

본 프로젝트는 A&I 동아리의 과제 운영을 담당하는 `Kotlin / Spring Boot WebFlux` 기반 백엔드 서버입니다. 코스별 과제 공개, 테스트케이스 관리, Online Judge 문제 동기화, 채점 완료 결과 반영, Auth 사용자 동기화, 구조화 로그를 한 흐름으로 관리합니다.

> 상세 구현 근거는 [Docs Index](./docs/README.md)와 [Resume Evidence](./docs/resume-evidence.md)에서 확인할 수 있습니다.

## 🧭 목차 (Table of Contents)

- [프로젝트 목표와 다짐](#goals)
- [문제 정의와 해결 방향](#problem-approach)
- [시스템 아키텍처](#architecture)
- [핵심 기능 및 동작 화면](#features-demo)
- [동작 과정](#how-it-works)
- [테스트 및 검증](#test-verification)
- [개발 로그 및 트러블 슈팅](#troubleshooting-log)
- [기술 스택](#tech-stack)
- [로컬 실행 방법](#getting-started)
- [상세 문서](#docs)
- [Resume Highlights](#resume-highlights)
- [회고와 개선점](#retrospective-next-step)

<a id="goals"></a>
## 🏃‍♂️ 프로젝트 목표와 다짐 (Goals)

### 프로젝트 정보

- 개발 기간: **2025년 03월 07일 ~ 진행 중**
- 저장소 역할: **A&I Back-End Repository**

| Front-End | Back-End |
| :---: | :---: |
| <img src="https://github.com/user-attachments/assets/3e22107e-3e30-44d5-8d4a-61cfbab8eac2" width="100"/> | <img src="https://github.com/user-attachments/assets/a51e908a-f9ca-4819-a36a-5f26da14a3aa" width="100"/> |
| [Han Sang Wook](https://github.com/SangWook16074) | [Hood](https://github.com/stdiodh) |

### 목표

1. **Assignment Operation:** 과제 생성, 공개, 조회, 테스트케이스 관리, 복사 흐름을 운영 관점에서 추적 가능하게 만듭니다.
2. **Event-driven Sync:** 과제 변경, 채점 완료, 사용자 변경을 `AWS SNS/SQS` 기반 이벤트 흐름으로 분리합니다.
3. **Operational Evidence:** 구조화 로그, CloudWatch, Discord alert 연계 기준을 문서화해 운영 중 원인 추적 근거를 남깁니다.

<a id="problem-approach"></a>
## 🧩 문제 정의와 해결 방향 (Problem & Approach)

| 문제 | 해결 방향 | 근거 |
| :--- | :--- | :--- |
| 공개 전 과제와 비공개 테스트케이스가 수강생에게 노출되면 안 됩니다. | `startAt` 기준 공개 상태를 계산하고 사용자 응답에는 `PUBLIC` 테스트케이스만 포함합니다. | `CourseQueryService` |
| 과제 변경과 Online Judge 문제 데이터가 어긋날 수 있습니다. | 테스트케이스 snapshot을 `PROBLEM_CREATED`, `PROBLEM_UPDATED`, `PROBLEM_DELETED` 이벤트로 발행합니다. | `AssignmentReportTestCaseEventMapper`, `SnsAssignmentReportTestCaseEventPublisher` |
| 채점 결과를 관리자 제출 현황에 반영해야 합니다. | `JUDGE_COMPLETED` 이벤트를 SQS로 소비하고 제출 상태 projection을 upsert합니다. | `SqsJudgeSubmissionEventConsumer`, `AssignmentSubmissionStatusProjectionService` |
| Auth 서버의 사용자 변경을 과제 운영 서버에 반영해야 합니다. | `UserProfileUpdated`, `UserDeleted` 이벤트를 SQS로 소비해 report user 데이터를 upsert/delete합니다. | `SqsUserEventConsumer`, `ReportUserSyncService` |
| 운영 장애를 추적하려면 요청 단위 로그가 필요합니다. | `traceId`, `requestId`, `statusCode`, `latencyMs` 중심의 JSON 로그를 stdout으로 남기고 CloudWatch에서 수집합니다. | `V2StructuredLoggingWebFilter`, `docker-compose.prod.yml` |

<a id="architecture"></a>
## 🏗️ 시스템 아키텍처 (System Architecture)

```text
Client / Admin
  -> A&I Web Server
      -> MongoDB
      -> AWS SNS: assignment problem sync event
      -> AWS SQS: auth user sync event
      -> AWS SQS: judge completed event
  -> Online Judge Server
  -> CloudWatch Logs -> Discord Alert Lambda/Webhook
```

- `MongoDB`는 코스, 수강, 과제, 테스트케이스, 사용자 동기화 데이터, 제출 상태 projection을 저장합니다.
- `AWS SNS/SQS`는 WEB, AUTH, OJ 서버 간 데이터 동기화 경로를 분리합니다.
- 운영 로그는 애플리케이션이 JSON 한 줄로 stdout에 출력하고, Docker `awslogs` logging driver가 CloudWatch Logs로 전달합니다.

자세한 구조는 [Architecture](./docs/architecture.md)에서 확인할 수 있습니다.

<a id="features-demo"></a>
## 🚀 핵심 기능 및 동작 화면 (Features & Demo)

> 자동 생성된 공통 이미지: [v2 Swagger/OpenAPI 화면](./docs/assets/images/swagger-ui-v2.jpg)
> 기능별 GIF는 아직 생성하지 않았으며, 필요한 데모 데이터와 수동 촬영 기준은 [Demo Capture](./docs/demo-capture.md)에 기록했습니다.

<a id="feature-assignment-lifecycle"></a>
### 과제 공개 및 테스트케이스 관리

관리자는 코스별 과제를 생성, 수정, 삭제하고 테스트케이스 visibility를 관리합니다. 수강생 조회에서는 `startAt`이 지난 과제만 공개 상태로 계산되며, `PUBLIC` 테스트케이스만 응답합니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/gifs/assignment-publish-demo.gif`
> - 대체 이미지: `docs/assets/images/assignment-publish-result.png`
> - 촬영 범위: 관리자 과제 생성/수정 요청, 사용자 과제 조회 응답, `PUBLIC` 테스트케이스 필터링 결과

> **핵심 구현 포인트**
> - `CourseQueryService`가 사용자 접근 권한과 공개 상태를 함께 검증합니다.
> - `AssignmentTestCaseVisibility.EXCLUDED`는 OJ sync payload에서도 제외됩니다.

자세한 흐름은 [Assignment Lifecycle](./docs/api-flows/assignment-lifecycle.md)에서 확인할 수 있습니다.

<a id="feature-problem-sync"></a>
### Online Judge problem sync

과제 생성, 수정, 삭제 이후 서버는 최신 테스트케이스 snapshot을 problem sync 이벤트로 발행합니다. 이벤트 발행이 꺼진 로컬 환경에서는 noop publisher가 로그만 남기고, 운영 환경에서는 SNS topic ARN 설정을 요구합니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/gifs/problem-sync-demo.gif`
> - 대체 이미지: `docs/assets/images/problem-sync-flow.png`
> - 촬영 범위: 테스트케이스 저장, problem sync 이벤트 로그, SNS/SQS 전달 확인

> **핵심 구현 포인트**
> - 이벤트 타입은 `PROBLEM_CREATED`, `PROBLEM_UPDATED`, `PROBLEM_DELETED`입니다.
> - FIFO topic ARN이면 `problemId`를 `messageGroupId`로 사용합니다.

자세한 흐름은 [Problem Sync](./docs/api-flows/problem-sync.md)에서 확인할 수 있습니다.

<a id="feature-judge-completed"></a>
### Judge completed event 소비

Online Judge Server의 채점 완료 이벤트는 SQS consumer가 읽고, 제출 상태 projection에 저장됩니다. 관리자는 projection과 수강생 목록을 조합한 제출/미제출 현황을 조회합니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/gifs/judge-completed-demo.gif`
> - 대체 이미지: `docs/assets/images/judge-completed-flow.png`
> - 촬영 범위: SQS 메시지 주입, consumer 처리 로그, 관리자 제출 현황 조회

> **핵심 구현 포인트**
> - direct JSON body와 SNS envelope body를 모두 지원합니다.
> - `JUDGE_COMPLETED`가 아닌 메시지는 reason 로그를 남기고 삭제합니다.

자세한 흐름은 [Judge Completed](./docs/api-flows/judge-completed.md)에서 확인할 수 있습니다.

<a id="feature-auth-user-sync"></a>
### Auth user sync

AUTH 서버의 사용자 변경 이벤트를 소비해 과제 운영 서버의 사용자 표시 정보를 최신화합니다. `updatedAt` 또는 `occurredAt` 기준으로 오래된 이벤트는 반영하지 않습니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/gifs/auth-user-sync-demo.gif`
> - 대체 이미지: `docs/assets/images/auth-user-sync-flow.png`
> - 촬영 범위: Auth user event body, SQS consumer 로그, report user upsert/delete 결과

> **핵심 구현 포인트**
> - `UserProfileUpdated`는 upsert, `UserDeleted`는 delete로 처리합니다.
> - direct JSON body와 SNS envelope body를 모두 파싱합니다.

자세한 흐름은 [Auth User Sync](./docs/api-flows/auth-user-sync.md)에서 확인할 수 있습니다.

<a id="feature-structured-logging"></a>
### 구조화 로그와 운영 알림

v2 API 요청은 `traceId`, `requestId`, `statusCode`, `latencyMs`, error fields를 포함한 JSON 로그로 남습니다. CloudWatch Logs에서는 5xx, 지연 요청, traceId 단위 조회가 가능하고 Discord alert는 allowlist 필드만 전달하는 기준으로 문서화했습니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/gifs/structured-logging-demo.gif`
> - 대체 이미지: `docs/assets/images/cloudwatch-log-example.png`, `docs/assets/images/discord-alert-example.png`
> - 촬영 범위: v2 API 요청, stdout JSON 로그, CloudWatch Logs Insights 조회, Discord alert payload

> **핵심 구현 포인트**
> - password, token, authorization, private testcase, user submitted code 원문은 마스킹하거나 출력하지 않습니다.
> - CloudWatch 전송은 앱 내부 appender가 아니라 Docker `awslogs` driver가 담당합니다.

자세한 기준은 [Structured Logging](./docs/structured-logging.md)에서 확인할 수 있습니다.

<a id="how-it-works"></a>
## 🔄 동작 과정 (How It Works)

| 흐름 | 요약 | 상세 문서 |
| :--- | :--- | :--- |
| Assignment Operation | 관리자 과제 변경 -> 공개 상태 계산 -> 사용자 과제 조회 | [Assignment Lifecycle](./docs/api-flows/assignment-lifecycle.md) |
| OJ Problem Sync | WEB -> SNS -> OJ 구독 SQS -> 문제/testcase 동기화 | [Problem Sync](./docs/api-flows/problem-sync.md) |
| Judge Completed Event | OJ -> SNS FIFO -> SQS -> WEB consumer -> projection upsert | [Judge Completed](./docs/api-flows/judge-completed.md) |
| Auth User Sync | AUTH -> SNS -> SQS -> WEB consumer -> report user upsert/delete | [Auth User Sync](./docs/api-flows/auth-user-sync.md) |
| Structured Logging | v2 API -> stdout JSON -> CloudWatch Logs -> Discord alert | [Structured Logging](./docs/structured-logging.md) |

<a id="test-verification"></a>
## 🧪 테스트 및 검증 (Test & Verification)

2026년 06월 04일 KST 기준 로컬에서 지정 명령을 실행했습니다.

| 항목 | 결과 |
| :--- | :--- |
| `./gradlew clean test` | 성공, 188 tests / 0 failures / 0 errors / 0 skipped |
| `./gradlew jacocoTestReport` | 성공 |
| `./gradlew jacocoTestCoverageVerification` | 성공 |
| `./gradlew check` | 성공 |
| JaCoCo line coverage | 2,289 / 2,932 = 78.07% |
| JaCoCo branch coverage | 761 / 1,391 = 54.71% |
| CI test task | `.github/workflows/ci-test.yml`에서 `./gradlew test --no-daemon` 실행 |

자세한 결과와 주의사항은 [Test](./docs/test.md)에서 확인할 수 있습니다.

<a id="troubleshooting-log"></a>
## 📚 개발 로그 및 트러블 슈팅 (Troubleshooting Log)

| 문서 | 핵심 내용 |
| :--- | :--- |
| [SQS Envelope Format](./docs/troubleshooting/sqs-envelope-format.md) | direct JSON body와 SNS envelope body 처리 기준 |
| [Assignment Copy](./docs/troubleshooting/assignment-copy.md) | origin/fingerprint 중복 방지와 복사 후 problem sync |
| [Event Field Consistency](./docs/troubleshooting/event-field-consistency.md) | eventType, problemId/publicCode 등 이벤트 필드 일관성 기준 |
| [Discord Alert From CloudWatch](./docs/discord-alert-from-cloudwatch.md) | CloudWatch subscription filter와 Discord alert payload 기준 |

<a id="tech-stack"></a>
## 🛠️ 기술 스택 (Tech Stack)

| 구분 | 기술 |
| :--- | :--- |
| Language | `Kotlin 1.9.25`, `Java 21` |
| Framework | `Spring Boot 3.4.3`, `Spring WebFlux` |
| Database | `MongoDB`, `Spring Data MongoDB Reactive` |
| Security | `Spring Security`, `OAuth2 Resource Server`, `JWT HS256` |
| Event | `AWS SNS`, `AWS SQS`, AWS SDK for Java v2 |
| API Docs | `SpringDoc OpenAPI`, Swagger UI |
| Build/Test | `Gradle Kotlin DSL`, `JUnit`, `Kotest`, `JaCoCo` |
| Deploy/Ops | `Docker`, `Docker Compose`, `GitHub Actions`, `Amazon ECR`, `EC2`, `CloudWatch Logs` |

<a id="getting-started"></a>
## ⚙️ 로컬 실행 방법 (Getting Started)

### 사전 준비

- JDK 21
- Docker & Docker Compose
- MongoDB는 Docker Compose로 실행 가능

### 환경 변수

로컬 기본값은 `src/main/resources/application.yml`에 정의되어 있습니다. 필요한 값만 프로젝트 루트의 `.env`에서 덮어씁니다.

```properties
MONGO_DB_URL=mongodb://localhost:27017/aandi
SWAGGER_URL=http://localhost:8080
AUTH_ISSUER_URI=http://localhost:9000
AUTH_AUDIENCE=aandi-gateway
AUTH_JWT_SECRET=<LOCAL_DEV_JWT_SECRET_MIN_32_BYTES>
```

### 실행

```bash
docker compose up -d mongodb
./gradlew bootRun
```

서버까지 Docker Compose로 실행할 수도 있습니다.

```bash
docker compose up -d
```

### 확인

```text
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/actuator/health/readiness
```

<a id="docs"></a>
## 📖 상세 문서 (Docs)

- [Docs Index](./docs/README.md)
- [Architecture](./docs/architecture.md)
- [Event-driven Sync](./docs/event-driven-sync.md)
- [Structured Logging](./docs/structured-logging.md)
- [Deployment](./docs/deployment.md)
- [Test](./docs/test.md)
- [Performance Measurement](./docs/performance-measurement.md)
- [Query Tuning](./docs/query-tuning.md)
- [Resume Evidence](./docs/resume-evidence.md)
- [Demo Capture](./docs/demo-capture.md)

<a id="resume-highlights"></a>
## 🎯 Resume Highlights

- A&I 과제 운영 백엔드에서 과제 공개, 테스트케이스 관리, Online Judge 동기화, 제출 결과 반영 흐름을 설계·운영했습니다.
- Assignment 변경 이벤트를 SNS/SQS 기반 problem sync 흐름으로 분리해 WEB-SERVER와 ONLINE-JUDGE-SERVER 간 동기화 경로를 명확히 했습니다.
- OJ의 `JUDGE_COMPLETED` 이벤트를 소비하고, 처리 대상 이벤트와 비대상 이벤트 처리 기준을 분리했습니다.
- API 요청·오류 로그를 `traceId`, `requestId`, `statusCode`, `latencyMs` 중심으로 구조화했습니다.

근거와 이력서에 쓰면 안 되는 표현은 [Resume Evidence](./docs/resume-evidence.md)에 정리했습니다.

<a id="retrospective-next-step"></a>
## 🧭 회고와 개선점 (Retrospective & Next Step)

이 서버는 과제 데이터를 저장하는 기능을 넘어, 과제 운영 이벤트와 외부 채점 시스템, 운영 로그를 연결합니다. 이번 문서 정리는 README를 3분 안에 읽히는 랜딩 페이지로 압축하고, 구현 근거와 검증 결과를 docs로 분리하는 데 집중했습니다.

현재 before/after 성능 측정값과 기능별 데모 GIF는 없습니다. 이번 작업에서는 민감정보 없는 v2 Swagger/OpenAPI 이미지를 생성했고, 다음 개선은 실제 데모 데이터 기반 API 응답 또는 CloudWatch 로그 화면을 보강하는 것입니다.
