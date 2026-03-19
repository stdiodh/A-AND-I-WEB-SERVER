# [WEB] auth 사용자 이벤트 구독 검증 및 users 컬렉션 자동 동기화 보강 작업 계획서

## 1. 배경

WEB 서버는 `users` 컬렉션의 `ReportUser` 문서에 아래 필드를 저장한다.

- `id`
- `publicCode`
- `username`
- `role`
- `nickname`
- `profileImageUrl`
- `syncedAt`
- `updatedAt`

`publicCode`에는 unique 인덱스가 걸려 있으며, 수강생 등록은 `publicCode`로 `users` 컬렉션을 조회해 사용자를 찾는다. 사용자가 존재하지 않으면 `POST /v1/admin/courses/{courseSlug}/enrollments`는 실패한다.

## 2. 현재 코드 기준 확인 결과

### 2.1 수강 등록은 이미 `users` 컬렉션 동기화에 의존함

- `ReportUser` 저장 구조는 [`src/main/kotlin/com/example/aandi_post_web_server/user/entity/ReportUser.kt`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/src/main/kotlin/com/example/aandi_post_web_server/user/entity/ReportUser.kt) 에 정의되어 있다.
- `publicCode` unique 인덱스는 [`src/main/kotlin/com/example/aandi_post_web_server/user/entity/ReportUser.kt`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/src/main/kotlin/com/example/aandi_post_web_server/user/entity/ReportUser.kt) 에 이미 선언되어 있다.
- 수강 등록은 [`src/main/kotlin/com/example/aandi_post_web_server/course/service/CourseCommandService.kt`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/src/main/kotlin/com/example/aandi_post_web_server/course/service/CourseCommandService.kt) 에서 `ReportUserRepository.findByPublicCode()`를 통해 `users` 컬렉션을 조회한다.
- 조회 실패 시 `"auth 이벤트 동기화 여부를 확인해주세요."` 메시지로 `422 Unprocessable Entity`를 반환한다.

### 2.2 auth 사용자 이벤트 consumer는 저장소 내에서 확인되지 않음

- `ReportUserRepository`는 조회 전용 수준이며, 이벤트 기반 upsert/delete 전용 API가 없다.
- 저장소 전체 검색 기준으로 `UserProfileUpdated`, `UserDeleted`, auth 사용자 이벤트 consumer, `users` 동기화 서비스 구현은 존재하지 않는다.
- 현재 이벤트 관련 구현은 과제 테스트케이스용 SNS publisher뿐이다.

### 2.3 운영 설정도 사용자 이벤트 수신 기준으로는 비어 있음

- [`build.gradle.kts`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/build.gradle.kts) 에는 `sns` 의존성만 있고, 소비자 측에서 일반적으로 필요한 `sqs` 의존성은 없다.
- [`src/main/resources/application.yml`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/src/main/resources/application.yml) 과 [`.github/workflows/deploy-tag.yml`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/.github/workflows/deploy-tag.yml) 에는 `report-test-case` 발행 설정만 있고, auth 사용자 이벤트 구독 설정은 없다.

### 2.4 현재 결론

현재 저장소 기준으로는 "WEB이 auth 사용자 변경 이벤트를 안정적으로 구독해 `users` 컬렉션을 최신 상태로 유지한다"는 보장을 할 수 없다. 이번 작업은 점검이 아니라, 실제 동기화 파이프라인을 추가하는 정합성 보강 작업으로 진행해야 한다.

## 3. 목표

- AUTH 사용자 변경 이벤트를 WEB이 정상적으로 수신하는지 검증한다.
- 이벤트 수신 시 `users` 컬렉션이 멱등적으로 upsert/delete 되도록 보장한다.
- `POST /v1/admin/courses/{courseSlug}/enrollments` 가 최신 `publicCode` 기준으로 즉시 동작하게 만든다.

## 4. 범위

### 포함

- auth 사용자 이벤트 수신 경로 확인 및 WEB consumer 구현
- `users` 컬렉션 upsert/delete 보강
- 멱등성, 순서 역전, unique 충돌 대응
- enrollment 연동 검증
- 운영 환경 바인딩, DLQ, 알림 기준 정의

### 제외

- AUTH 서비스 자체의 이벤트 발행 로직 수정
- 기존 enrollment 데이터의 대규모 일괄 보정
- 인증/인가 정책 변경

## 5. 작업 가설 및 설계 방향

### 5.1 이벤트 수신 방식

현재 저장소에는 HTTP webhook 기반 SNS 수신이나 queue consumer 구현이 없다. 운영 안정성을 고려하면 WEB의 auth 사용자 이벤트 수신은 다음 우선순위로 검토한다.

1. AUTH가 SNS topic으로 사용자 이벤트를 발행한다.
2. WEB 전용 SQS subscription을 붙인다.
3. WEB은 SQS를 polling 소비해 MongoDB를 갱신한다.
4. 실패 메시지는 DLQ로 보낸다.

이 방식은 재시도, back-pressure, DLQ, 운영 가시성을 확보하기 쉽고 WebFlux 서비스와도 분리 운영이 가능하다.

만약 AUTH 계약이 이미 다른 transport를 강제한다면 그 계약을 우선 따르되, 최소 요구사항은 아래와 동일하다.

- 적어도 한 번 이상 전달되어도 안전해야 한다.
- 중복 수신 시 동일 결과를 보장해야 한다.
- 처리 실패 메시지의 재처리 경로와 DLQ가 있어야 한다.

### 5.2 도메인 반영 정책

- 생성/수정 이벤트는 `id` 기준 upsert 한다.
- 삭제 이벤트는 hard delete를 기본안으로 둔다.
- hard delete가 운영상 위험하면 soft delete 필드를 추가하되, enrollment 조회에서는 반드시 제외한다.
- `publicCode` 변경은 기존 문서 재생성이 아니라 동일 `id` 문서의 원자적 갱신으로 처리한다.

### 5.3 순서 안정성

- 이벤트 payload에 `occurredAt`, `version`, 또는 `updatedAt` 같은 정렬 기준이 있어야 한다.
- 저장 시 문서의 `updatedAt`보다 오래된 이벤트는 무시한다.
- 같은 timestamp만 있는 경우에는 `id + eventId` 또는 message deduplication key를 함께 저장해 중복 반영을 막는다.

## 6. 세부 작업 계획

### 6.1 1단계: AUTH 이벤트 계약 및 운영 경로 검증

- AUTH에서 발행하는 사용자 이벤트 종류와 payload 스키마를 확정한다.
- 최소 필요 이벤트:
  - `UserProfileUpdated`
  - `UserDeleted`
- payload 필드 확인:
  - `id`
  - `publicCode`
  - `username`
  - `role`
  - `nickname`
  - `profileImageUrl`
  - `updatedAt`
  - 가능하면 `eventId`, `occurredAt`, `version`
- 운영 환경 확인:
  - topic ARN
  - WEB subscription ARN / queue ARN
  - raw message delivery 사용 여부
  - visibility timeout
  - DLQ 연결 여부
  - queue redrive policy
  - consumer scale / concurrency 기준

산출물:

- AUTH 이벤트 계약 문서 링크 또는 스키마 확정본
- 운영 바인딩 체크리스트

### 6.2 2단계: WEB 설정 및 consumer 골격 추가

예상 변경 파일:

- [`build.gradle.kts`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/build.gradle.kts)
- [`src/main/resources/application.yml`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/src/main/resources/application.yml)
- 신규 패키지 예시:
  - `src/main/kotlin/com/example/aandi_post_web_server/user/event/`
  - `src/main/kotlin/com/example/aandi_post_web_server/user/service/`
  - `src/main/kotlin/com/example/aandi_post_web_server/user/config/`

구현 항목:

- consumer enable/disable 설정 추가
- queue URL 또는 subscription endpoint 설정 추가
- region, polling interval, batch size, visibility timeout 관련 설정 추가
- consumer 시작/종료 lifecycle 정의
- 실패 시 nack/retry/DLQ 이동 로직 정의

### 6.3 3단계: users 동기화 서비스 구현

구현 항목:

- `UserProfileUpdated` 수신 시 `id` 기준 upsert
- 갱신 대상 필드:
  - `publicCode`
  - `username`
  - `role`
  - `nickname`
  - `profileImageUrl`
  - `syncedAt`
  - `updatedAt`
- `UserDeleted` 수신 시 hard delete 또는 soft delete
- soft delete를 선택할 경우 `ReportUser`와 조회 조건을 함께 수정

권장 저장 전략:

- MongoTemplate의 atomic upsert 사용
- `updatedAt` 비교 조건을 포함해 older event 덮어쓰기 방지
- unique 충돌 발생 시 구조화된 에러 로그와 알림 발행

### 6.4 4단계: repository 및 enrollment 연동 보강

예상 변경 파일:

- [`src/main/kotlin/com/example/aandi_post_web_server/user/repository/ReportUserRepository.kt`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/src/main/kotlin/com/example/aandi_post_web_server/user/repository/ReportUserRepository.kt)
- [`src/main/kotlin/com/example/aandi_post_web_server/course/service/CourseCommandService.kt`](/Users/dh/Desktop/Code/A-AND-I-WEB-SERVER/src/main/kotlin/com/example/aandi_post_web_server/course/service/CourseCommandService.kt)

구현 항목:

- 필요 시 `findActiveByPublicCode` 또는 soft delete 제외 조회 추가
- `publicCode` 변경 직후 enrollment가 최신 값으로 성공하는지 테스트 보강
- 삭제된 사용자는 enrollment 대상에서 제외

### 6.5 5단계: 관측성 및 운영 안전장치 추가

- 이벤트 처리 성공/실패/무시 건수 메트릭
- unique 충돌 로그에 `userId`, `publicCode`, `eventId`, `updatedAt` 포함
- DLQ 적재 시 운영 알림 기준 정의
- 재처리 runbook 작성

## 7. 테스트 계획

### 단위 테스트

- 동일 `id` 사용자 생성 이벤트가 문서를 upsert 한다.
- 수정 이벤트가 `publicCode`, `username`, `nickname`, `profileImageUrl`을 갱신한다.
- 삭제 이벤트가 문서를 제거하거나 비활성화한다.
- 동일 이벤트 재수신 시 문서가 1건만 유지된다.
- 더 오래된 이벤트가 나중에 와도 최신 문서를 덮어쓰지 못한다.
- `publicCode` unique 충돌 시 예외를 로깅하고 메시지를 ack/nack 정책대로 처리한다.

### 통합 테스트

- 이벤트 소비 후 MongoDB `users` 컬렉션 반영 확인
- `POST /v1/admin/courses/{courseSlug}/enrollments` 가 변경 후 `publicCode` 기준으로 성공하는지 확인
- 삭제된 유저의 `publicCode` 로 enrollment 시 실패 확인

### 시나리오 테스트

생성:

- AUTH invite-mail 호출
- WEB `users` 컬렉션에 신규 문서 생성 확인
- 같은 `publicCode`로 enrollment 성공 확인

수정:

- AUTH `PATCH /v1/admin/users`
- WEB `users.publicCode` 갱신 확인
- 변경 전 `publicCode`는 실패, 변경 후 `publicCode`는 성공 확인

삭제:

- AUTH user 삭제
- WEB `users` 제거 또는 비활성화 확인
- 삭제된 `publicCode`로 enrollment 실패 확인

멱등성:

- 동일 이벤트 재전송
- 문서 중복 생성 없이 1건 유지 확인

## 8. 완료 조건

- AUTH에서 초대 유저를 생성하면 WEB `users` 컬렉션에 동일 `id/publicCode` 문서가 자동 생성된다.
- AUTH에서 유저 정보를 수정하면 WEB `users` 문서가 자동 갱신된다.
- AUTH에서 유저 삭제 시 WEB `users`에서도 제거되거나 등록 불가 상태가 된다.
- 수강 등록 API가 최신 `publicCode` 기준으로 정상 동작한다.
- 운영에서 DLQ, 재처리, 알림 경로가 준비되어 있다.

## 9. 리스크 및 선결 조건

### 선결 조건

- AUTH 이벤트 스키마 확정
- 운영 topic/subscription/queue 권한 확보
- DLQ 정책 합의

### 주요 리스크

- AUTH payload에 순서 비교용 필드가 없으면 out-of-order 방어가 약해진다.
- `publicCode` unique 충돌은 운영 데이터 불일치의 신호이므로 자동 무시하면 안 된다.
- soft delete를 택하면 enrollment 조회, 관리자 검색, 운영 스크립트가 함께 조정되어야 한다.

## 10. 권장 구현 순서

1. AUTH 이벤트 계약과 운영 바인딩을 먼저 확정한다.
2. WEB에 consumer 설정과 수신 골격을 추가한다.
3. `users` upsert/delete 동기화 서비스를 구현한다.
4. enrollment 연동 테스트와 멱등성 테스트를 추가한다.
5. DLQ, 알림, 운영 가이드를 마무리한다.

## 11. 이번 저장소 기준 판단 요약

- `users` 컬렉션 의존성은 이미 존재한다.
- 하지만 auth 사용자 이벤트를 받는 실제 consumer와 운영 설정은 현재 없다.
- 따라서 본 작업은 P0 정합성 보강 작업으로 진행하는 것이 타당하다.
