# ADR 0001: 과제 변경과 problem sync 이벤트의 일관성

- Status: Proposed
- Date: 2026-07-10

## Context

과제 생성·수정·삭제는 여러 MongoDB 문서를 변경한 뒤 SNS로 problem sync 이벤트를 발행합니다. 현재 DB 변경과 SNS 발행은 하나의 원자적 작업이 아닙니다.

- SNS가 실패하면 API는 실패하지만 DB 변경은 이미 남을 수 있습니다.
- 클라이언트 재시도는 중복 쓰기 또는 중복 이벤트를 만들 수 있습니다.
- 삭제 이벤트가 실패하면 원본 snapshot이 이미 없어 재구성이 어렵습니다.

publisher 호출에 retry만 추가해도 프로세스 종료와 장기 장애 사이의 유실을 막을 수 없습니다.

## Decision

MongoDB transaction을 사용할 수 있는 replica set 또는 managed MongoDB 구성을 전제로 transactional outbox를 도입합니다.

과제 aggregate 변경과 outbox event 저장을 하나의 MongoDB transaction으로 커밋합니다. 별도 relay가 outbox를 SNS로 발행하고 성공 상태를 기록합니다. 전달 보장은 at-least-once로 두며 소비자는 `eventId`로 중복을 제거합니다.

삭제 이벤트는 삭제 전에 필요한 problem snapshot을 outbox payload에 저장합니다. relay가 삭제된 원본 문서를 다시 조회하지 않도록 합니다.

## Required contract

- 모든 이벤트에 전역적으로 유일한 `eventId` 포함
- aggregate ID, event type, schema version, occurredAt 포함
- relay 상태와 retry 시각을 조회할 index 제공
- 동일 `eventId` 재발행과 재소비가 안전한 idempotency 보장
- SENT 데이터 보존·삭제 정책과 DLQ 또는 terminal failure 상태 정의

## Rollout

0. 기존 wire payload와 실패·순서 계약을 고정하고 `AssignmentProblemSyncPort`/direct adapter 경계를 준비
1. 운영 MongoDB의 transaction 지원과 replica 상태 확인
2. transaction·rollback을 검증하는 MongoDB 통합 테스트 추가
3. outbox collection과 versioned index migration 추가
4. relay, 지수 backoff, metrics·alert 추가
5. 생성·수정·삭제 장애 주입 테스트 추가
6. direct publisher 경로를 outbox 저장으로 전환
7. 안정화 후 기존 직접 발행 코드 제거

전환 전에는 현재 이벤트 순서를 유지하고 direct publisher를 부분적으로 outbox와 혼용하지 않습니다.

## Consequences

장애 중에도 이벤트를 재전송할 수 있고 DB 변경과 이벤트 의도가 함께 남습니다. 대신 MongoDB transaction 운영 조건, relay 프로세스, outbox 정리, 소비자 idempotency와 모니터링이 추가됩니다.

## Rejected alternatives

- direct SNS publish 재시도만 추가: 프로세스 종료와 장기 장애에서 유실 가능
- DB 저장 후 별도 outbox insert: 두 DB 작업 사이가 다시 dual write
- SNS 선발행 후 DB 저장: 소비자가 아직 존재하지 않는 상태를 관찰 가능
- 이번 리팩터링에서 즉시 구현: 운영 MongoDB transaction 조건과 장애 테스트가 확인되지 않아 안전 기준 미충족
