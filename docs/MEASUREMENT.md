# 테스트와 성능 측정

> 테스트 개수나 P95 숫자만 강조하지 않고, 어떤 데이터와 조건으로 무엇을 확인했는지 함께 기록합니다.

[README로 돌아가기](../README.md)

## 테스트

2026-06-04 KST 기준으로 다음 명령을 실행했습니다.

```bash
./gradlew clean test
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification
./gradlew check
```

| 항목 | 결과 |
| :--- | :--- |
| 테스트 | 188 tests, 0 failures, 0 errors, 0 skipped |
| JaCoCo line coverage | 2,289 / 2,932 = **78.07%** |
| JaCoCo branch coverage | 761 / 1,391 = **54.71%** |
| CI verification | configured scope의 line coverage 70% 이상 |

![Coverage Report](./assets/images/coverage-report.png)

Coverage는 `build.gradle.kts`의 제외 규칙을 적용한 결과입니다. entity, repository, DTO, OpenAPI/config 일부, controller 일부와 일부 legacy service가 제외되어 있으므로 전체 코드 기준 수치로 표현하지 않습니다.

숫자보다 다음 규칙을 우선적으로 테스트합니다.

- 공개 전 과제와 private testcase 비노출
- 과제 복사 중복 방지와 부분 실패 정리
- 이벤트 파싱과 submission projection 갱신
- 동시 갱신 충돌 재시도
- 공통 오류 응답과 구조화 로그 마스킹

## k6 읽기 baseline

### 목적

과제 목록과 상세 조회의 현재 기준을 남겨 이후 query 개선 전후를 같은 조건으로 비교합니다. 운영 최대 처리량이나 전체 이벤트 파이프라인 성능을 주장하기 위한 결과가 아닙니다.

### 환경

| 항목 | 값 |
| :--- | :--- |
| 측정일 | 2026-06-20 KST |
| 측정 대상 SHA | `718ff0ee2ad25a323ea8fb9120151c6845ea7ea1` |
| Java | OpenJDK 21.0.8, Eclipse OpenJ9 0.53.0 |
| MongoDB | Docker standalone, 7.0.37 |
| k6 | v0.52.0 |
| 머신 | Apple M5 Pro, 48 GiB |
| 부하 모델 | constant-arrival-rate |
| 부하 | 100 RPS, 2분, 3회 |
| 요청 비율 | 목록 60%, 상세 40% |

### Fixture

| 데이터 | 개수 |
| :--- | ---: |
| Course | 1 |
| Course week | 3 |
| Assignment | 30 |
| Enrollment | 100 |
| Submission projection | 60 |

Private testcase 검증을 위해 `PERF_PRIVATE_MUST_NOT_LEAK_001` marker를 fixture에 포함했습니다.

### 3회 중앙값

| 항목 | 결과 |
| :--- | ---: |
| 과제 목록 P50 | 4.912 ms |
| 과제 목록 P95 | **6.559 ms** |
| 과제 목록 P99 | 8.997 ms |
| 과제 상세 P50 | 2.209 ms |
| 과제 상세 P95 | **3.088 ms** |
| 과제 상세 P99 | 4.420 ms |
| 성공 처리량 | **100.002 req/s** |
| HTTP 실패율 | **0.00%** |
| Check 성공률 | **100.00%** |
| Dropped iterations | **0** |
| Private testcase 노출 | **0건** |

k6 check는 상태 코드뿐 아니라 다음 계약도 확인합니다.

- target assignment가 목록에 존재하는가
- 상세 응답의 `assignmentId`와 `courseSlug`가 요청과 일치하는가
- 응답 testcase가 `PUBLIC`만 포함하는가
- private marker가 노출되지 않는가

## 실행

```bash
performance/fixtures/run-fixture.sh cleanup
performance/fixtures/run-fixture.sh seed
performance/fixtures/run-fixture.sh verify
python3 performance/k6/tools/generate_test_jwt.py --output performance/k6/env.local
MONGO_DB_URL=mongodb://localhost:27017/aandi_performance ./gradlew bootRun
```

다른 터미널에서 실행합니다.

```bash
performance/k6/run-local.sh preflight performance/k6/env.local
performance/k6/run-local.sh assignment-read performance/k6/env.local
performance/k6/run-local.sh submission-status-read performance/k6/env.local
```

production 대상 부하 테스트는 스크립트에서 차단합니다. 원격 실행은 HTTPS staging과 명시적인 host allowlist가 모두 설정된 경우에만 허용합니다.

## 비교 기준

before/after 비교는 다음 조건이 같을 때만 유효합니다.

- fixture fingerprint와 데이터 개수
- JVM, MongoDB mode, 하드웨어와 k6 버전
- executor, target RPS, duration, endpoint 비율
- warm-up 여부와 check 결과

조건이 다르면 개선율을 계산하지 않습니다.

## 다음 측정

가장 먼저 과제 목록 조회의 query 수를 줄입니다.

1. 과제 30, 300, 1,000개 fixture를 준비합니다.
2. 현재 query count, P95, `totalDocsExamined`, `totalKeysExamined`를 기록합니다.
3. requirements와 testcases를 assignment ID 묶음으로 조회합니다.
4. 같은 조건에서 다시 측정해 query count와 P95 감소율을 계산합니다.

그다음에는 event pipeline을 별도로 측정합니다.

- Outbox 도입 후 SNS 장애 상황의 유실 이벤트 0건
- event publish lag P95
- duplicate event 재전달 시 projection 중복 0건
- SQS oldest message age와 DLQ 유입 건수
