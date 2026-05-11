# Report Server CloudWatch Logs

이 서버는 애플리케이션에서 CloudWatch appender를 직접 사용하지 않는다.
구조화 로그는 stdout에 JSON 한 줄로 출력하고, Docker `awslogs` logging driver가 CloudWatch Logs로 전송한다.

## IAM Role

EC2 인스턴스 또는 Docker 호스트의 IAM Role에는 최소한 다음 권한이 필요하다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams"
      ],
      "Resource": "*"
    }
  ]
}
```

운영 환경에서는 리소스를 `/a-and-i/prod/report*` ARN으로 좁히는 것을 권장한다.

## Log Groups

- report server: `/a-and-i/prod/report`
- MongoDB optional: `/a-and-i/prod/report-mongodb`

retention 예시:

```bash
aws logs put-retention-policy \
  --log-group-name /a-and-i/prod/report \
  --retention-in-days 14
```

## Docker Compose

운영 compose는 `docker-compose.prod.yml`을 사용한다.

```bash
REPORT_SERVER_IMAGE=ghcr.io/team-ani/a-and-i-web-server:latest \
MONGO_DB_URL=mongodb://mongodb:27017/aandi \
SWAGGER_URL=https://api.aandiclub.com \
docker compose -f docker-compose.prod.yml up -d
```

## Logs Insights Queries

최근 `API_ERROR`:

```sql
fields @timestamp, level, logType, service.name, trace.traceId, trace.requestId, http.method, http.path, http.statusCode, http.latencyMs, response.error.code, response.error.value, response.error.message
| filter logType = "API_ERROR"
| sort @timestamp desc
| limit 50
```

5xx만 조회:

```sql
fields @timestamp, trace.traceId, http.method, http.path, http.statusCode, http.latencyMs, response.error.code, response.error.message
| filter logType = "API_ERROR" and http.statusCode >= 500
| sort @timestamp desc
| limit 50
```

traceId 단위 조회:

```sql
fields @timestamp, level, logType, http.method, http.path, http.statusCode, http.latencyMs, response.error.code, message
| filter trace.traceId = "PUT_TRACE_ID_HERE"
| sort @timestamp asc
| limit 100
```

path별 에러 수:

```sql
fields http.path, http.statusCode, response.error.code
| filter logType = "API_ERROR"
| stats count(*) as errorCount by http.path, http.statusCode, response.error.code
| sort errorCount desc
| limit 30
```

느린 API:

```sql
fields @timestamp, http.method, http.path, http.statusCode, http.latencyMs, trace.traceId
| filter logType = "API" or logType = "API_ERROR"
| sort http.latencyMs desc
| limit 30
```
