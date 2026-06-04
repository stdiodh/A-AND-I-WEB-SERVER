# Troubleshooting

> 상위 문서로 돌아가기: [Docs Index](../README.md)

본 디렉터리는 이벤트 동기화와 과제 운영에서 재발 가능성이 있는 문제를 문서화합니다.

| 문서 | 설명 |
| :--- | :--- |
| [SQS Envelope Format](./sqs-envelope-format.md) | SQS body가 direct JSON인지 SNS envelope인지에 따른 처리 기준 |
| [Assignment Copy](./assignment-copy.md) | 과제 복사 중복, cleanup, problem sync 재발행 기준 |
| [Event Field Consistency](./event-field-consistency.md) | 이벤트 필드명, enum, id 의미를 일관되게 유지하는 기준 |

## 공통 원칙

- 운영 메시지 예시는 account ID, queue URL, token, publicCode를 데모 값으로 치환합니다.
- 실패 원인은 로그와 코드 경로를 함께 남깁니다.
- 성능 개선률이나 장애 대응 시간 단축률은 측정값 없이는 작성하지 않습니다.
