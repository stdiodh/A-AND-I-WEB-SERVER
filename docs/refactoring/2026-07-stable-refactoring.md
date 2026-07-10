# 2026-07 안정적 리팩터링 완료 기록

## 목표와 원칙

외부 API와 이벤트 순서를 유지하면서 전역 상태, 중복 정책, 서비스 경계, 배포 위험을 작은 단계로 줄입니다. 각 단계는 독립적인 테스트 checkpoint를 통과한 뒤 다음 단계로 넘어갑니다.

- 상태: 완료 — [PR #68](https://github.com/Team-AnI/A-AND-I-WEB-SERVER/pull/68)로 `main` 병합
- 기준 commit: `14a283a`
- 작업 branch(기록): `develop/stable-refactoring`
- 병합 commit: `c2c542d`
- 최종 checkpoint: 306 tests, failures/errors/skipped 0, Line 85.67%, Branch 63.37%

보존하는 핵심 계약:

- 과제 PATCH에서 `testCases` 생략은 기존 값 유지
- `testCases: []`는 기존과 동일하게 400
- 예약 공개 과제는 저장 상태가 `PUBLISHED`여도 시작 전 응답은 `DRAFT`
- 생성·수정·삭제 후 problem sync 이벤트 발행 순서 유지
- 운영 MongoDB의 기존 `mongo_data` 볼륨 유지
- 실제 Docker volume 이름을 자동 추정하지 않고 기존 container mount 또는 명시된 `MONGO_VOLUME_NAME`으로 확인

## 진행 상태

| 단계 | 상태 | 변경과 검증 |
| :--- | :--- | :--- |
| 0. 기준선 | 완료 | 277 tests, Line 85.04%, Branch 62.59% 기준 확보 |
| 1. 요청 계약 | 완료 | 전역 `IdentityHashMap` tracker 제거, DTO 내부 presence로 PATCH 계약 보존, 중첩 Bean Validation 보강 |
| 2. 공개 정책 | 완료 | 상태·`publishedAt` 계산 통합, 관리자 응답의 요청당 기준 시각 고정, Clock 경계 테스트 추가 |
| 3. 배포 안전화 | 완료 | 테스트된 단일 JAR 이미지, secret 비영속화, candidate readiness, 직전 이미지 rollback 추가 |
| 4. 서비스 경계 seam | 완료 | 요청 검증과 `testCases` 교체 판정을 `AssignmentCommandRequestResolver`로 이동 |
| 5. 과제 명령 서비스 분리 | 완료 | CRUD·복사·`deleteAllByCourseId`를 `AssignmentCommandService`로 이동하고 기존 Course facade는 delegate 유지 |
| 6. Course 의존 포트 | 완료 | 코스 조회·주차 보장 port/adapter 도입, assignment application의 Course persistence 직접 의존 금지 |
| 7A. problem sync 발행 경계 | 완료 | 동기 발행 계약을 테스트로 고정하고 application port/direct adapter로 분리 |
| 7B. transactional outbox | 제안 | replica-set transaction과 소비자 idempotency 확인 후 ADR 0001에 따라 진행 |
| 8. 데이터 운영 | 예정 | Mongo index migration, replica/backup 복원, Testcontainers 동시성 검증 |
| 9. 문서·레거시 분류 | 완료 | 현재 문서와 과거 측정 근거를 분류하고 후속 코드·운영 정리는 별도 단계로 분리 |

이 문서의 구현 범위는 PR #68에서 완료했습니다. 7B transactional outbox, 8 데이터 운영, 운영 Compose 단일화는 완료 누락이 아니라 별도 운영 전제와 검증이 필요한 후속 작업입니다.

## 완료한 서비스 경계

### 과제 명령 서비스 분리

생성·수정·삭제와 코스 삭제 시 cascade를 한 변경으로 이동했습니다. `CourseCommandService`의 공개 과제 메서드는 호환 delegate로 남겼습니다.

검증 결과:

- `CourseCommandService` 749줄에서 185줄로 축소
- assignment cascade 완료 → 삭제 이벤트 완료 → course relation 삭제 → course 삭제 순서 테스트 추가
- assignment application의 Course command facade 역참조를 금지하는 구조 테스트 추가
- 기존 `CourseCommandServiceTest`와 전체 회귀 테스트 통과

### Course 의존 포트 도입

`AssignmentCommandService`와 `AssignmentCopyService`가 사용하던 Course repository/entity 경계를 최소 port로 치환했습니다. 외부 오류 응답과 원본 과제의 레거시 `courseSlug` fallback은 기존 서비스에 그대로 유지했습니다.

검증 결과:

- `AssignmentCoursePort`가 코스 id/slug 조회와 주차 보장 계약만 노출
- `AssignmentCourseAdapter`가 기존 Course/CourseWeek repository 호출과 Asia/Seoul 날짜 변환을 담당
- `assignment.application -> course.entity/infrastructure` import를 금지하는 구조 테스트 추가
- adapter 및 기존 과제 명령·복사 회귀 테스트와 전체 298개 테스트 통과

### Problem sync 발행 경계 분리

과제 명령 서비스가 SNS mapper/publisher/wire event 구현을 직접 조합하던 경로를 `AssignmentProblemSyncPort` 뒤로 이동했습니다. `DirectAssignmentProblemSyncAdapter`가 최신 snapshot 조회와 기존 mapper·publisher 호출을 담당하므로 향후 outbox adapter로 교체할 경계가 생겼습니다.

검증 결과:

- CREATED/UPDATED/DELETED의 subject와 JSON payload 계약 고정
- 생성·수정은 저장 후 최신 assignment/testcase snapshot으로 발행하는 동작 유지
- publisher 오류의 동일 전파와 코스 삭제 중간 실패 시 후속 삭제 중단 계약 유지
- 단건 삭제의 publisher 호출 자체를 연관 데이터 삭제 완료 뒤로 지연
- assignment application의 problem sync infrastructure 직접 import를 금지하는 구조 테스트 추가
- 전체 306개 테스트와 JaCoCo gate 통과

## 다음 변경의 안전 기준

### 남은 기능 경계

assignment application은 Course persistence 구현을 더 이상 직접 사용하지 않습니다. `CourseId`, `CourseSlug`, `WeekNo`, `AssignmentId` 같은 명시적 course domain 값 계약은 현재 허용합니다. 이 값 객체까지 이동하는 변경은 저장 데이터와 API 검증 규칙의 소유권을 먼저 정한 뒤 별도 단계에서 진행합니다.

### 이벤트 일관성

7A에서는 기존 direct SNS 동기 발행을 유지한 채 교체 경계만 만들었습니다. 단순 retry는 추가하지 않습니다. 7B 전환 전에는 외부 replica-set MongoDB의 실제 rollback 테스트, Judge 소비자의 `eventId` 중복 제거, 동일 assignment 내 순서 정책, direct/outbox 상호 배타 전환을 먼저 확인합니다.

### 운영 설정 단일화

`deploy-tag.yml`과 `docker-compose.prod.yml`의 볼륨 이름을 바로 통일하지 않습니다. 실제 운영 볼륨과 복구 절차를 확인한 뒤 canonical Compose 파일을 별도 PR에서 도입합니다.

## 공통 checkpoint

```bash
./gradlew clean test jacocoTestCoverageVerification bootJar --no-daemon
git diff --check
```

배포 파일 변경 시 workflow YAML parse, SSH shell `bash -n`, dummy 환경의 `docker compose config --quiet`을 추가로 확인합니다. Docker daemon과 AWS가 필요한 검증은 staging 결과를 별도로 기록합니다.
