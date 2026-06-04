# API / Event Flow Index

> 상위 문서로 돌아가기: [Docs Index](../README.md)

본 디렉터리는 README에서 분리한 API/Event 상세 흐름을 보관합니다.

## Portfolio Flow Docs

| 문서 | 설명 |
| :--- | :--- |
| [Assignment Lifecycle](./assignment-lifecycle.md) | 과제 생성/수정/삭제, 공개 상태 계산, 테스트케이스 응답 기준 |
| [Problem Sync](./problem-sync.md) | WEB에서 SNS로 Online Judge problem sync 이벤트 발행 |
| [Judge Completed](./judge-completed.md) | OJ 채점 완료 이벤트를 SQS로 소비하고 projection에 반영 |
| [Auth User Sync](./auth-user-sync.md) | Auth 사용자 이벤트를 SQS로 소비하고 report user에 반영 |

## Existing Detailed Docs

| 문서 | 설명 |
| :--- | :--- |
| [과제 공개 및 조회 흐름](./assignment-publish-flow.md) | `startAt` 기준 과제 공개 상태 계산과 사용자 조회 흐름 |
| [테스트케이스 OJ 동기화 흐름](./testcase-oj-sync-flow.md) | 과제 생성/수정/삭제 후 OJ problem sync 이벤트를 발행하는 흐름 |
| [Judge Completion 이벤트 소비 흐름](./judge-completion-consumer-flow.md) | OJ 채점 완료 이벤트를 SQS로 소비하고 projection에 반영하는 흐름 |
| [관리자 코스/과제 관리 흐름](./admin-course-assignment-flow.md) | 관리자 API 기반 코스, 수강생, 과제 운영 흐름 |
| [v2 인증 및 권한 흐름](./auth-flow.md) | JWT, v2 헤더, role 기반 접근 제어 흐름 |
