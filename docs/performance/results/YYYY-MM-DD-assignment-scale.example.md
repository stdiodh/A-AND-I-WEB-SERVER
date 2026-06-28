# Assignment Scale Performance

> 로컬 MongoDB `aandi_performance`와 localhost API만 대상으로 한 고정 부하 회귀 검증 결과입니다. 운영 최대 처리량으로 해석하지 않습니다.

- Generated At: `YYYY-MM-DDT00:00:00Z`
- Java/JVM: `openjdk version "21.x"`
- Scope: local-only assignment read scale fixture
- Comparison: `[비교 불가]` - before/after 조건을 완전히 동일하게 맞춘 비교 입력이 아니므로 latency 개선율을 계산하지 않습니다.

## Safety

- BASE_URL은 `localhost` 또는 `127.0.0.1`만 허용합니다.
- MongoDB는 `mongodb://localhost:27017/aandi_performance` 또는 `127.0.0.1`의 동일 DB만 허용합니다.
- `TARGET_ENVIRONMENT=prod`, `production`, `staging`은 실행 실패 처리합니다.
- `aandiclub.com`, `api.aandiclub.com`, AWS SNS/SQS, CloudWatch, production Discord webhook, production DB, EC2 public IP는 사용하지 않습니다.

## k6 Median

| Scenario | Commit | Fixture | k6 | Machine | P50 | P90 | P95 | P99 | HTTP failed | Checks | Throughput | Iterations | Dropped |
| :--- | :--- | :--- | :--- | :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| assignment-300 | `abcdef1` | `fixture-hash-300` | `k6 v0.52.0` | local CPU / local memory | 0.00 ms | 0.00 ms | 0.00 ms | 0.00 ms | 0.00% | 100.00% | 100.00 req/s | 12000 | 0 |
| assignment-1000 | `abcdef1` | `fixture-hash-1000` | `k6 v0.52.0` | local CPU / local memory | 0.00 ms | 0.00 ms | 0.00 ms | 0.00 ms | 0.00% | 100.00% | 100.00 req/s | 12000 | 0 |

## MongoDB Profile Median

| Scenario | MongoDB | Command count | Docs examined | Keys examined | Explain docs | Explain keys |
| :--- | :--- | ---: | ---: | ---: | ---: | ---: |
| assignment-300 | 7.x | 0 | 0 | 0 | 0 | 0 |
| assignment-1000 | 7.x | 0 | 0 | 0 | 0 | 0 |

## Resume Sentence

- 확인된 경우: 300/1000개 과제 fixture에서 k6 고정 부하와 MongoDB profile로 읽기 API P95/P99와 DB 접근 효율을 로컬 재현 환경에서 검증
- 측정 전: 과제 조회 API의 scale fixture 성능 측정 환경을 구축해 P95/P99와 DB 접근 패턴을 회귀 기준으로 관리
