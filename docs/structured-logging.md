# Structured Logging

> 메인 README로 돌아가기: [README](../README.md)

본 문서는 v2 API 구조화 로그와 CloudWatch/Discord alert 연계 기준을 정리합니다.

## 구현 위치

| 파일 | 역할 |
| :--- | :--- |
| `V2StructuredLoggingWebFilter.kt` | v2 API 요청/응답 body snapshot, latency, statusCode 수집 |
| `V2StructuredLogFormatter.kt` | JSON log payload 생성 |
| `V2StructuredAccessLog.kt` | 로그 스키마 |
| `V2StructuredLogSanitizer.kt` | 민감 필드 마스킹 |
| `logback-spring.xml` | `AANDI_V2_STRUCTURED_STDOUT` logger를 stdout JSON 한 줄로 출력 |
| `docker-compose.prod.yml` | Docker `awslogs` logging driver로 CloudWatch Logs 전송 |

## 로그 스키마 핵심 필드

| 필드 | 설명 |
| :--- | :--- |
| `@timestamp` | Asia/Seoul 기준 ISO offset time |
| `level` | `INFO`, `WARN`, `ERROR` |
| `logType` | 성공은 `API`, 실패는 `API_ERROR` |
| `trace.traceId` | 요청 추적 ID |
| `trace.requestId` | 요청 단위 ID |
| `http.method` | HTTP method |
| `http.path` | 실제 요청 path |
| `http.route` | Spring best matching route 또는 path |
| `http.statusCode` | 응답 status code |
| `http.latencyMs` | 요청 시작부터 로그 완료까지 ms |
| `response.error.code` | 공통 error code |
| `response.error.message` | 오류 메시지 |
| `response.error.value` | 오류 value |
| `response.error.alert` | alert field |

## statusCode별 level

| 조건 | level | logType |
| :--- | :--- | :--- |
| `statusCode < 400` | `INFO` | `API` |
| `400 <= statusCode < 500` | `WARN` | `API_ERROR` |
| `statusCode >= 500` | `ERROR` | `API_ERROR` |

## 마스킹 기준

원문 출력 금지 기준은 `V2StructuredLogSanitizer`에 있습니다.

| 분류 | 기준 |
| :--- | :--- |
| secret | `password`, `token`, `accessToken`, `refreshToken`, `authorization`, `secret`, `credential`, `privateKey`, `cookie` 등 |
| nullable secret | `Authenticate`, `salt`는 `null`로 출력 |
| partial mask | `email`, `phone`, `loginId`, `username` |
| hidden testcase | `privateTestCases`, `hiddenTestCases`, `hiddenCase`, hidden/private visibility payload |
| code/testcase payload | `input`, `output`, `expectedOutput`, `code`, `sourceCode`, `submittedCode`, `userCode` |

## CloudWatch Logs 탐색 예시

5xx 또는 API error 조회:

```sql
fields @timestamp, level, logType, service.name, trace.traceId, trace.requestId,
       http.method, http.path, http.statusCode, http.latencyMs,
       response.error.code, response.error.message, message
| filter logType = "API_ERROR" or http.statusCode >= 500
| sort @timestamp desc
| limit 50
```

traceId 단위 조회:

```sql
fields @timestamp, level, trace.traceId, trace.requestId,
       http.method, http.path, http.statusCode, http.latencyMs,
       response.error.code, response.error.message
| filter trace.traceId = "PUT_TRACE_ID_HERE"
| sort @timestamp asc
```

느린 요청 후보 조회:

```sql
fields @timestamp, http.method, http.path, http.statusCode, http.latencyMs, trace.traceId
| filter ispresent(http.latencyMs)
| sort http.latencyMs desc
| limit 50
```

## Discord alert 연계 기준

이 저장소는 Discord bot 또는 alert Lambda 전체를 구현하지 않습니다. 이 저장소의 책임은 CloudWatch에서 안전하게 조회하고 전달할 수 있는 구조화 로그를 생성하는 것입니다.

권장 subscription filter:

```text
{ $.logType = "API_ERROR" && $.http.statusCode >= 500 }
```

Discord payload allowlist:

| 필드 | 이유 |
| :--- | :--- |
| `env` | 운영/스테이징 구분 |
| `service.name` | 서비스 구분 |
| `http.method`, `http.path` | 실패 API 식별 |
| `http.statusCode`, `http.latencyMs` | 장애/지연 판단 |
| `trace.traceId`, `trace.requestId` | 후속 추적 |
| `response.error.code`, `response.error.message` | 오류 분류 |

알림 계층에서도 request/response payload 전체 전달은 금지합니다.

## 관련 문서

- [Logging v2](./logging-v2.md)
- [CloudWatch Report Server](./cloudwatch-report-server.md)
- [Discord Alert From CloudWatch](./discord-alert-from-cloudwatch.md)
