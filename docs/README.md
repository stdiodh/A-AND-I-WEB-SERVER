# A&I Web Server Docs

본 문서는 `A-AND-I-WEB-SERVER`를 이력서와 포트폴리오에서 근거 문서로 연결하기 위한 상세 문서 목차입니다.

> 메인 README로 돌아가기: [README](../README.md)

## 핵심 문서

| 문서 | 설명 |
| :--- | :--- |
| [Architecture](./architecture.md) | 서버 책임, MongoDB 저장 영역, SNS/SQS 연동, 운영 로그 경로 |
| [Event-driven Sync](./event-driven-sync.md) | WEB -> SNS/SQS -> OJ, OJ -> SNS/SQS -> WEB, AUTH -> SNS/SQS -> WEB 이벤트 흐름 |
| [Structured Logging](./structured-logging.md) | v2 API JSON 로그 필드, 마스킹 기준, CloudWatch/Discord alert 연계 |
| [Deployment](./deployment.md) | Docker, GitHub Actions, ECR, EC2, CloudWatch Logs 배포 근거 |
| [Test](./test.md) | 실제 Gradle 명령 실행 결과, JaCoCo line/branch coverage, CI task |
| [Performance Measurement](./performance-measurement.md) | 현재 성능 측정값 유무와 향후 측정 절차 |
| [Query Tuning](./query-tuning.md) | MongoDB 쿼리 튜닝 후보, 기존 인덱스, 측정 불가 사유 |
| [Resume Evidence](./resume-evidence.md) | 이력서 문장, 근거 링크, 검증 상태 |
| [Demo Capture](./demo-capture.md) | 생성된 Swagger 이미지, 기능별 GIF 미생성 사유, 수동 촬영 기준 |

## API / Event Flow

| 문서 | 설명 |
| :--- | :--- |
| [Assignment Lifecycle](./api-flows/assignment-lifecycle.md) | 과제 생성/수정/삭제, 공개 상태 계산, 사용자 조회 흐름 |
| [Problem Sync](./api-flows/problem-sync.md) | Assignment 변경 후 Online Judge problem sync 이벤트 발행 흐름 |
| [Judge Completed](./api-flows/judge-completed.md) | OJ 채점 완료 이벤트 소비와 제출 상태 projection 반영 흐름 |
| [Auth User Sync](./api-flows/auth-user-sync.md) | Auth 사용자 변경 이벤트 소비와 report user 동기화 흐름 |
| [Legacy Flow Index](./api-flows/README.md) | 기존 API 흐름 문서와 신규 문서 연결 |

## Troubleshooting

| 문서 | 설명 |
| :--- | :--- |
| [Troubleshooting Index](./troubleshooting/README.md) | 트러블슈팅 문서 목록 |
| [SQS Envelope Format](./troubleshooting/sqs-envelope-format.md) | direct JSON body와 SNS envelope body 파싱 기준 |
| [Assignment Copy](./troubleshooting/assignment-copy.md) | 과제 복사 중복 방지, cleanup, problem sync 기준 |
| [Event Field Consistency](./troubleshooting/event-field-consistency.md) | 이벤트 필드명과 타입 일관성 기준 |

## 기존 운영 문서

| 문서 | 설명 |
| :--- | :--- |
| [Logging v2](./logging-v2.md) | v2 구조화 로깅 상세 스키마 |
| [CloudWatch Report Server](./cloudwatch-report-server.md) | Docker `awslogs` driver 기반 CloudWatch 수집 |
| [Discord Alert From CloudWatch](./discord-alert-from-cloudwatch.md) | CloudWatch subscription filter와 Discord alert 설계 |
| [Deploy v2.0.8](./deploy-v2.0.8.md) | 태그 배포와 EC2 확인 절차 |
| [Legacy README](./legacy-readme.md) | 포트폴리오 README 개편 전 기존 README 보관본 |

## Assets

| 경로 | 용도 |
| :--- | :--- |
| `docs/assets/gifs/` | 데모 GIF 저장 위치 |
| `docs/assets/images/` | Swagger/API 응답, CloudWatch, Discord alert 이미지 저장 위치 |
| `docs/assets/videos/` | GIF 변환 전 원본 녹화 저장 위치 |
| [Demo Assets Guide](./assets/README.md) | 기존 데모 파일 촬영 기준 |
