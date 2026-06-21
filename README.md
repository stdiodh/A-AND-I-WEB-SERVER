# A&I Assignment & Report Server

> A&I에서 과제가 만들어지고 공개된 뒤, Online Judge의 채점 결과가 운영 화면에 반영되기까지의 흐름을 담당하는 교육 운영 백엔드입니다.

저장소 이름은 `WEB-SERVER`지만 화면을 렌더링하는 서버는 아닙니다. 코스·수강·과제·테스트케이스의 원본 데이터를 관리하고, 수강생과 운영자에게 필요한 API를 제공합니다. 과제 변경은 이벤트로 Online Judge에 전달하고, 채점 완료 이벤트는 조회에 적합한 projection으로 저장합니다.

![Architecture](./docs/assets/diagrams/architecture.png)

## 이 저장소가 A&I에서 맡는 일

### 과제 운영 데이터의 기준점

![MongoDB Data Model](./docs/assets/diagrams/data-model.png)

코스, 수강 정보, 과제, 요구사항, 테스트케이스를 MongoDB에 저장합니다. 관리자는 과제를 생성·수정·복사·삭제하고 공개 시점을 관리하며, 수강생은 자신이 접근할 수 있는 공개 과제만 조회합니다.

### Online Judge와의 경계

과제가 바뀔 때 problem과 testcase snapshot을 SNS로 발행합니다. Online Judge를 API로 직접 호출하지 않기 때문에 과제 운영 로직과 채점 서버의 배포·장애 범위를 분리할 수 있습니다.

### 제출 결과 조회 모델

Online Judge의 `JUDGE_COMPLETED` 이벤트를 SQS로 받아 과제·사용자별 제출 상태 projection을 갱신합니다. 관리자 제출 현황 API는 채점 서버를 실시간 호출하지 않고 이 projection을 조회합니다.

### 운영 추적성

v2 API 요청은 `traceId`, `requestId`, 상태 코드, 오류 코드, 지연시간을 JSON 로그로 남깁니다. 토큰, 비밀번호, private testcase, 제출 코드 원문은 마스킹하거나 기록하지 않습니다.

## 과제가 만들어지고 결과가 보이기까지

### 과제 생성·수정

```text
Admin API
  -> Assignment / Requirement / TestCase 저장
  -> problem snapshot 생성
  -> SNS 발행
  -> Online Judge가 SQS로 소비
```

과제 복사에서는 원본 과제 ID와 내용 fingerprint를 함께 저장하고 unique index로 중복 복사를 막습니다. requirements와 testcases 복사 중 실패하면 생성한 문서를 정리해 부분 데이터가 남지 않도록 합니다.

### 수강생 과제 조회

```text
USER 요청
  -> 코스 수강 여부 확인
  -> 공개 상태와 시작 시각 확인
  -> PUBLIC testcase만 필터링
  -> 과제 목록 또는 상세 응답
```

공개 전 과제는 존재 여부까지 감추기 위해 수강생 조회에서 `404`로 처리합니다. 상세 응답에는 `PUBLIC` testcase만 포함합니다.

### 채점 결과 반영

```text
Online Judge JUDGE_COMPLETED
  -> SQS consumer
  -> 이벤트 파싱
  -> submission projection upsert
  -> 관리자 제출 현황 API 조회
```

동일 과제와 사용자의 projection은 compound unique index로 보호합니다. 동시 갱신 충돌은 optimistic locking과 최대 5회 재시도로 처리하며, 점수는 가장 높은 결과를 기준으로 갱신합니다.

## 화면으로 확인하기

[과제 운영 데모 영상](./docs/assets/videos/assignment-operation.mov)

![Problem Judge Demo](./docs/assets/gifs/problem-judge.gif)

## 구현에서 중요하게 본 세 가지

### 공개 상태와 testcase visibility를 따로 다룹니다

과제가 공개되었더라도 private testcase는 수강생 응답에 포함되면 안 됩니다. 과제 공개 여부와 testcase visibility를 별도의 규칙으로 검사해 두 조건을 모두 만족한 데이터만 반환합니다.

- [CourseQueryService.kt](./src/main/kotlin/com/example/aandi_post_web_server/course/application/service/CourseQueryService.kt)

### 서비스 간 동기화를 이벤트로 분리합니다

과제 저장과 Online Judge 문제 반영을 하나의 동기 API 호출로 묶지 않았습니다. 과제 snapshot을 이벤트 payload로 만들어 발행하고, 소비자가 자신의 데이터 모델에 맞게 반영합니다.

- [SnsAssignmentReportTestCaseEventPublisher.kt](./src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/event/SnsAssignmentReportTestCaseEventPublisher.kt)
- [SqsJudgeSubmissionEventConsumer.kt](./src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/submission/event/SqsJudgeSubmissionEventConsumer.kt)

### 조회 목적에 맞는 projection을 둡니다

관리자 제출 현황은 Online Judge의 원본 이벤트를 매번 조합하지 않습니다. `assignmentId + publicCode` 단위 projection을 유지하고, 첫 제출 시각·최근 제출 시각·최고 점수·통과 testcase 수를 조회에 맞게 저장합니다.

- [AssignmentSubmissionStatusProjectionService.kt](./src/main/kotlin/com/example/aandi_post_web_server/assignment/application/submission/service/AssignmentSubmissionStatusProjectionService.kt)

## k6로 확인한 읽기 경로

합성 fixture를 준비하고 수강생의 과제 목록과 상세 조회를 60:40 비율로 호출했습니다.

| 측정 조건 | 값 |
| :--- | :--- |
| Fixture | 과제 30개, 수강생 100명, 제출 projection 60개 |
| 부하 모델 | constant-arrival-rate, 100 RPS |
| 시간 | 2분 |
| 반복 | 3회 |
| k6 | v0.52.0 |

| 3회 중앙값 | 결과 |
| :--- | ---: |
| 과제 목록 P95 | **6.559 ms** |
| 과제 상세 P95 | **3.088 ms** |
| 성공 처리량 | **100.002 req/s** |
| HTTP 실패율 | **0.00%** |
| Check 성공률 | **100.00%** |
| Dropped iterations | **0** |
| Private testcase 노출 | **0건** |

k6 check는 HTTP 200만 확인하지 않습니다. 요청한 과제가 목록에 존재하는지, 상세 응답의 ID가 일치하는지, private testcase marker가 응답에 포함되지 않는지도 함께 검사합니다.

```bash
performance/k6/run-local.sh preflight performance/k6/env.local
performance/k6/run-local.sh assignment-read performance/k6/env.local
```

> 위 결과는 로컬 단일 머신과 합성 데이터에서 얻은 회귀 baseline입니다. 운영 최대 처리량이나 전체 이벤트 파이프라인의 성능을 의미하지 않습니다.

## 테스트

| 항목 | 결과 |
| :--- | :--- |
| 테스트 | 188 tests, 0 failures |
| JaCoCo line coverage | 78.07% |
| JaCoCo branch coverage | 54.71% |
| Coverage verification | line 70% 이상을 CI에서 검증 |

![Coverage Report](./docs/assets/images/coverage-report.png)

Coverage 수치는 `build.gradle.kts`에 정의된 제외 범위를 적용한 결과입니다. 숫자만 강조하기보다 공개·비공개 데이터 분리, 이벤트 파싱, projection 갱신, 오류 응답처럼 장애 영향이 큰 규칙을 테스트 대상으로 유지합니다.

```bash
./gradlew clean test
./gradlew jacocoTestCoverageVerification
```

## 현재 알고 있는 한계

### MongoDB 저장과 SNS 발행은 하나의 원자 작업이 아닙니다

현재는 과제 데이터를 저장한 뒤 SNS 이벤트를 발행합니다. DB 저장은 성공했지만 이벤트 발행이 실패하면 Online Judge와 데이터가 어긋날 수 있습니다. 다음 단계는 transactional outbox와 재처리 상태를 도입하고, 이벤트 ID를 기준으로 소비자의 중복 처리를 막는 것입니다.

### 과제 목록 조회에는 N+1 쿼리 가능성이 있습니다

과제 목록을 읽은 뒤 각 과제의 requirements와 testcases를 별도로 조회합니다. 현재 30개 과제 fixture에서는 낮은 P95를 확인했지만, 과제 수가 커질수록 쿼리 수가 함께 늘어날 수 있습니다. assignment ID 묶음 조회와 서버 측 필터링으로 바꾼 뒤 같은 fixture에서 `docsExamined`, query count, P95를 before/after로 비교할 계획입니다.

### 현재 k6는 HTTP 읽기 경로만 설명합니다

100 RPS 결과는 과제 목록·상세 조회에 한정됩니다. 과제 생성, SNS/SQS 처리량, projection 반영 지연을 설명하려면 별도의 이벤트 테스트와 CloudWatch 지표가 필요합니다.

## 실행

```bash
docker compose up -d mongodb
./gradlew bootRun
```

```text
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/actuator/health/readiness
```

## 기술 스택

| 영역 | 기술 |
| :--- | :--- |
| Language | Kotlin 1.9.25, Java 21 |
| Backend | Spring Boot 3.4.3, WebFlux |
| Database | MongoDB Reactive |
| Event | AWS SNS/SQS, AWS SDK |
| Security | Spring Security, OAuth2 Resource Server |
| Test | JUnit5, Kotest, Reactor Test, JaCoCo |
| Infra | Docker, GitHub Actions, AWS ECR/EC2, CloudWatch Logs |
| Performance | k6 |

## 필요한 문서만 남겼습니다

- [측정 기준과 검증 결과](./docs/MEASUREMENT.md)
- [패키지 구조 원칙](./PACKAGE_STRUCTURE_GUIDE.md)
