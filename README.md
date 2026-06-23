# A&I Assignment & Report Server

> 과제를 만들고 공개하는 순간부터 Online Judge의 채점 결과가 운영 화면에 반영되기까지를 담당하는 교육 운영 백엔드입니다.

저장소 이름은 `WEB-SERVER`지만 화면을 렌더링하는 서버는 아닙니다.

코스·수강·과제·요구사항·테스트케이스의 원본 데이터를 관리하고, 수강생과 운영자에게 필요한 API를 제공합니다.

과제가 변경되면 Online Judge가 사용할 problem snapshot을 이벤트로 전달하고, 채점 완료 이벤트는 관리자 조회에 적합한 submission projection으로 저장합니다.

![Architecture](./docs/assets/diagrams/architecture.png)

## 이 서버가 맡는 일

| 구간 | 처리 내용 |
| :--- | :--- |
| 코스 운영 | 코스, 주차, 수강 정보와 접근 권한을 관리합니다. |
| 과제 운영 | 과제, 요구사항, 공개 시각, 테스트케이스를 관리합니다. |
| 수강생 조회 | 수강 여부와 공개 상태를 확인하고 공개 가능한 데이터만 반환합니다. |
| 문제 동기화 | 과제 변경 내용을 SNS/SQS 이벤트로 Online Judge에 전달합니다. |
| 제출 현황 | 채점 완료 이벤트를 과제·사용자 단위 projection으로 저장합니다. |
| 운영 추적 | 요청 ID, 상태 코드, 오류 코드, 지연시간을 구조화 로그로 남깁니다. |

## 과제가 Online Judge에 반영되는 흐름

```mermaid
sequenceDiagram
    autonumber
    actor Admin as 운영자
    participant API as Assignment & Report Server
    participant DB as MongoDB
    participant SNS
    participant SQS
    participant Judge as Online Judge

    Admin->>API: 과제 생성 또는 수정
    API->>API: 입력값·공개 상태·테스트케이스 검증
    API->>DB: Assignment · Requirement · TestCase 저장
    DB-->>API: 저장 결과
    API->>SNS: Problem snapshot 발행
    SNS->>SQS: Judge 구독 큐로 전달
    SQS->>Judge: Problem sync event
    Judge->>Judge: 문제와 테스트케이스 반영
```

서버가 Online Judge API를 동기 호출하지 않기 때문에 과제 운영과 채점 서버의 배포·장애 범위를 분리할 수 있습니다.

## 수강생 조회와 채점 결과 반영

```mermaid
sequenceDiagram
    autonumber
    actor Student as 수강생
    participant API as Assignment & Report Server
    participant DB as MongoDB
    participant Judge as Online Judge
    participant Queue as Judge Event SQS
    actor Admin as 운영자

    Student->>API: 과제 목록 또는 상세 조회
    API->>DB: 수강 정보와 과제 조회
    DB-->>API: 코스·과제·테스트케이스
    API->>API: 공개 시각 확인 · PRIVATE testcase 제외
    API-->>Student: 공개 가능한 과제 응답

    Judge->>Queue: JUDGE_COMPLETED 이벤트
    Queue->>API: 채점 완료 이벤트 전달
    API->>DB: assignmentId + publicCode projection 갱신

    Admin->>API: 제출 현황 조회
    API->>DB: submission projection 조회
    DB-->>API: 점수·통과 testcase·제출 시각
    API-->>Admin: 수강생별 제출 현황
```

공개 전 과제는 수강생 응답에서 존재 여부까지 드러나지 않도록 처리하고, 공개된 과제에서도 `PUBLIC` 테스트케이스만 반환합니다.

관리자 제출 현황은 Online Judge를 매번 호출하지 않고 MongoDB projection을 바로 조회해 읽기 경로를 짧게 유지합니다.

## 데이터 구조

![MongoDB Data Model](./docs/assets/diagrams/data-model.png)

MongoDB collection은 과제 원본 데이터와 제출 현황 projection의 목적을 나눠 관리합니다.

| 데이터 | 역할 |
| :--- | :--- |
| Course · Enrollment · Week | 코스 일정과 수강생 접근 범위 |
| Assignment · Requirement · TestCase | 과제 원본과 공개 데이터 |
| Submission Status Projection | 과제·사용자별 제출 여부와 최고 점수 |
| Event Snapshot | Online Judge 문제 동기화와 채점 결과 반영 |

## 화면으로 확인하기

운영자가 과제를 생성·수정하고 공개하는 흐름은 아래 영상에서 확인할 수 있습니다.

[과제 운영 데모 영상 보기](./docs/assets/videos/assignment-operation.mov)

수강생이 문제를 확인하고 채점 결과를 받는 흐름입니다.

![Problem Judge Demo](./docs/assets/gifs/problem-judge.gif)

## 성능과 테스트

### 자동화 테스트

테스트 결과는 일회성 수치가 아니라 CI에서 유지해야 하는 기준으로 관리합니다.

| 기준 | 테스트 | Line coverage | Branch coverage | CI 기준 |
| :--- | ---: | ---: | ---: | :--- |
| 초기 기준 | 188 | 78.07% | 54.71% | Line 70% |
| 1차 보강 | 220 | 81.57% | 59.35% | Line 70% |
| 현재 기준 | 277 | 85.04% | 62.59% | Line 83%, Branch 61% |

![JaCoCo coverage gate summary](./docs/assets/images/jacoco-report-before-after.png)

Coverage는 JaCoCo 제외 규칙이 적용된 configured scope 기준입니다.

### 읽기 API 부하 테스트

과제 목록 조회에서 assignment마다 requirement와 testcase를 따로 조회하던 구조를 batch 조회로 변경했습니다.

| 항목 | Before | After |
| :--- | ---: | ---: |
| Child repository calls, 30 assignments | 60 | 2 |
| 감소율 | - | 96.67% |
| HTTP 실패율 | 0.00% | 0.00% |
| Check 성공률 | 100.00% | 100.00% |
| Dropped iterations | 0 | 0 |
| Private testcase 노출 | 0건 | 0건 |

![k6 읽기 API 부하 테스트](./docs/assets/performance/web-k6-read-capacity.svg)

100 RPS, 2분, 3회 반복 조건에서 확인했습니다. 고정 부하 테스트 결과이므로 최대 처리량으로 해석하지 않습니다.

상세 측정 조건과 per-run 결과는 [테스트와 성능 측정](./docs/MEASUREMENT.md)에 정리했습니다.

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

## 참고 문서

- [테스트와 성능 측정](./docs/MEASUREMENT.md)
- [N+1 개선기](https://velog.io/@stdiodh/%ED%85%8C%EC%8A%A4%ED%8A%B8%EC%99%80-k6%EB%A1%9C-%EA%B2%80%EC%A6%9D%ED%95%9C-%EA%B3%BC%EC%A0%9C-%EB%AA%A9%EB%A1%9D-N1-%EA%B0%9C%EC%84%A0%EA%B8%B0#%EC%A1%B0%ED%9A%8C-%ED%9A%9F%EC%88%98)
- [패키지 구조 원칙](./PACKAGE_STRUCTURE_GUIDE.md)
