# SQS Envelope Format

> 상위 문서로 돌아가기: [Troubleshooting](./README.md)

SQS 메시지는 구독 설정에 따라 direct JSON body 또는 SNS notification envelope body로 들어올 수 있습니다. 이 저장소는 judge completed와 auth user sync에서 두 형식을 모두 지원합니다.

## direct JSON body

```json
{
  "eventType": "JUDGE_COMPLETED",
  "publicCode": "A00123",
  "problemId": "assignment-id",
  "score": 100,
  "passedCases": 10,
  "totalCases": 10,
  "timestamp": "2026-04-09T02:15:30.123Z"
}
```

## SNS envelope body

```json
{
  "Type": "Notification",
  "Message": "{\"eventType\":\"JUDGE_COMPLETED\",\"publicCode\":\"A00123\",\"problemId\":\"assignment-id\",\"score\":100,\"passedCases\":10,\"totalCases\":10,\"timestamp\":\"2026-04-09T02:15:30.123Z\"}"
}
```

## 구현 기준

| Parser | 기준 |
| :--- | :--- |
| `JudgeCompletedEventParser` | root object에 `Message`가 있으면 SNS envelope로 보고 `Message`를 JSON으로 재파싱 |
| `AuthUserEventParser` | root object에 `Message`가 있으면 SNS envelope로 보고 `Message`를 JSON으로 재파싱 |

## 삭제 기준 차이

| Consumer | ignored message | 처리 예외 |
| :--- | :--- | :--- |
| Judge completed | reason 로그 후 `deleteMessage` | error 로그 후 삭제하지 않음 |
| Auth user sync | parser가 예외를 던지므로 error 로그 후 삭제하지 않음 | error 로그 후 삭제하지 않음 |

## 점검 순서

1. SQS body가 direct JSON인지 SNS envelope인지 확인합니다.
2. envelope라면 `Message` 문자열이 비어 있지 않은지 확인합니다.
3. `Message` 문자열 내부 JSON escaping이 올바른지 확인합니다.
4. judge completed는 `eventType=JUDGE_COMPLETED`와 필수 필드를 확인합니다.
5. auth user sync는 `eventType=UserProfileUpdated` 또는 `UserDeleted`인지 확인합니다.

## 관련 테스트

- `JudgeCompletedEventParserTest`
- `AuthUserEventParserTest`
- `SqsJudgeSubmissionEventConsumerTest`
- `SqsUserEventConsumerTest`
