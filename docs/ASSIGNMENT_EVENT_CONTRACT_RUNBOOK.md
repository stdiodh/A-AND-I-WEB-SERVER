# 과제 이벤트 계약과 outbox 전환 기준

## 목적과 범위

이 문서는 이 저장소에서 확인할 수 있는 현재 과제 이벤트 계약과, transactional outbox로 전환하기 전에 충족해야 할 조건을 구분합니다. 외부 Online Judge 저장소와 실제 AWS/MongoDB 운영 설정은 여기서 확인할 수 없으므로 추정하지 않습니다.

- Report → Online Judge 방향: assignment problem sync 발행
- Online Judge → Report 방향: `JUDGE_COMPLETED` 수신과 submission projection 갱신
- 기준일: 2026-07-11 KST
- 목표 설계: [ADR 0001](./adr/0001-assignment-event-consistency.md)

## 현재 계약 요약

| 항목 | 현재 확인된 상태 |
| :--- | :--- |
| problem sync `eventId` | 없음 |
| `JUDGE_COMPLETED` `eventId` | 없음 |
| `schemaVersion` | 없음. 현재 payload는 version 필드가 없는 사실상 v0 계약 |
| aggregate `sequence` | 없음 |
| problem sync 활성화 | `APP_EVENTS_REPORT_TEST_CASE_ENABLED=false`가 기본값. 비활성 시 no-op publisher |
| Judge consumer 활성화 | `REPORT_JUDGE_SUBMISSION_EVENTS_ENABLED=false`가 기본값. 비활성 시 consumer bean 없음 |
| SNS FIFO group | topic ARN이 `.fifo`일 때 `messageGroupId=problemId` |
| SNS FIFO dedup ID | publish 호출마다 새 UUID. 논리 이벤트의 안정적인 ID가 아님 |
| 논리 이벤트 중복 제거 | producer와 이 서버의 Judge consumer 모두 없음 |
| MongoDB transaction | 생성·수정·복사 child write는 순차화했지만 transaction manager/unit of work가 없고, delayed-error 삭제 경로와 운영 replica-set 지원도 미확인 |
| outbox/relay | 미구현 |

## Report → Online Judge: problem sync

### Wire payload

현재 JSON 필드는 다음과 같습니다.

```json
{
  "eventType": "PROBLEM_CREATED",
  "problemId": "assignment-id",
  "testCases": [
    {
      "caseId": 1,
      "input": ["value"],
      "output": "expected"
    }
  ]
}
```

| 필드 | 현재 의미 |
| :--- | :--- |
| `eventType` | `PROBLEM_CREATED`, `PROBLEM_UPDATED`, `PROBLEM_DELETED` |
| `problemId` | assignment ID |
| `testCases` | `EXCLUDED`를 제외하고 `caseId` 오름차순으로 만든 testcase snapshot |

생성·수정은 assignment 저장 뒤 repository에서 최신 testcase snapshot을 다시 읽어 발행합니다. 삭제 payload는 `problemId`와 빈 `testCases`이며, 삭제 전 원본 전체 snapshot을 포함하지 않습니다.

### Publish 동작

- 기본 설정에서는 no-op publisher 단계가 성공 완료되어 publish 때문에 command가 실패하지 않습니다.
- 기능이 활성화된 환경에서 command 경로는 SNS publish 완료를 기다립니다.
- 활성 환경의 SNS publish 실패는 API 호출자에게 전파되지만, 앞서 완료된 MongoDB 변경은 남을 수 있습니다.
- SNS subject는 `eventType` 이름입니다.
- FIFO topic이면 `messageGroupId=problemId`를 사용하지만 dedup ID는 매 호출 새 UUID입니다.
- 애플리케이션 수준 direct publish retry, durable outbox, relay 재처리 상태는 없습니다.

따라서 활성 환경에서 이 저장소가 보장하는 범위는 SNS publish 요청과 그 성공/실패 전파까지입니다. SNS 이후 SQS 전달, Online Judge 반영, consumer unknown-field 호환성은 외부 시스템에서 별도로 확인해야 합니다.

## Online Judge → Report: `JUDGE_COMPLETED`

### 수신 payload

consumer는 raw JSON과 SNS envelope의 `Message` JSON을 모두 처리합니다.

```json
{
  "eventType": "JUDGE_COMPLETED",
  "problemId": "assignment-id",
  "publicCode": "#FL301",
  "score": 100,
  "passedCases": 10,
  "totalCases": 10,
  "timestamp": "2026-07-11T00:00:00Z"
}
```

| 필드 | 현재 검증 |
| :--- | :--- |
| `eventType` | 정확히 `JUDGE_COMPLETED` |
| `problemId`, `publicCode` | trim 뒤 nonblank |
| `score`, `passedCases`, `totalCases` | null 여부만 확인. 범위와 상호 관계는 검증하지 않음 |
| `timestamp` | 역직렬화 가능한 non-null `Instant` |

알 수 없는 필드는 무시합니다. 반면 잘못된 JSON, 지원하지 않는 `eventType`, 필수 필드 누락은 `Ignored`로 분류한 뒤 현재 SQS 메시지를 삭제합니다. 새 필드를 필수화하기 전에 producer 전환 순서와 영구 폐기 위험을 먼저 검토해야 합니다.

### Projection과 ACK 동작

- Judge consumer는 기본 비활성이며, 활성 환경에서만 SQS polling과 아래 처리를 수행합니다.
- projection 저장이 성공한 뒤에만 SQS `deleteMessage`를 호출합니다.
- projection 저장 또는 message 삭제가 실패하면 메시지가 다시 보일 수 있습니다.
- MongoDB index V001에는 `assignmentId + publicCode` unique index가 정의되어 있습니다. 실제 적용·검증된 환경에서만 중복 projection 문서 생성을 막는다고 볼 수 있습니다.
- optimistic-lock 충돌 재시도는 동시 갱신을 수렴시키기 위한 것이며 event deduplication이 아닙니다.
- 같은 논리 이벤트가 재전달되면 문서는 하나여도 `updatedAt`과 version이 다시 바뀔 수 있습니다.

## 현재 보장하지 않는 것

- MongoDB 변경과 SNS 발행의 원자성
- 무손실 전달 또는 exactly-once 처리
- 안정적인 `eventId` 기반 producer/consumer 중복 제거
- 동일 assignment의 전역 monotonic 순서
- outbox `PENDING`/`IN_FLIGHT`/`SENT`/`FAILED`, lease, retry, 수동 재처리
- 실제 AWS topic/queue의 FIFO 연결, DLQ/redrive, retention, alarm
- 운영 MongoDB의 replica-set topology와 transaction commit/rollback
- 외부 Online Judge가 현재 payload를 실제 처리하는지와 additive field를 허용하는지

## 목표 이벤트 계약

아래는 현재 구현이 아니라 outbox 전환 전에 합의해야 하는 목표 계약입니다.

| 필드/정책 | 결정할 내용 |
| :--- | :--- |
| `eventId` | 생성 주체, 불변성, retry/relay 재발행 시 동일 ID 유지 |
| `schemaVersion` | additive/breaking change 규칙과 지원 기간 |
| aggregate sequence | producer가 동일 assignment마다 증가시키고 consumer가 낮은 sequence를 무시할 기준 |
| consumer dedupe | persistent marker, payload hash 충돌 처리, 보존 기간 |
| delivery semantics | at-least-once를 기준으로 하고 exactly-once 표현 금지 |
| terminal failure | retry 상한, `FAILED`, DLQ, 수동 requeue와 reconciliation |

## Outbox 전환 차단조건

다음 항목이 확인되기 전에는 direct publisher와 outbox를 혼용하거나 payload 필드를 필수화하지 않습니다.

1. Online Judge의 현재 payload 계약과 unknown-field 호환성을 확인합니다.
2. problem sync consumer의 persistent `eventId` dedupe와 보존 기간을 확인합니다.
3. 동일 assignment의 monotonic sequence 생성·저장과 낮은 sequence 무시 정책을 결정합니다.
4. Judge producer의 `eventId` 생성·재시도 안정성을 확인합니다.
5. 운영 MongoDB topology를 확인하고 실제 replica set에서 commit/rollback을 검증합니다.
6. transaction 안의 MongoDB write를 순차 실행하도록 준비합니다. 생성·수정·복사의 child write는 완료했으며, delayed-error cascade/cleanup 삭제 세 경로는 별도 전환이 필요합니다.
7. aggregate 저장과 outbox 저장의 양방향 rollback 통합 테스트를 통과합니다.
8. relay retry가 동일 `eventId`를 유지하고 다중 인스턴스 claim·lease 만료를 처리하는지 검증합니다.
9. direct/outbox를 동시에 켤 수 없는 단일 cutover switch와 fail-fast 설정 검증을 둡니다.
10. rollback 전에 pending outbox drain/pause와 순서 역전 방지 절차를 정합니다.

## 검증 체크리스트

### 계약 확인

- [ ] Online Judge consumer owner와 wire schema를 확인했다.
- [ ] additive field와 unknown-field 처리 결과를 양쪽 저장소 테스트로 확인했다.
- [ ] 같은 `eventId`/같은 payload와 같은 `eventId`/다른 payload 정책을 정했다.
- [ ] 동일 assignment sequence의 생성·증가 기준과 낮은 sequence 무시 정책을 정했다.

### MongoDB와 relay

- [x] 과제 생성·수정·복사의 requirements → testCases write를 실제 subscription 기준으로 순차화했다.
- [ ] cascade/cleanup 삭제 세 경로를 전체 시도·오류 보존 계약과 함께 순차 구독으로 전환했다.
- [ ] 실제 replica set에서 commit과 강제 rollback을 검증했다.
- [ ] collection/index를 transaction 전에 생성하는 migration 순서를 정했다.
- [ ] SNS 성공 후 `SENT` 기록 전 종료 시 동일 ID 재발행을 검증했다.
- [ ] 두 relay가 같은 event를 동시에 claim하지 못하는지 검증했다.
- [ ] pending/failed 수, oldest pending age, publish lag, retry, lease reclaim alarm을 정했다.

### Cutover와 rollback

- [ ] direct/outbox 혼용이 설정 검증에서 차단된다.
- [ ] 장애 주입 후 pending event를 조회·재처리·대조할 수 있다.
- [ ] rollback이 새 direct event로 pending outbox event를 추월하지 않는다.

## 코드 근거

- outbound payload: [AssignmentReportTestCaseEvent.kt](../src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/event/AssignmentReportTestCaseEvent.kt)
- outbound mapping: [AssignmentReportTestCaseEventMapper.kt](../src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/event/AssignmentReportTestCaseEventMapper.kt)
- SNS publish/FIFO 설정: [SnsAssignmentReportTestCaseEventPublisher.kt](../src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/event/SnsAssignmentReportTestCaseEventPublisher.kt)
- inbound parsing: [JudgeCompletedEventParser.kt](../src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/submission/event/JudgeCompletedEventParser.kt)
- SQS 처리와 삭제 시점: [SqsJudgeSubmissionEventConsumer.kt](../src/main/kotlin/com/example/aandi_post_web_server/assignment/infrastructure/submission/event/SqsJudgeSubmissionEventConsumer.kt)
- projection 갱신: [AssignmentSubmissionStatusProjectionService.kt](../src/main/kotlin/com/example/aandi_post_web_server/assignment/application/submission/service/AssignmentSubmissionStatusProjectionService.kt)

이 문서는 현재 계약의 기준입니다. 목표 설계가 구현되면 코드·migration·통합 테스트와 함께 같은 PR에서 상태 표와 체크리스트를 갱신합니다.
