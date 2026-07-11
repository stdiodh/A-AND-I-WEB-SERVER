# ADR 0001: 과제 변경과 problem sync 이벤트의 일관성

- Status: Proposed
- Date: 2026-07-10
- Scope: 목표 설계. 현재 구현 계약이 아님

## Context

과제 생성·수정·삭제는 여러 MongoDB 문서를 변경한 뒤 SNS로 problem sync 이벤트를 발행합니다. 현재 DB 변경과 SNS 발행은 하나의 원자적 작업이 아닙니다.

- SNS가 실패하면 API는 실패하지만 DB 변경은 이미 남을 수 있습니다.
- 클라이언트 재시도는 중복 쓰기 또는 중복 이벤트를 만들 수 있습니다.
- 삭제 뒤 SNS 발행이 실패하면 durable한 delete event intent가 남지 않고, 같은 API 재시도는 이미 삭제된 assignment에 대해 404가 될 수 있습니다.

publisher 호출에 retry만 추가해도 프로세스 종료와 장기 장애 사이의 유실을 막을 수 없습니다.

현재 wire payload, ACK 동작, 미보장 범위와 전환 차단조건은 [과제 이벤트 계약과 outbox 전환 기준](../ASSIGNMENT_EVENT_CONTRACT_RUNBOOK.md)을 따릅니다.

## Current implementation

현재 problem sync 기능은 기본 비활성이며 no-op publisher를 사용합니다. 활성 환경에서는 MongoDB 저장 뒤 command 경로에서 SNS direct publish 완료를 기다립니다. Judge consumer도 기본 비활성입니다. problem sync와 `JUDGE_COMPLETED` 모두 안정적인 `eventId`, `schemaVersion`, aggregate sequence가 없으며 outbox collection, relay, processed-event store도 없습니다. 이 ADR의 outbox와 idempotency 항목을 구현 완료로 해석하지 않습니다.

## Decision

아래 전제조건을 검증한 뒤 MongoDB transaction을 사용할 수 있는 replica set 또는 managed MongoDB 구성에서 transactional outbox를 도입합니다.

과제 aggregate 변경과 outbox event 저장을 하나의 MongoDB transaction으로 커밋합니다. 별도 relay가 outbox를 SNS로 발행하고 성공 상태를 기록합니다. 전달 보장은 at-least-once로 두며 소비자는 `eventId`로 중복을 제거합니다.

삭제 이벤트는 삭제 전에 exact outbound payload 또는 재발행 가능한 event intent를 outbox에 저장합니다. relay가 삭제된 원본 문서를 다시 조회하지 않도록 합니다.

## Required target contract

- problem sync 이벤트에 전역적으로 유일한 `eventId` 포함
- aggregate ID, event type, schema version, occurredAt 포함
- relay 상태와 retry 시각을 조회할 index 제공
- 동일 `eventId` 재발행과 재소비가 안전한 idempotency 보장
- SENT 데이터 보존·삭제 정책과 DLQ 또는 terminal failure 상태 정의
- 동일 aggregate의 순서 정책과 낮은 sequence 무시 기준 정의
- direct publisher와 outbox를 동시에 활성화할 수 없는 cutover 설정

## Rollout

0. 현재 wire payload와 실패·publisher 호출 순서를 고정하고 `AssignmentProblemSyncPort`/direct adapter 경계를 준비
1. 외부 Online Judge의 schema 호환성과 persistent `eventId` dedupe·순서 정책 확인
2. 운영 MongoDB의 transaction 지원과 replica 상태 확인
3. 실제 replica set에서 transaction commit·rollback 통합 테스트 추가
4. transaction 내부 MongoDB write를 순차 실행하도록 변경 — 현재 assignment·course command write 선행 준비 완료
5. outbox collection과 versioned index migration 추가
6. relay, lease, 지수 backoff, metrics·alert 추가
7. 생성·수정·삭제와 relay 장애 주입 테스트 추가
8. direct/outbox 상호 배타 설정으로 단일 cutover
9. 안정화 후 기존 직접 발행 코드 제거

전환 전에는 현재 애플리케이션 publisher 호출 순서를 유지하고 direct publisher를 부분적으로 outbox와 혼용하지 않습니다. 상세 gate와 rollback 조건은 runbook 체크리스트를 통과해야 합니다.

## Consequences

목표 설계를 적용하면 장애 중에도 이벤트를 재전송할 수 있고 DB 변경과 이벤트 의도가 함께 남습니다. 대신 MongoDB transaction 운영 조건, relay 프로세스, outbox 정리, 소비자 idempotency와 모니터링이 추가됩니다.

## Rejected alternatives

- direct SNS publish 재시도만 추가: 프로세스 종료와 장기 장애에서 유실 가능
- DB 저장 후 별도 outbox insert: 두 DB 작업 사이가 다시 dual write
- SNS 선발행 후 DB 저장: 소비자가 아직 존재하지 않는 상태를 관찰 가능
- 이번 리팩터링에서 즉시 구현: 운영 MongoDB transaction 조건과 장애 테스트가 확인되지 않아 안전 기준 미충족
