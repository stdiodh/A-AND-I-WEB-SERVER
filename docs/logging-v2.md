# V2 Structured Logging

Report server의 V2 API 로그는 stdout에 한 줄 JSON으로 출력된다.
CloudWatch 전송은 애플리케이션 appender가 아니라 Docker `awslogs` logging driver가 담당한다.

## 대상 경로

기본 포함 경로:

- `/v2`
- `/api/v2`

기본 제외 경로:

- `/actuator/**`
- `/swagger-ui/**`
- `/v3/api-docs/**`
- `/favicon.ico`
- static resource 경로

## Schema

주요 필드:

- `@timestamp`: Asia/Seoul offset datetime
- `level`: 2xx/3xx `INFO`, 4xx `WARN`, 5xx `ERROR`
- `logType`: 2xx/3xx `API`, 4xx/5xx `API_ERROR`
- `env`: `APP_ENV`
- `service`: `report-service`, domain code `4`, version, instance id
- `trace`: `traceId`, `requestId`
- `http`: method, path, route, statusCode, latencyMs
- `headers`: `deviceOS`, `Authenticate: null`, `timestamp`, `salt: null`
- `client`: ip, userAgent, appVersion
- `actor`: userId, role, isAuthenticated
- `request`: sanitized query, pathVariables, body
- `response`: success, sanitized data, error, timestamp
- `tags`: route 기반 태그와 `success` 또는 `fail`

## Masking Policy

원문 로그 금지:

- password, token, accessToken, refreshToken
- authorization, Authenticate, salt
- secret, credential, privateKey, clientSecret
- session, cookie
- private/hidden testcase
- expectedOutput
- 사용자 제출 코드
- OJ 입력/출력 원문 중 private/hidden 성격의 데이터

부분 마스킹:

- email
- phone
- loginId
- userName

body 처리:

- `maxBodyBytes` 초과 시 body 원문 대신 summary를 남긴다.
- multipart, image, audio, video, octet-stream은 body 원문을 남기지 않는다.
- JSON 파싱 실패 시 raw text를 남기지 않고 summary만 남긴다.

## Environment Variables

- `APP_ENV`
- `APP_V2_LOG_SERVICE_NAME`
- `APP_V2_LOG_DOMAIN_CODE`
- `APP_VERSION`
- `APP_V2_LOG_INSTANCE_ID`
- `APP_V2_LOG_MAX_BODY_BYTES`
- `APP_V2_LOG_INCLUDE_PATH_PREFIXES`
- `APP_V2_LOG_EXCLUDE_PATH_PREFIXES`
- `AWS_REGION`

운영 기본값:

- service name: `report-service`
- domain code: `4`
- max body bytes: `8192`
- CloudWatch log group: `/a-and-i/prod/report`

## Local Check

```bash
./gradlew bootRun
```

다른 터미널에서 v2 요청을 보낸다.

```bash
curl -i http://localhost:8080/v2/courses \
  -H 'Authorization: Bearer LOCAL_TOKEN' \
  -H 'deviceOS: ios' \
  -H 'timestamp: 2026-04-14T20:31:12.335+09:00'
```

stdout에서 `{"@timestamp":...}`로 시작하는 JSON 한 줄을 확인한다.

## Production Check

```bash
docker compose -f docker-compose.prod.yml up -d
```

CloudWatch Logs에서 `/a-and-i/prod/report` log group을 확인한다.

자주 쓰는 Logs Insights 쿼리는 `docs/cloudwatch-report-server.md`를 참고한다.
