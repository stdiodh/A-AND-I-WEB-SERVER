# 테스트와 성능 측정

테스트는 188개에서 602개로 늘렸고, 과제 목록 조회가 과제마다 하위 데이터를 따로 읽던 구조를 일괄 조회로 바꿨습니다. 테스트 개수나 P95만 적지 않고, 어떤 데이터와 조건으로 무엇을 확인했는지 함께 기록합니다.

[README로 돌아가기](../README.md)

## 이전과 현재

| 항목 | 이전 | 현재 |
| --- | --- | --- |
| 테스트 | 188 (2026-06-04) | 602 (2026-11-26) |
| Line coverage | 78.07% | 97.13% |
| Branch coverage | 54.71% | 82.17% |
| CI coverage 기준 | Line 70% | Line 86%, Branch 62% |
| 과제 30개 목록의 하위 데이터 조회 | 60회 | 2회 (96.67% 감소) |
| 과제 300개 · 15 RPS, 목록 P95 | 79.049ms | 47.760ms (39.58% 감소) |
| 과제 1,000개 · 5 RPS, 목록 P95 | 187.474ms | 128.437ms (31.49% 감소) |
| 과제 300개 · 50 RPS | 처리량 30.8 req/s, 요청 누락 발생 | 50 RPS 유지, 누락 0 |
| 과제 1,000개 · 50 RPS | 처리량 8.6 req/s, 요청 누락 발생 | 50 RPS 유지, 누락 0 |

2026-07-10에 coverage 측정 범위를 넓혀 분모가 늘었습니다. 그래서 이전·현재 coverage 차이를 테스트 보강만의 효과로 보지 않습니다. 성능 수치는 로컬 MongoDB와 localhost API에서 같은 데이터·같은 부하로 비교한 값이며, 운영 최대 처리량이 아닙니다. 과제 규모별 측정 조건은 [과제 목록 재측정 결과](./performance/results/2026-11-26-assignment-list-scale-before-after.md)에 있습니다.

## 테스트와 coverage 변화

| 시점 | 테스트 | Line coverage | Branch coverage | CI 기준 | 바뀐 것 |
| --- | ---: | ---: | ---: | --- | --- |
| 2026-06-04 | 188 | 78.07% (2,289 / 2,932) | 54.71% (761 / 1,391) | Line 70% | 처음 기준 |
| 2026-06-23 | 277 | 85.04% | 62.59% | Line 83%, Branch 61% | 실패 경로 테스트 보강 (PR #58) |
| 2026-07-10 | 338 | 88.22% (4,233 / 4,798) | 63.42% (1,125 / 1,774) | Line 86%, Branch 62% | 측정 범위 확장 |
| 2026-07-11 | 388 | 88.87% (4,431 / 4,986) | 63.74% (1,141 / 1,790) | Line 86%, Branch 62% | 서비스 경계·인덱스·삭제 순서 테스트 |
| 2026-07-13 | 456 | 92.00% (4,555 / 4,951) | 66.09% (1,175 / 1,778) | Line 86%, Branch 62% | 저장소 port·adapter 분리 |
| 2026-11-26 | 602 | 97.13% | 82.17% | Line 86%, Branch 62% | 현재 |

- 처음 기준은 entity, repository, DTO, 일부 config·controller·service를 측정에서 뺀 값이라 전체 코드 기준이 아닙니다.
- 2026-07-10에 같은 338개 테스트로 제외 규칙만 바꿔 다시 계산하니 Line 89.98%가 88.22%로 내려갔습니다. 분모가 Line 1,665개, Branch 286개 늘었고, 실제로 덮인 Line은 1,414개, Branch는 174개 늘었습니다.
- 지금 제외하는 것은 Spring Boot 진입점(`Application`, `ApplicationKt`)과 실행 로직이 없는 OpenAPI 문서 모델뿐입니다. 이전에 쓰던 `**/*$*` 패턴은 코루틴·람다로 컴파일된 서비스 로직까지 숨겨서 뺐습니다.
- MongoDB 인덱스 migration 스크립트의 단위 테스트 12개는 `node --test`로 따로 돌리며 위 숫자에 넣지 않습니다.

개수보다 다음 규칙을 먼저 테스트합니다.

- 공개 전 과제와 비공개 testcase는 노출하지 않음
- 과제 복사의 중복 방지와 부분 실패 정리
- 이벤트 파싱과 제출 현황 갱신
- 동시 갱신 충돌 재시도
- 공통 오류 응답과 로그 마스킹

## 과제 목록 하위 데이터 일괄 조회

과제 목록이 과제마다 requirement와 testcase를 따로 조회하던 구조를, 두 종류를 한 번씩 일괄 조회하도록 바꿨습니다.

| 과제 30개 | 이전 | 현재 |
| --- | ---: | ---: |
| requirement 조회 | 30 | 1 |
| testcase 조회 | 30 | 1 |
| 합계 | 60 | 2 (96.67% 감소) |

서비스에서 저장소를 호출한 횟수이며 MongoDB 명령 수가 아닙니다. `AssignmentQueryServiceUserTest`가 과제 0·1·30개에서 일괄 조회 횟수와 단건 조회 미사용을 확인합니다. 인덱스는 추가하지 않았고, 세 조회 모두 기존 인덱스(`ux_assignment_course_week_order`, `ux_assignment_requirement_sort`, `ux_assignment_test_case_seq`)를 사용해 검사 문서 수가 반환 수와 같았습니다.

같은 날 목록 60%·상세 40% 혼합 부하(100 RPS, 2분, 3회)로도 쟀습니다.

| 지표 | 이전 중앙값 | 현재 중앙값 | 해석 |
| --- | ---: | ---: | --- |
| 목록 P95 | 8.084ms | 8.006ms | 3회 범위가 겹쳐 개선으로 보지 않음 |
| 목록 P99 | 10.234ms | 9.638ms | 5.82% 감소 |
| 상세 P95 | 3.570ms | 3.901ms | 이번 변경 대상 아님 |
| HTTP 실패율·check 성공률·요청 누락 | 0% · 100% · 0 | 0% · 100% · 0 | 같음 |
| 비공개 testcase 노출 | 0 | 0 | 같음 |

과제 30개에서는 응답 시간 차이가 측정 편차 안에 있었습니다. 과제 수를 300·1,000개로 늘린 재측정에서 차이가 분명해졌습니다([과제 목록 재측정 결과](./performance/results/2026-11-26-assignment-list-scale-before-after.md)). 목록만 측정한 7월 결과는 [과제 목록 응답 시간 비교 (2026-07-09)](./performance/results/2026-07-09-assignment-list-before-after.md)에 있습니다.

## 측정 조건

| 항목 | 값 |
| --- | --- |
| 데이터 | Course 1, 주차 3, 과제 30, 수강생 100, 제출 현황 60 (비공개 testcase 노출 확인용 표식 포함) |
| 부하 | constant-arrival-rate, 100 RPS, 2분, 3회, 준비 실행 뒤 측정 |
| 환경 | Apple M5 Pro 48GiB, OpenJDK 21.0.6, 로컬 MongoDB 7.0.16, k6 v0.52.0 |

k6 check는 상태 코드와 함께 목록에 대상 과제가 있는지, 상세 응답의 `assignmentId`·`courseSlug`가 요청과 같은지, testcase가 `PUBLIC`만 있는지를 확인합니다.

변경 전후 비교는 데이터 개수와 내용, JVM·MongoDB·하드웨어·k6 버전, 부하 모델·RPS·시간·요청 비율, 준비 실행과 check 결과가 같을 때만 개선율을 계산합니다.

## 확인한 방법

```bash
./gradlew clean test jacocoTestReport jacocoTestCoverageVerification

performance/fixtures/run-fixture.sh seed
performance/fixtures/run-fixture.sh verify
python3 performance/k6/tools/generate_test_jwt.py --output performance/k6/env.local
MONGO_DB_URL=mongodb://localhost:27017/aandi_performance ./gradlew bootRun

# 다른 터미널
performance/k6/run-local.sh preflight performance/k6/env.local
performance/k6/run-local.sh assignment-read performance/k6/env.local
```

스크립트는 운영 대상 부하 테스트를 막고, 원격 실행은 HTTPS staging과 명시한 host 허용 목록이 모두 있을 때만 허용합니다.

## 남은 한계

- 고정 RPS 측정이라 최대 처리량은 따로 시나리오를 정의해야 잴 수 있습니다.
- 이벤트 파이프라인(SNS·SQS)은 아직 측정하지 않았습니다. Outbox 도입 뒤 장애 시 유실 건수, 발행 지연 P95, DLQ 유입 건수가 다음 측정 대상입니다.
