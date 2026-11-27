# 과제 목록 응답 시간 비교 (2026-07-09)

과제 목록의 하위 데이터 조회를 일괄 조회로 바꾼 서버를, 과제 30개 데이터와 목록만 호출하는 부하로 비교했습니다. 이 측정에서는 P95 중앙값이 18.63% 낮아졌지만, 2026-11-26 재측정에서는 재현되지 않았습니다.

## 이전과 현재

| 지표 | 이전 중앙값 | 현재 중앙값 | 변화 |
| --- | ---: | ---: | ---: |
| 목록 P50 | 7.280ms | 6.273ms | 13.83% 감소 |
| 목록 P90 | 8.606ms | 7.142ms | 17.01% 감소 |
| 목록 P95 | 9.297ms | 7.565ms | 18.63% 감소 |
| 목록 P99 | 11.512ms | 9.546ms | 17.08% 감소 |
| HTTP 실패율 · check 성공률 · 요청 누락 | 0% · 100% · 0 | 0% · 100% · 0 | 같음 |

| 지표 | 이전 3회 범위 | 현재 3회 범위 |
| --- | --- | --- |
| 목록 P50 | 7.111~7.490ms | 6.144~6.376ms |
| 목록 P95 | 9.168~9.419ms | 7.422~7.730ms |
| 목록 P99 | 11.270~13.025ms | 9.340~9.581ms |

같은 서버 두 버전으로 [2026-11-26에 다시 쟀을 때](./2026-11-26-assignment-list-scale-before-after.md) 과제 30개에서는 차이가 편차 범위 안이었습니다. 그래서 과제 30개 기준 응답 시간 개선은 대표 수치로 쓰지 않고, 과제 300·1,000개 재측정 결과를 씁니다.

## 측정 조건

| 항목 | 값 |
| --- | --- |
| API | `GET /v2/courses/{courseSlug}/assignments?status=PUBLISHED` (목록 100%) |
| 데이터 | Course 1, 주차 3, 과제 30, 수강생 100, 제출 현황 60 |
| 부하 | constant-arrival-rate, 100 RPS, 2분, 3회, sleep 0 |
| 환경 | Apple M5 Pro 48GiB, OpenJDK 21.0.6, 로컬 MongoDB 7.0.16, k6 v0.52.0 |

localhost API와 로컬 MongoDB만 사용했습니다.

## 확인한 방법

두 버전의 서버를 각각 띄우고 같은 시나리오를 3회씩 실행했습니다.

```bash
MONGO_DB_URL=mongodb://localhost:27017/aandi_performance ./gradlew bootRun

# 다른 터미널
RUNS=3 SCENARIO_FILES=<과제 30개 목록 시나리오> \
  performance/scripts/run-assignment-scale-local.sh
```

하위 데이터 조회 횟수(60회 → 2회)는 `AssignmentQueryServiceUserTest`로 확인합니다.

```bash
./gradlew test --tests '*AssignmentQueryServiceUserTest'
```

## 남은 한계

- MongoDB `system.profile` 명령 수는 보관 한도 영향이 있어 쓰지 않았습니다. 60회 → 2회는 서비스가 저장소를 호출한 횟수입니다.
