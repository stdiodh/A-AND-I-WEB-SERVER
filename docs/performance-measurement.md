# Performance Measurement

> 메인 README로 돌아가기: [README](../README.md)

현재 before/after 측정값은 없습니다.

본 문서는 성능 수치를 임의로 만들지 않기 위해, 현재 확인된 수치 유무와 향후 측정 절차만 정리합니다.

## 현재 측정값 유무

| 항목 | 상태 |
| :--- | :--- |
| API latency before/after | 현재 before/after 측정값은 없습니다 |
| throughput before/after | 현재 before/after 측정값은 없습니다 |
| MongoDB query explain 비교 | 현재 before/after 측정값은 없습니다. 로컬 `aandi` DB의 `db.getCollectionNames()` 결과가 `[]`라 대표 query cost를 측정하지 않았습니다. |
| SQS 처리량 개선률 | 현재 before/after 측정값은 없습니다 |
| 장애 대응 시간 단축률 | 현재 before/after 측정값은 없습니다 |

## 측정 대상 API 후보

| 후보 | 이유 |
| :--- | :--- |
| 사용자 과제 목록 조회 | `startAt` 공개 상태 계산과 enrollment 검증이 포함됩니다. |
| 사용자 과제 상세 조회 | requirement/testcase 조회와 `PUBLIC` testcase 필터링이 포함됩니다. |
| 관리자 과제 생성/수정 | assignment 저장, testcase 저장, problem sync publish가 포함됩니다. |
| 관리자 제출 현황 조회 | enrollment, projection, report user 데이터를 조합합니다. |
| v2 API 오류 응답 | 구조화 로그와 error envelope를 함께 확인할 수 있습니다. |

## CloudWatch Logs Insights latency 조회 예시

```sql
fields @timestamp, http.method, http.path, http.statusCode, http.latencyMs, trace.traceId
| filter ispresent(http.latencyMs)
| stats count(*) as count,
        avg(http.latencyMs) as avgLatencyMs,
        pct(http.latencyMs, 50) as p50LatencyMs,
        pct(http.latencyMs, 95) as p95LatencyMs,
        max(http.latencyMs) as maxLatencyMs
  by http.method, http.path
| sort p95LatencyMs desc
| limit 20
```

## MongoDB explain 절차

1. 측정할 API와 query repository를 고릅니다.
2. 동일한 데이터셋을 준비합니다.
3. Mongo shell에서 API가 사용하는 query 조건과 sort를 재현합니다.
4. `explain("executionStats")` 결과의 `totalDocsExamined`, `totalKeysExamined`, `executionTimeMillis`를 기록합니다.
5. index 추가 또는 query 변경 후 같은 데이터셋으로 다시 측정합니다.

예시:

```javascript
db.assignments
  .find({ courseId: "COURSE_ID", weekNo: 1 })
  .sort({ weekNo: 1, orderInWeek: 1 })
  .explain("executionStats")
```

쿼리 튜닝 후보와 측정 보류 사유는 [Query Tuning](./query-tuning.md)에 별도로 기록했습니다.

## API benchmark 절차

1. 로컬 또는 staging 환경을 고정합니다.
2. MongoDB 데이터셋 크기, JVM option, event publish enabled 여부를 기록합니다.
3. 같은 JWT와 같은 request body를 사용합니다.
4. `wrk`, `k6`, `hey` 중 하나로 baseline을 측정합니다.
5. 변경 후 같은 조건으로 재측정합니다.
6. p50, p95, p99, error rate, throughput을 함께 기록합니다.

## before/after 표 템플릿

| 대상 | 조건 | Before | After | 변화 | 근거 |
| :--- | :--- | ---: | ---: | :--- | :--- |
| MongoDB query tuning | 로컬 `aandi` DB 컬렉션 없음 | 현재 before/after 측정값은 없습니다 | 현재 before/after 측정값은 없습니다 | 적용하지 않음 | [Query Tuning](./query-tuning.md) |

## 이력서 사용 기준

현재 쓰면 안 되는 문장:

- 쿼리 성능 N% 개선
- p95 latency N ms 단축
- SQS 처리량 N배 개선
- 장애 대응 시간 N분 단축

성능 수치를 쓰려면 CloudWatch Logs, benchmark report, MongoDB explain 결과를 함께 보관해야 합니다.
