# A&I Web Server

> 본 프로젝트는 A&I 동아리의 코스 운영, 과제 공개, 테스트케이스 관리, Online Judge 연동을 담당하는 `Kotlin Spring Boot WebFlux` 기반 백엔드 서버입니다.
> 서버는 `MongoDB`에 코스/과제/수강/사용자 동기화 데이터를 저장하고, `AWS SNS/SQS`로 문제 동기화와 채점 완료 이벤트를 연결합니다.

## 🧭 목차 (Table of Contents)

- [🏃‍♂️ 프로젝트 목표와 다짐 (Goals)](#-프로젝트-목표와-다짐-goals)
- [🧩 문제 정의와 해결 방향 (Problem & Approach)](#-문제-정의와-해결-방향-problem--approach)
- [🏗️ 시스템 아키텍처 (System Architecture)](#-시스템-아키텍처-system-architecture)
- [🚀 핵심 기능 및 동작 화면 (Features & Demo)](#-핵심-기능-및-동작-화면-features--demo)
- [🔄 동작 과정 (How It Works)](#-동작-과정-how-it-works)
- [🧪 테스트 및 검증 (Test & Verification)](#-테스트-및-검증-test--verification)
- [📚 개발 로그 및 트러블 슈팅 (Troubleshooting Log)](#-개발-로그-및-트러블-슈팅-troubleshooting-log)
- [🛠️ 기술 스택 (Tech Stack)](#-기술-스택-tech-stack)
- [⚙️ 로컬 실행 방법 (Getting Started)](#-로컬-실행-방법-getting-started)
- [📖 상세 문서 (Docs)](#-상세-문서-docs)
- [🧭 회고와 개선점 (Retrospective & Next Step)](#-회고와-개선점-retrospective--next-step)

## 🏃‍♂️ 프로젝트 목표와 다짐 (Goals)

이 저장소는 A&I 서비스의 Back-End Repository입니다.
프론트엔드가 코스와 과제를 화면에 보여주는 데 필요한 API를 제공하고, 과제 테스트케이스가 Online Judge Server의 문제 데이터로 이어지는 흐름을 책임집니다.

이번 프로젝트에서 중점으로 둔 기준은 `운영 흐름 이해`, `외부 시스템 연동`, `문서화 가능한 설계`입니다.

1. **운영 흐름 이해:** 과제 생성부터 공개, 제출 결과 반영까지 서버가 맡는 구간을 코드와 문서로 추적합니다.
2. **외부 시스템 연동:** 테스트케이스 변경과 채점 완료 이벤트를 `SNS/SQS` 기반 비동기 흐름으로 연결합니다.
3. **문서화 가능한 설계:** README는 전체 그림을 빠르게 보여주고, API/Event 상세는 `docs/`로 분리합니다.

## 🧩 문제 정의와 해결 방향 (Problem & Approach)

A&I 과제 서비스는 단순 CRUD보다 운영 흐름이 더 중요합니다.
관리자는 코스와 과제를 만들고, 사용자는 공개된 과제만 조회하며, Online Judge Server는 서버가 발행한 문제 동기화 이벤트와 채점 완료 이벤트를 기준으로 움직입니다.

| 문제 | 해결 방향 | 확인한 근거 |
| :--- | :--- | :--- |
| 수강생에게 공개 전 과제가 노출되면 안 됨 | `startAt` 기준으로 사용자 조회 응답의 공개 상태를 계산하고, 공개 테스트케이스만 반환 | `CourseQueryService` |
| 관리자 과제 운영이 반복됨 | 코스/수강/과제 관리자 API와 과제 복사 API 제공 | `CourseAdminV2Controller`, `AssignmentCopyService` |
| 테스트케이스 변경을 OJ 문제와 맞춰야 함 | `EXCLUDED`를 제외한 테스트케이스 snapshot을 `PROBLEM_CREATED/UPDATED/DELETED` 이벤트로 발행 | `AssignmentReportTestCaseEventMapper` |
| OJ 채점 완료를 관리자 화면에서 봐야 함 | `JUDGE_COMPLETED` 이벤트를 SQS로 소비하고 projection에 반영 | `SqsJudgeSubmissionEventConsumer`, `AssignmentSubmissionStatusProjectionService` |
| 운영 장애 추적이 필요함 | v2 API 구조화 로그를 stdout JSON으로 남기고 CloudWatch로 수집 | `docs/logging-v2.md`, `docker-compose.prod.yml` |

## 🏗️ 시스템 아키텍처 (System Architecture)

> [이미지 필요] `docs/assets/images/architecture.png`에 전체 시스템 흐름 이미지를 추가합니다.
> 촬영/제작 기준은 [Demo Assets Guide](./docs/assets/README.md)를 참고합니다.

```text
Client
  -> A&I Web Server
      -> MongoDB
      -> AWS SNS: assignment problem sync event
      -> AWS SQS: user sync event, judge completion event
  -> Online Judge Server
```

- **Client:** JWT를 포함해 코스/과제 조회와 관리자 요청을 보냅니다.
- **A&I Web Server:** `v1`, `v2` API를 제공하고, 코스/과제/수강/제출 projection 데이터를 관리합니다.
- **MongoDB:** 코스, 주차, 과제, 요구사항, 테스트케이스, 수강 정보, 사용자 동기화 데이터, 제출 상태 projection을 저장합니다.
- **AWS SNS/SQS:** 테스트케이스 문제 동기화 이벤트를 발행하고, 사용자/채점 완료 이벤트를 소비합니다.
- **Operations:** Docker, GitHub Actions, ECR, EC2, CloudWatch Logs 기반 운영 문서가 포함되어 있습니다.

## 🚀 핵심 기능 및 동작 화면 (Features & Demo)

### 1. 코스별 과제 조회와 공개 상태 계산

수강 중인 사용자는 자신에게 접근 권한이 있는 코스와 과제를 조회합니다.
사용자 조회에서는 `startAt` 이전 과제를 `DRAFT`로 계산하고, 공개된 과제의 `PUBLIC` 테스트케이스만 응답합니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/demo-assignment-flow.gif`
> - 보여줄 흐름: 로그인 사용자 요청 -> 코스 목록 조회 -> 과제 목록 조회 -> 과제 상세 확인
> - 대체 기준: 프론트엔드 화면이 없으면 Swagger UI 또는 API Client로 대체

> 자세한 흐름은 [과제 공개 및 조회 흐름](./docs/api-flows/assignment-publish-flow.md)에서 확인할 수 있습니다.

### 2. 관리자 코스/수강/과제 운영

관리자는 코스 생성, 수강생 등록, 과제 생성/수정/삭제, 과제 복사를 수행합니다.
과제 생성/수정/삭제는 저장된 테스트케이스 snapshot을 기준으로 OJ 문제 동기화 이벤트까지 이어집니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/demo-admin-assignment-flow.gif`
> - 보여줄 흐름: 관리자 토큰 요청 -> 코스 선택 -> 과제 생성 또는 복사 -> 응답 확인

> 자세한 흐름은 [관리자 코스/과제 관리 흐름](./docs/api-flows/admin-course-assignment-flow.md)에서 확인할 수 있습니다.

### 3. 테스트케이스 Online Judge 동기화

관리자가 과제를 생성하거나 수정하면 서버는 `EXCLUDED` 테스트케이스를 제외한 최종 snapshot을 OJ 문제 이벤트로 발행합니다.
삭제 시에는 같은 `problemId`에 대해 빈 테스트케이스 배열을 담은 `PROBLEM_DELETED` 이벤트를 보냅니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/demo-testcase-oj-sync.gif`
> - 보여줄 흐름: 테스트케이스 저장 -> problem sync 이벤트 로그 확인 -> SNS/SQS 전달 확인

> 자세한 흐름은 [테스트케이스 OJ 동기화 흐름](./docs/api-flows/testcase-oj-sync-flow.md)에서 확인할 수 있습니다.

### 4. Judge Completion 이벤트 소비와 제출 현황 projection

Online Judge Server가 채점 완료 이벤트를 보내면 서버는 SQS 메시지를 소비하고 제출 상태 projection을 갱신합니다.
관리자 제출 현황 API는 코스 수강생 목록과 projection을 조합해 제출/미제출 상태를 반환합니다.

> **🎬 동작 화면**
> - GIF 위치: `docs/assets/demo-judge-completion-flow.gif`
> - 보여줄 흐름: SQS 메시지 주입 -> consumer 로그 확인 -> 관리자 제출 현황 조회

> 자세한 흐름은 [Judge Completion 이벤트 소비 흐름](./docs/api-flows/judge-completion-consumer-flow.md)에서 확인할 수 있습니다.

### 5. v2 인증, 공통 응답, 구조화 로깅

`v2` API는 `Authenticate` 또는 `Authorization` Bearer 토큰을 사용하고, `deviceOS`, `timestamp`, 선택적 `salt` 헤더를 검증합니다.
응답은 `success`, `data`, `error`, `timestamp` 구조로 통일되며, 운영 로그는 한 줄 JSON으로 남깁니다.

> 자세한 인증 흐름은 [v2 인증 및 권한 흐름](./docs/api-flows/auth-flow.md)에서 확인할 수 있습니다.

## 🔄 동작 과정 (How It Works)

| 기능 | 사용자가 보는 흐름 | 상세 문서 |
| :--- | :--- | :--- |
| 과제 조회 | 코스 선택 -> 주차/과제 목록 조회 -> 공개된 과제 상세 확인 | [과제 공개 및 조회 흐름](./docs/api-flows/assignment-publish-flow.md) |
| 관리자 과제 운영 | 관리자 인증 -> 코스 선택 -> 과제 생성/수정/삭제/복사 -> 결과 확인 | [관리자 코스/과제 관리 흐름](./docs/api-flows/admin-course-assignment-flow.md) |
| OJ 문제 동기화 | 테스트케이스 저장 -> snapshot 생성 -> SNS 이벤트 발행 | [테스트케이스 OJ 동기화 흐름](./docs/api-flows/testcase-oj-sync-flow.md) |
| 채점 완료 반영 | OJ 이벤트 발행 -> SQS 소비 -> projection 저장 -> 제출 현황 조회 | [Judge Completion 이벤트 소비 흐름](./docs/api-flows/judge-completion-consumer-flow.md) |
| 인증/인가 | Bearer 토큰 전달 -> JWT 검증 -> role 기반 접근 제어 | [v2 인증 및 권한 흐름](./docs/api-flows/auth-flow.md) |

## 🧪 테스트 및 검증 (Test & Verification)

- `.github/workflows/ci-test.yml`에서 `develop`, `main`, PR 기준 `./gradlew test --no-daemon` 실행이 확인됩니다.
- `build.gradle.kts`에 `Jacoco` 리포트와 라인 커버리지 `0.70` 검증 설정이 있습니다.
- Docker 이미지에는 `/actuator/health/readiness` 기반 `HEALTHCHECK`가 포함되어 있습니다.
- 운영 배포 성공 여부와 현재 공개 API URL은 이 README 작성 시점에 직접 검증하지 못해 [확인 필요]입니다.

## 📚 개발 로그 및 트러블 슈팅 (Troubleshooting Log)

| 문서 | 핵심 내용 |
| :--- | :--- |
| [V2 Structured Logging](./docs/logging-v2.md) | v2 API 로그 스키마, 마스킹 정책, 로컬/운영 확인 방법 |
| [CloudWatch Report Server](./docs/cloudwatch-report-server.md) | Docker `awslogs` driver 기반 CloudWatch 수집과 Logs Insights 쿼리 |
| [Discord Alerts From CloudWatch](./docs/discord-alert-from-cloudwatch.md) | CloudWatch subscription filter와 Discord 알림 전달 기준 |
| [v2.0.8 Deploy](./docs/deploy-v2.0.8.md) | 태그 배포, EC2 확인, Gateway smoke test, rollback 절차 |

## 🛠️ 기술 스택 (Tech Stack)

| 구분 | 기술 |
| :--- | :--- |
| Language | `Kotlin 1.9.25`, `Java 21` |
| Framework | `Spring Boot 3.4.3`, `Spring WebFlux` |
| Database | `MongoDB`, `Spring Data MongoDB Reactive` |
| Security | `Spring Security`, `OAuth2 Resource Server`, `JWT HS256` |
| Event | `AWS SNS`, `AWS SQS`, AWS SDK for Java v2 |
| API Docs | `SpringDoc OpenAPI`, Swagger UI |
| Build/Test | `Gradle Kotlin DSL`, `JUnit`, `Kotest`, `Jacoco` |
| Deploy/Ops | `Docker`, `Docker Compose`, `GitHub Actions`, `Amazon ECR`, `EC2`, `CloudWatch Logs` |

## ⚙️ 로컬 실행 방법 (Getting Started)

### 1. 사전 준비

- JDK 21
- Docker & Docker Compose
- MongoDB는 Docker Compose로 실행 가능

### 2. 환경 변수

로컬 기본값은 `src/main/resources/application.yml`에 정의되어 있습니다.
필요하면 프로젝트 루트에 `.env` 파일을 만들고 값을 덮어씁니다.

```properties
MONGO_DB_URL=mongodb://localhost:27017/aandi
SWAGGER_URL=http://localhost:8080
AUTH_ISSUER_URI=http://localhost:9000
AUTH_AUDIENCE=aandi-gateway
AUTH_JWT_SECRET=local-dev-jwt-secret-must-be-at-least-32-bytes
```

### 3. 실행

```bash
docker compose up -d mongodb
./gradlew bootRun
```

또는 서버까지 Docker Compose로 실행합니다.

```bash
docker compose up -d
```

### 4. 확인

```text
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/actuator/health/readiness
```

## 📖 상세 문서 (Docs)

- [Docs Index](./docs/README.md)
- [API/Event Flow Index](./docs/api-flows/README.md)
- [Demo Assets Guide](./docs/assets/README.md)
- [Legacy README](./docs/legacy-readme.md)

## 🧭 회고와 개선점 (Retrospective & Next Step)

이 서버는 과제 운영 데이터를 단순히 저장하는 데서 끝나지 않고, Online Judge Server와 운영 로그까지 이어지는 흐름을 다룹니다.
문서 개편에서는 첫 화면의 정보를 줄이고, API/Event 상세를 `docs/`로 분리해 포트폴리오 README와 개발 문서의 역할을 나눴습니다.

다음 개선점은 실제 화면 GIF 추가, 운영 배포 URL 검증, OJ 연동 smoke test 기록 보강입니다.
