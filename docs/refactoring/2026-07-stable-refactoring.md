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
| 6A. 과제 조회 서비스 경계 | 완료 | 목록·상세·outline·과제 course 참조를 `AssignmentQueryService`로 이동하고 Course→Assignment infrastructure 의존 제거 |
| 6B. 과제 조회 테스트 소유권 | 완료 | 사용자·관리자 조회 테스트를 Assignment 패키지와 query port mock 기반 직접 서비스 테스트로 이동 |
| 7A. problem sync 발행 경계 | 완료 | 동기 발행 계약을 테스트로 고정하고 application port/direct adapter로 분리 |
| 7B. transactional outbox | 제안 | replica-set transaction과 소비자 idempotency 확인 후 ADR 0001에 따라 진행 |
| 8. 데이터 운영 | 진행 | Mongo index V001과 preflight/apply/verify 절차 마련. replica/backup 복원과 동시성 검증은 운영 환경 확인 후 진행 |
| 8A. 사용자 동기화 순서 | 완료 | hard delete를 동일 문서 tombstone으로 전환하고 stale profile 재삽입과 tombstone 조회 노출 차단 |
| 9. 문서·레거시 분류 | 완료 | 현재 문서와 과거 측정 근거를 분류하고 후속 코드·운영 정리는 별도 단계로 분리 |

이 문서의 최초 구현 범위는 PR #68에서 완료했습니다. 이후 데이터 운영 단계에서 애플리케이션 자동 생성을 사용하지 않는 Mongo index V001과 운영 절차를 추가했습니다. 7B transactional outbox, replica/backup 복원과 동시성 검증, 운영 Compose 단일화는 별도 운영 전제와 검증이 필요한 후속 작업입니다.

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

### 과제 조회 서비스 경계 분리

`CourseQueryService`에 섞여 있던 사용자·관리자 과제 목록/상세, outline용 공개 과제, 과제의 course 참조 조회를 `AssignmentQueryService`로 이동했습니다. Course API와 `CourseV1Service`의 호출 계약은 바꾸지 않고 기존 공개 메서드를 호환 delegate로 유지했습니다.

검증 결과:

- `AssignmentCourseQueryPort`는 과제 조회에 필요한 course id/slug와 활성 수강 여부만 노출
- `AssignmentCourseQueryAdapter`가 Course/CourseEnrollment repository 접근과 `ENABLED` 판정을 담당
- 목록의 requirement/testcase batch 조회, 사용자 공개 제한, 관리자 전체 testcase 조회, 요청당 공개 기준 시각 계약 유지
- outline은 최소 reference만 전달하고 Course의 `checked` 계산과 응답 조합 책임을 유지
- 과제의 저장된 raw course id와 visibility 404 의미를 유지한 채 Course 조회·수강 검증 순서 보존
- `CourseQueryService`에서 Assignment entity/repository/policy 의존 제거, 508줄에서 285줄로 축소
- course application의 모든 Assignment infrastructure 직접 import를 금지하는 구조 테스트 추가
- facade 전체 위임과 adapter의 course 없음·활성/비활성/미수강 경계 테스트 추가
- 전체 372개 테스트와 JaCoCo gate, bootJar 통과

### 과제 조회 테스트 소유권 정리

서비스 이동 뒤에도 Course 패키지에 남아 facade와 실제 adapter를 함께 거치던 사용자·관리자 조회 테스트를 Assignment 패키지로 옮겼습니다. 동작 계약은 유지하면서 service와 adapter의 테스트 책임을 분리했습니다.

- 사용자 13개·관리자 9개 조회 계약을 `AssignmentQueryService` 직접 테스트로 유지
- Course repository mock 대신 `AssignmentCourseQueryPort`의 typed 입력과 결과를 stub
- Course 패키지는 course 응답 조합·outline·facade 위임 테스트만 소유
- `AssignmentCourseQueryAdapterTest`가 course/enrollment repository 매핑 경계를 별도로 검증
- 전체 372개 테스트 수와 JaCoCo 측정값을 유지하고 gate·bootJar 통과

## 다음 변경의 안전 기준

### 남은 기능 경계

assignment application은 Course persistence 구현을 더 이상 직접 사용하지 않습니다. `CourseId`, `CourseSlug`, `WeekNo`, `AssignmentId` 같은 명시적 course domain 값 계약은 현재 허용합니다. 이 값 객체까지 이동하는 변경은 저장 데이터와 API 검증 규칙의 소유권을 먼저 정한 뒤 별도 단계에서 진행합니다.

### 이벤트 일관성

7A에서는 기존 direct SNS 동기 발행을 유지한 채 교체 경계만 만들었습니다. 단순 retry는 추가하지 않습니다. 7B 전환 전에는 외부 replica-set MongoDB의 실제 rollback 테스트, Judge 소비자의 `eventId` 중복 제거, 동일 assignment 내 순서 정책, direct/outbox 상호 배타 전환을 먼저 확인합니다.

### 운영 설정 단일화

`deploy-tag.yml`과 `docker-compose.prod.yml`의 볼륨 이름을 바로 통일하지 않습니다. 실제 운영 볼륨과 복구 절차를 확인한 뒤 canonical Compose 파일을 별도 PR에서 도입합니다.

### 사용자 삭제 tombstone

사용자 삭제 이벤트가 `users` 문서를 물리 삭제하면 정렬 기준 시각도 함께 사라져, 늦게 도착한 과거 profile 이벤트가 사용자를 다시 insert할 수 있었습니다. 삭제 후에는 같은 `_id` 문서에 tombstone을 남겨 단일 문서 conditional upsert로 순서를 보존합니다.

- profile과 delete 이벤트 시각은 MongoDB BSON Date 정밀도에 맞춰 millisecond로 정규화
- 같은 millisecond에서는 delete 우선, 더 최신 profile만 tombstone을 해제하고 재활성화
- tombstone은 원래 publicCode·username·nickname·profileImageUrl을 보존하지 않음
- publicCode unique 계약은 사용자 ID별 예약 sentinel 문자열로 유지
- publicCode 및 ID batch 조회는 `deletedAt: null` 조건으로 legacy active 문서를 포함하고 tombstone을 제외
- replay 상한이 없으므로 tombstone TTL은 두지 않음

이 변경은 `users` 문서의 profile 정보를 최소 tombstone으로 바꾸는 범위입니다. `course_enrollments`처럼 기존에 별도 저장된 사용자 snapshot 정리는 포함하지 않습니다.

배포 전에 이미 hard delete되어 DB에서 사라진 사용자는 이 서버만으로 식별할 수 없습니다. 배포 경계의 과거 profile 재전달까지 차단하려면 Auth 원본에서 삭제 사용자 ID를 재전달하거나 별도 승인된 backfill을 수행해야 합니다.

## 공통 checkpoint

```bash
./gradlew clean test jacocoTestCoverageVerification bootJar --no-daemon
git diff --check
```

배포 파일 변경 시 workflow YAML parse, SSH shell `bash -n`, dummy 환경의 `docker compose config --quiet`을 추가로 확인합니다. Docker daemon과 AWS가 필요한 검증은 staging 결과를 별도로 기록합니다.
