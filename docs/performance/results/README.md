# Performance Results

로컬 재현 환경에서 생성한 성능 측정 결과를 보관하는 위치입니다.

## Assignment scale

300/1000개 assignment fixture 기반 읽기 API 측정은 아래 스크립트로 실행합니다.

```bash
docker compose up -d mongodb
python3 performance/k6/tools/generate_test_jwt.py --output performance/k6/env.local

MONGO_DB_URL=mongodb://localhost:27017/aandi_performance \
APP_V2_LOG_EXCLUDE_PATH_PREFIXES=/actuator,/swagger-ui,/v3/api-docs,/favicon.ico,/static,/assets,/webjars,/v2 \
./gradlew bootRun
```

다른 터미널에서 실행합니다.

```bash
K6_BIN_DIR=/tmp/aandi-k6-0.52.0
PATH="$K6_BIN_DIR:$PATH" performance/scripts/run-assignment-scale-local.sh
```

스크립트는 다음 파일을 생성합니다.

- `performance/results/assignment-scale/YYYY-MM-DD/...`: k6 summary JSON/Markdown, aggregate JSON/Markdown, MongoDB profile JSON/Markdown
- `docs/performance/results/YYYY-MM-DD-assignment-scale.json`: 문서 링크용 통합 JSON
- `docs/performance/results/YYYY-MM-DD-assignment-scale.md`: 문서 링크용 통합 Markdown

## Safety rules

- `BASE_URL`은 `localhost` 또는 `127.0.0.1`만 허용합니다.
- MongoDB는 `aandi_performance` DB만 허용합니다.
- `TARGET_ENVIRONMENT=prod`, `production`, `staging`은 실행 실패 처리합니다.
- `aandiclub.com`, `api.aandiclub.com`, remote target, production DB, EC2 public IP, AWS SNS/SQS, CloudWatch, production Discord webhook은 사용하지 않습니다.

## Interpretation

이 결과는 운영 최대 처리량이 아니라 고정 부하 조건에서의 회귀 검증 기준입니다.

latency 개선율은 before/after fixture, commit, JVM, MongoDB, k6, machine, load model이 모두 같을 때만 계산합니다. 조건이 다르면 `[비교 불가]`로 표시합니다.
