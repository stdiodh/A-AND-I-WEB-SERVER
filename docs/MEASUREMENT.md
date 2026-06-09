# Measurement

> 메인 README로 돌아가기: [README](../README.md)

본 문서는 테스트, 커버리지, 성능 측정 상태를 실제 실행 결과 기준으로 기록합니다. 확인되지 않은 latency, throughput, 성능 개선률은 작성하지 않습니다.

## 실행 환경

| 항목 | 값 |
| :--- | :--- |
| 저장소 | `/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER` |
| 실행일 | 2026-06-04 KST |
| 언어/빌드 | Kotlin, Java 21, Gradle Kotlin DSL |
| 테스트 도구 | JUnit, Kotest |
| 커버리지 도구 | JaCoCo |
| DB | MongoDB |
| CI | `.github/workflows/ci-test.yml`, `.github/workflows/deploy-tag.yml` |

## 실행한 명령

```bash
./gradlew clean test
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification
./gradlew check
```

## 테스트 결과

| 명령 | 결과 |
| :--- | :--- |
| `./gradlew clean test` | 성공, 188 tests / 0 failures / 0 errors / 0 skipped |
| `./gradlew jacocoTestReport` | 성공 |
| `./gradlew jacocoTestCoverageVerification` | 성공 |
| `./gradlew check` | 성공 |

Kotlin compile warning 3건이 있었지만 build 실패로 이어지지 않았습니다.

## 커버리지 결과

JaCoCo XML 기준:

| 항목 | covered | total | coverage |
| :--- | ---: | ---: | ---: |
| INSTRUCTION | 13,848 | 18,289 | 75.72% |
| LINE | 2,289 | 2,932 | 78.07% |
| BRANCH | 761 | 1,391 | 54.71% |

커버리지 이미지:

```txt
docs/assets/images/coverage-report.png
```

## 성능 측정 상태

현재 before/after 측정값은 없습니다.

| 항목 | 상태 |
| :--- | :--- |
| API latency before/after | 현재 before/after 측정값은 없습니다 |
| throughput before/after | 현재 before/after 측정값은 없습니다 |
| MongoDB query explain 비교 | 현재 before/after 측정값은 없습니다 |
| SQS 처리량 개선률 | 현재 before/after 측정값은 없습니다 |
| 장애 대응 시간 단축률 | 현재 before/after 측정값은 없습니다 |

로컬 `aandi` MongoDB는 컬렉션이 없는 상태로 확인되어 대표 query cost를 측정하지 않았습니다. 쿼리 튜닝은 대표 데이터셋과 `explain("executionStats")` 결과가 있을 때만 적용합니다.

## 다음 측정 절차

1. 대표 fixture 또는 staging 데이터를 준비합니다.
2. 사용자 과제 목록 조회, 과제 상세 조회, 관리자 제출 현황 조회를 측정 대상으로 고릅니다.
3. 같은 데이터셋과 같은 JVM 옵션에서 baseline을 기록합니다.
4. MongoDB query는 `explain("executionStats")`로 `totalDocsExamined`, `totalKeysExamined`, `executionTimeMillis`를 기록합니다.
5. 변경 후 같은 조건으로 다시 측정합니다.
6. 수치가 확인되기 전까지 이력서에는 latency, throughput, 성능 개선률을 쓰지 않습니다.
