# Demo Capture

> 메인 README로 돌아가기: [README](../README.md)

본 문서는 README에 연결한 이미지와 GIF 촬영 상태를 기록합니다. 민감정보, JWT, Authorization header, 운영 URL, private testcase, user submitted code 원문은 캡처하지 않습니다.

## 생성된 데모 파일

| 기능 | 파일 | 생성 방식 | 상태 |
| :--- | :--- | :--- | :--- |
| 시스템 구조 | `docs/assets/diagrams/architecture.png` | draw.io 원본 기반 정적 PNG 생성 | 생성 |
| MongoDB 데이터 모델 | `docs/assets/diagrams/data-model.png` | draw.io 원본 기반 정적 PNG 생성 | 생성 |
| 커버리지 요약 | `docs/assets/images/coverage-report.png` | JaCoCo XML 기준 summary 이미지 생성 | 생성 |
| v2 Swagger/OpenAPI 문서 | `docs/assets/images/swagger-ui-v2.jpg` | 로컬 Swagger 화면 screenshot | 생성 |
| Assignment Operation | `docs/assets/gifs/assignment-operation.gif` | [확인 필요] | 미생성 |
| Online Judge Problem Sync | `docs/assets/gifs/problem-sync.gif` | [확인 필요] | 미생성 |
| Judge Completed Event | `docs/assets/gifs/judge-completed.gif` | [확인 필요] | 미생성 |

## 자동 생성 시도 결과

| 항목 | 결과 |
| :--- | :--- |
| 정적 다이어그램 | 성공 |
| 커버리지 이미지 | 성공 |
| 브라우저 자동화 | 실패. 현재 환경에 Chrome/Chromium, Playwright, Puppeteer가 설치되어 있지 않습니다. |
| GIF 생성 | 실패. 현재 환경에 ffmpeg가 설치되어 있지 않습니다. |
| 기능 GIF 미생성 원인 | 실제 과제 생성, SQS 메시지 처리, CloudWatch/Discord 화면에는 seed data, JWT, SNS/SQS 또는 localstack 구성이 필요합니다. |

## 수동 촬영 기준

| 기능 | 권장 파일명 | 촬영 범위 | 반드시 보여줄 액션 |
| :--- | :--- | :--- | :--- |
| Assignment Operation | `docs/assets/gifs/assignment-operation.gif` | Swagger 또는 API client | 과제 생성 또는 공개 상태 변경, 테스트케이스 연결 결과 |
| Online Judge Problem Sync | `docs/assets/gifs/problem-sync.gif` | API client, application log, AWS console 또는 localstack | Assignment 변경, SNS/SQS event 발행 로그, OJ 동기화 메시지 |
| Judge Completed Event | `docs/assets/gifs/judge-completed.gif` | SQS message injection, application log, MongoDB 또는 API 응답 | `JUDGE_COMPLETED` 메시지 입력, consumer 처리, report 상태 반영 |

## 캡처 전 점검

- JWT, Authorization, Authenticate, salt, cookie, secret 값은 화면에서 가립니다.
- 실제 userId, publicCode, 이메일, 운영 queue URL, account ID는 데모 값으로 치환합니다.
- private testcase input/output과 user submitted code 원문은 보여주지 않습니다.
- 운영 URL 대신 localhost 또는 마스킹된 URL을 사용합니다.
