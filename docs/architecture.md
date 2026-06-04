# Architecture

> 메인 README로 돌아가기: [README](../README.md)

본 프로젝트는 A&I 과제 운영 백엔드이며, 코스/과제/수강 데이터 관리와 외부 서버 이벤트 동기화를 함께 담당합니다.

## 책임 범위

| 영역 | 책임 | 주요 근거 |
| :--- | :--- | :--- |
| Course / Assignment | 코스, 주차, 수강, 과제, 요구사항, 테스트케이스 관리 | `course/application/service`, `assignment/entity` |
| Assignment Operation | 과제 공개 상태 계산, 사용자/관리자 조회 분리, 과제 복사 | `CourseQueryService`, `CourseCommandService`, `AssignmentCopyService` |
| Online Judge Sync | 과제 테스트케이스 snapshot을 problem sync 이벤트로 발행 | `AssignmentReportTestCaseEventMapper`, `SnsAssignmentReportTestCaseEventPublisher` |
| Judge Result Sync | OJ 채점 완료 이벤트를 소비해 제출 상태 projection 갱신 | `SqsJudgeSubmissionEventConsumer`, `AssignmentSubmissionStatusProjectionService` |
| Auth User Sync | AUTH 사용자 이벤트를 소비해 report user 정보 갱신 | `SqsUserEventConsumer`, `ReportUserSyncService` |
| Operations | 구조화 로그, CloudWatch Logs 수집, Discord alert 연계 기준 | `common/logging/v2`, `docker-compose.prod.yml`, `docs/discord-alert-from-cloudwatch.md` |

## 시스템 경계

```text
Client / Admin
  -> A&I Web Server
      -> MongoDB
      -> AWS SNS: problem sync publish
      -> AWS SQS: judge completed consume
      -> AWS SQS: auth user sync consume
  -> Online Judge Server
  -> Auth Server
  -> CloudWatch Logs
```

## 저장소 구성

| 패키지 | 설명 |
| :--- | :--- |
| `assignment` | 과제 entity, DTO, problem sync 이벤트, 제출 상태 projection |
| `course` | 코스, 수강, 주차, 과제 운영 API와 service |
| `user` | Auth user event parser, SQS consumer, report user sync service |
| `common/security` | JWT, v2 헤더, role 기반 접근 제어 |
| `common/logging/v2` | v2 API 요청/응답 구조화 로그 |
| `common/error` | requestId/traceId와 공통 오류 응답 |

## 주요 데이터 저장

| Collection / Entity | 용도 |
| :--- | :--- |
| `assignments` | 과제 메타데이터, 공개 시간, origin/fingerprint 복사 정보 |
| `assignment_test_cases` | 과제별 테스트케이스와 visibility |
| `assignment_submission_statuses` | `JUDGE_COMPLETED` 이벤트 기반 제출 상태 projection |
| `courses`, `course_weeks`, `course_enrollments` | 코스 운영과 수강 상태 |
| `report_users` | Auth 서버에서 동기화한 사용자 표시 정보 |

## 이벤트 아키텍처 판단

- Assignment 변경은 API 응답 처리와 OJ 동기화 책임이 섞이지 않도록 SNS publish로 분리했습니다.
- Judge completed와 Auth user sync는 WEB 서버가 SQS consumer로 pull합니다.
- direct JSON body와 SNS envelope body를 모두 지원해 SQS raw message delivery 설정 차이에 대응합니다.
- 이벤트 처리 실패 시 메시지를 무조건 삭제하지 않습니다. parser가 처리 불가로 판정한 비대상 메시지는 reason 로그 후 삭제하고, 처리 중 예외는 로그 후 재시도 가능성을 남깁니다.

## 운영 경계

- 애플리케이션은 CloudWatch appender를 직접 사용하지 않습니다.
- v2 구조화 로그는 stdout JSON 한 줄로 출력됩니다.
- Docker production compose와 deploy workflow가 `awslogs` logging driver, log group, retention policy를 설정합니다.

## 확인 필요

- 실제 운영 SNS/SQS subscription 구성과 IAM policy는 이 저장소 안에서 전체 값을 확인할 수 없습니다.
- 외부 공개 URL의 현재 정상 동작 여부는 이번 문서 작업에서 smoke test하지 않았습니다.
