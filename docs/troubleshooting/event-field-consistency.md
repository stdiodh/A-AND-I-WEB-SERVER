# Event Field Consistency

> 상위 문서로 돌아가기: [Troubleshooting](./README.md)

이벤트 필드명은 WEB, OJ, AUTH 서버 사이의 계약입니다. 필드 의미가 흔들리면 메시지는 도착해도 동기화가 실패할 수 있습니다.

## Assignment problem sync

| 필드 | 기준 |
| :--- | :--- |
| `eventType` | `PROBLEM_CREATED`, `PROBLEM_UPDATED`, `PROBLEM_DELETED` |
| `problemId` | WEB assignment id |
| `testCases[].caseId` | testcase seq |
| `testCases[].input` | input values 배열 |
| `testCases[].output` | expected output |

주의:

- `PROBLEM_DELETED`는 `testCases: []`를 사용합니다.
- `EXCLUDED` visibility testcase는 problem sync payload에 포함하지 않습니다.

## Judge completed

| 필드 | 기준 |
| :--- | :--- |
| `eventType` | `JUDGE_COMPLETED` |
| `publicCode` | 수강생 공개 코드 |
| `problemId` | WEB assignment id로 해석 |
| `score` | 정수 점수 |
| `passedCases` | 통과 케이스 수 |
| `totalCases` | 전체 케이스 수 |
| `timestamp` | UTC ISO-8601 Instant |

주의:

- `problemId`라는 이름을 쓰지만 WEB 내부에서는 `assignmentId`로 저장합니다.
- `eventType`이 다르면 consumer는 처리하지 않고 ignored reason 로그를 남깁니다.

## Auth user sync

| 필드 | 기준 |
| :--- | :--- |
| `eventType` or `type` | `UserProfileUpdated`, `UserDeleted` |
| `id` or `userId` | Auth user id |
| `publicCode` | 수강/제출 현황 join에 쓰는 공개 코드 |
| `username` | 기본 표시 이름 |
| `nickname` | 있으면 관리자 제출 현황에서 username보다 우선 |
| `updatedAt` | freshness 판단 기준 |
| `occurredAt` | `updatedAt`이 없을 때 대체 기준 |

## 변경 전 체크리스트

- [ ] 생산자와 소비자가 같은 `eventType` casing을 사용하는가?
- [ ] `problemId`가 assignment id를 의미한다는 점이 OJ와 WEB 모두에 반영되어 있는가?
- [ ] SQS raw message delivery 설정 변경에도 direct JSON/SNS envelope 테스트가 통과하는가?
- [ ] private testcase input/output 또는 user code 원문이 로그에 남지 않는가?
- [ ] README 또는 resume에 수치가 필요하면 실제 테스트/리포트가 있는가?

## 관련 문서

- [Event-driven Sync](../event-driven-sync.md)
- [Problem Sync](../api-flows/problem-sync.md)
- [Judge Completed](../api-flows/judge-completed.md)
- [Auth User Sync](../api-flows/auth-user-sync.md)
