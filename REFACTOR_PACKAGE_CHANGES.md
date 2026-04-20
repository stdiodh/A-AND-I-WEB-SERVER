# Package Refactor Changes

## 요약

이번 리팩토링은 `package_map.md` 기준에 맞춰 패키지 구조를 `feature + layer` 중심으로 재배치한 작업이다.

- 외부 API path, method, request/response field, header, error contract는 변경하지 않았다.
- `v1`, `v2`는 가능한 한 API 레이어에만 남기고 내부 로직은 공통 계층으로 이동했다.
- 운영 Mongo 문서 호환성 리스크가 있는 `entity` 패키지는 의도적으로 유지했다.

## 주요 변경

### 1. common 정리

- `common/v2/api` -> `common/api/envelope`, `common/api/factory`, `common/api/header`
- `common/v2/error` -> `common/error/v2`
- `common/v2/security` -> `common/security/v2`
- `common/v2/logging` -> `common/logging/v2`

### 2. report 정리

- `report/v2/api` -> `report/api/v2`
- `report/v2/error` -> `report/api/v2/error`
- `report/v2/openapi` -> `report/api/v2/openapi`
- `report/v2/mapper` -> `report/infrastructure/mapper`

### 3. course 정리

- `course/controller` -> `course/api/v1/controller`
- `course/v2/controller` -> `course/api/v2/controller`
- `course/service` -> `course/application/service`
- `course/dtos` -> `course/api/dto`
- `course/domain` + `course/enum` -> `course/domain/model`
- `course/repository` -> `course/infrastructure/repository`

### 4. assignment 정리

- `assignment/dtos` -> `assignment/api/dto`
- `assignment/v2/dto` -> `assignment/api/v2/dto`
- `assignment/v2/service` -> `assignment/application/service`
- `assignment/domain` + `assignment/enum` -> `assignment/domain/model`
- `assignment/jackson` -> `assignment/infrastructure/jackson`
- `assignment/event` -> `assignment/infrastructure/event`
- `assignment/repository` -> `assignment/infrastructure/repository`
- `assignment/submission/service` -> `assignment/application/submission/service`
- `assignment/submission/event` -> `assignment/infrastructure/submission/event`
- `assignment/submission/repository` -> `assignment/infrastructure/submission/repository`

### 5. user 정리

- `user/service` -> `user/application/service`
- `user/config` -> `user/infrastructure/config`
- `user/event` -> `user/infrastructure/event`
- `user/repository` -> `user/infrastructure/repository`

## 의도적으로 보류한 항목

### entity 패키지 유지

아래 영역은 운영 Mongo 문서의 `_class` 저장 여부에 따라 런타임 역직렬화 리스크가 있을 수 있어 이번 커밋에서는 이동하지 않았다.

- `assignment/entity`
- `course/entity`
- `user/entity`
- `assignment/submission/entity`

이 영역을 이동하려면 아래 확인이 선행되어야 한다.

1. 운영 DB에 `_class`가 실제 저장되는지 확인
2. 필요 시 `@TypeAlias` 또는 데이터 마이그레이션 전략 수립
3. 샘플 문서 복원 테스트 후 이동

## 검증

다음 검증을 수행했다.

- `./gradlew compileKotlin compileTestKotlin`
- `./gradlew clean test --tests '*CourseApiRoutingWebFluxTest' --tests '*CourseV2ApiRoutingWebFluxTest' --tests '*CourseCommandServiceTest' --tests '*CourseQueryServiceTest' --tests '*CourseV1ServiceTest' --tests '*ErrorHandlingWebFluxTest' --tests '*ReportSwaggerDocumentationIntegrationTest' --tests '*ReportUserSyncServiceTest' --tests '*SqsUserEventConsumerTest' --tests '*AdminAssignmentSubmissionStatusesV2ServiceTest'`
- `./gradlew cleanTest test --tests '*V2StructuredLoggingWebFilterTest' --tests '*ErrorHandlingWebFluxTest' --tests '*CourseApiRoutingWebFluxTest' --tests '*CourseV2ApiRoutingWebFluxTest' --tests '*AssignmentReportTestCaseEventMapperTest' --tests '*SqsJudgeSubmissionEventConsumerTest'`

## 다음 단계 권장

1. ArchUnit 또는 detekt 기반 패키지 의존 규칙 추가
2. `entity` 이동 전 Mongo `_class` 저장 여부 확인
3. `assignment/submission/entity` 포함한 마지막 인프라 정리 여부 결정
