# Test

> 메인 README로 돌아가기: [README](../README.md)

본 문서는 2026년 06월 04일 KST 기준 로컬에서 실제 실행한 Gradle 명령 결과를 기록합니다.

## 실행 환경

| 항목 | 값 |
| :--- | :--- |
| 저장소 | `/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER` |
| 실행일 | 2026-06-04 KST |
| Gradle wrapper | `./gradlew` |
| Java toolchain | `Java 21` (`build.gradle.kts`) |

## 실행한 명령

```bash
./gradlew clean test
./gradlew jacocoTestReport
./gradlew jacocoTestCoverageVerification
./gradlew check
```

## 결과 요약

| 명령 | 결과 | 비고 |
| :--- | :--- | :--- |
| `./gradlew clean test` | 성공 | 188 tests / 0 failures / 0 errors / 0 skipped |
| `./gradlew jacocoTestReport` | 성공 | XML/HTML report 생성 확인 |
| `./gradlew jacocoTestCoverageVerification` | 성공 | `LINE minimum 0.70` rule 통과 |
| `./gradlew check` | 성공 | Gradle `check` task 성공 |

`./gradlew clean test` 실행 중 Kotlin warning이 있었지만 build 실패로 이어지지 않았습니다.

## 테스트 결과

`build/test-results/test/TEST-*.xml` 집계:

| tests | failures | errors | skipped |
| :--- | :--- | :--- | :--- |
| 188 | 0 | 0 | 0 |

## 이번 단계 추가 테스트

| 테스트 | 검증한 기존 동작 |
| :--- | :--- |
| `ErrorResponseFactoryTest` | `ResponseStatusException` status별 v1 error code 매핑 |
| `ErrorResponseFactoryTest` | 필수값 누락 `ServerWebInputException`의 `MISSING_REQUIRED_VALUE` 응답 |
| `ErrorResponseFactoryTest` | 잘못된 JSON의 `JSON_PARSE_ERROR`와 line/column 메시지 |
| `ErrorResponseFactoryTest` | 중첩된 enum `InvalidFormatException`의 `ENUM_MISMATCH` 응답 |
| `ErrorResponseFactoryTest` | 알 수 없는 예외의 `INTERNAL_ERROR`와 request id header 유지 |

HTML report:

```text
build/reports/tests/test/index.html
```

## JaCoCo report

Report path:

```text
build/reports/jacoco/test/jacocoTestReport.xml
build/reports/jacoco/test/html/index.html
```

JaCoCo XML 최상위 counter 기준:

| 항목 | missed | covered | total | coverage |
| :--- | ---: | ---: | ---: | ---: |
| INSTRUCTION | 4,441 | 13,848 | 18,289 | 75.72% |
| LINE | 643 | 2,289 | 2,932 | 78.07% |
| BRANCH | 630 | 761 | 1,391 | 54.71% |

주의: HTML index는 정수 단위로 line 78%, branch 54%처럼 표시합니다. 위 소수점 값은 XML counter의 `covered / (missed + covered)`로 계산했습니다.

## Coverage before / after

| 기준 | Before | After | 변화 | 근거 |
| :--- | ---: | ---: | :--- | :--- |
| tests | 183 | 188 | +5 | `build/test-results/test/TEST-*.xml` |
| LINE | 76.40% | 78.07% | +1.67%p | `build/reports/jacoco/test/jacocoTestReport.xml` |
| BRANCH | 52.19% | 54.71% | +2.52%p | `build/reports/jacoco/test/jacocoTestReport.xml` |

Before는 포트폴리오 문서 정리 직후의 JaCoCo XML 기준입니다. After는 `ErrorResponseFactoryTest` 추가 후 같은 명령으로 재측정한 값입니다.

## Coverage verification rule

`build.gradle.kts` 기준:

```kotlin
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
```

- 검증 대상은 `coverageExcludes`로 제외된 class를 뺀 Kotlin main class directory입니다.
- verification rule은 line coverage minimum 0.70입니다.
- branch coverage minimum rule은 현재 설정되어 있지 않습니다.

## CI에서 실행되는 task

`.github/workflows/ci-test.yml` 기준:

```yaml
- name: Run tests
  run: ./gradlew test --no-daemon
```

CI는 `test` task를 실행합니다. `build.gradle.kts`에서 `test.finalizedBy(jacocoTestReport)`가 설정되어 있어 test 뒤에 report task는 이어지지만, CI workflow에 `jacocoTestCoverageVerification`는 직접 포함되어 있지 않습니다. 따라서 “CI 품질 게이트가 coverage verification으로 차단한다”는 표현은 현재 근거가 부족합니다.

## 실패 원인 기록

이번 실행에서는 실패한 명령이 없습니다.

## 이력서 사용 기준

쓸 수 있는 문장:

- 로컬 기준 Gradle 테스트 188개가 통과했습니다.
- JaCoCo XML 기준 line coverage는 78.07%, branch coverage는 54.71%입니다.
- 현재 Gradle coverage verification은 line coverage 0.70 minimum rule을 통과했습니다.

주의할 문장:

- CI에서 coverage verification으로 PR을 차단한다고 쓰면 안 됩니다.
- branch coverage 목표를 달성했다고 쓰면 안 됩니다. branch minimum rule이 없습니다.
