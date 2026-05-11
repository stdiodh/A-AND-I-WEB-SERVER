# Discord Alerts From CloudWatch

이 리포지토리에서는 Discord bot 또는 알림 Lambda 전체를 구현하지 않는다.
이 리포의 책임은 CloudWatch에서 안전하게 조회하고 전달할 수 있는 구조화 운영 로그를 만드는 것이다.

권장 흐름:

```text
Report Server stdout JSON
  -> Docker awslogs driver
  -> CloudWatch Logs
  -> Subscription filter
  -> Lambda
  -> Discord Webhook
```

## Subscription Filter Examples

5xx만 Discord로 전송:

```text
{ $.logType = "API_ERROR" && $.http.statusCode >= 500 }
```

특정 v2 error code만 전송:

```text
{ $.logType = "API_ERROR" && $.response.error.code = 50001 }
```

필터는 `/a-and-i/prod/report` log group에 연결한다.

## Discord Message Fields

알림 메시지에는 다음 필드만 포함한다.

- `env`
- `service.name`
- `level`
- `http.method`
- `http.path`
- `http.statusCode`
- `http.latencyMs`
- `trace.traceId`
- `trace.requestId`
- `response.error.code`
- `response.error.value`
- `response.error.message`

절대 포함하지 말아야 할 필드:

- `request.body` 원문 전체
- `response.data` 원문 전체
- 토큰
- 인증 헤더
- 테스트케이스 원문
- 사용자 제출 코드

Lambda에서 Discord payload를 만들 때도 allowlist 방식으로 위 필드만 추출한다. CloudWatch 로그에는 sanitizer가 적용되지만, 알림 계층에서 다시 한번 request/response payload 전체 전달을 금지해야 한다.
