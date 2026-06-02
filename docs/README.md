# A&I Web Server Docs

이 문서는 메인 README에서 다루지 않은 API/Event 상세 흐름과 운영 문서를 정리한다.

## API / Event Flow

| 문서 | 설명 |
| :--- | :--- |
| [과제 공개 및 조회 흐름](./api-flows/assignment-publish-flow.md) | `startAt` 기준 과제 공개 상태 계산과 사용자 조회 흐름 |
| [테스트케이스 OJ 동기화 흐름](./api-flows/testcase-oj-sync-flow.md) | 과제 생성/수정/삭제 후 OJ problem sync 이벤트를 발행하는 흐름 |
| [Judge Completion 이벤트 소비 흐름](./api-flows/judge-completion-consumer-flow.md) | OJ 채점 완료 이벤트를 SQS로 소비하고 projection에 반영하는 흐름 |
| [관리자 코스/과제 관리 흐름](./api-flows/admin-course-assignment-flow.md) | 관리자 API 기반 코스, 수강생, 과제 운영 흐름 |
| [v2 인증 및 권한 흐름](./api-flows/auth-flow.md) | JWT, v2 헤더, role 기반 접근 제어 흐름 |

## Operations

| 문서 | 설명 |
| :--- | :--- |
| [배포 문서](./deploy-v2.0.8.md) | `v2.0.8` 태그 배포와 EC2 확인 절차 |
| [Logging v2](./logging-v2.md) | v2 구조화 로깅 스키마와 마스킹 정책 |
| [CloudWatch Report Server](./cloudwatch-report-server.md) | CloudWatch 로그 수집과 Logs Insights 쿼리 |
| [Discord Alert](./discord-alert-from-cloudwatch.md) | CloudWatch 기반 Discord 알림 설계 |

## Assets

| 문서 | 설명 |
| :--- | :--- |
| [Demo Assets Guide](./assets/README.md) | README와 docs에 추가할 GIF/이미지 촬영 기준 |

## Legacy

| 문서 | 설명 |
| :--- | :--- |
| [Legacy README](./legacy-readme.md) | 포트폴리오 README 개편 전 기존 README 보관본 |
