# API / Event Flow Index

README에서 압축한 기능별 상세 흐름을 이 디렉터리에 분리한다.

| 문서 | 포함 내용 |
| :--- | :--- |
| [과제 공개 및 조회 흐름](./assignment-publish-flow.md) | 사용자 코스/과제 조회 API, 공개 상태 계산, 예외 흐름 |
| [테스트케이스 OJ 동기화 흐름](./testcase-oj-sync-flow.md) | 관리자 과제 변경 후 SNS problem sync 이벤트 발행 |
| [Judge Completion 이벤트 소비 흐름](./judge-completion-consumer-flow.md) | SQS 메시지 파싱, projection upsert, 관리자 제출 현황 조회 |
| [관리자 코스/과제 관리 흐름](./admin-course-assignment-flow.md) | 관리자 API 목록과 코스/수강/과제 운영 절차 |
| [v2 인증 및 권한 흐름](./auth-flow.md) | JWT Resource Server, v2 헤더 검증, role 기반 인가 |
