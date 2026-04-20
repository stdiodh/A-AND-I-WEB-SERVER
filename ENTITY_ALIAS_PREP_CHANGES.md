# Entity Alias Preparation Changes

## 요약

이번 변경은 Mongo 엔티티 패키지 이동 전에 문서 호환성 리스크를 줄이기 위한 준비 단계다.

- `@Document` 엔티티에 안정적인 `@TypeAlias`를 추가했다.
- 새로 저장되는 Mongo 문서가 패키지명이 아니라 고정 alias를 `_class`에 사용하도록 준비했다.
- 패키지 회귀 방지 테스트와 alias 기록 검증 테스트를 추가했다.

## 변경 파일

### TypeAlias 추가

- `src/main/kotlin/com/example/aandi_post_web_server/assignment/entity/Assignment.kt`
- `src/main/kotlin/com/example/aandi_post_web_server/assignment/entity/AssignmentDelivery.kt`
- `src/main/kotlin/com/example/aandi_post_web_server/assignment/entity/AssignmentRequirement.kt`
- `src/main/kotlin/com/example/aandi_post_web_server/assignment/entity/AssignmentTestCase.kt`
- `src/main/kotlin/com/example/aandi_post_web_server/assignment/submission/entity/AssignmentSubmissionStatusProjection.kt`
- `src/main/kotlin/com/example/aandi_post_web_server/course/entity/Course.kt`
- `src/main/kotlin/com/example/aandi_post_web_server/course/entity/CourseEnrollment.kt`
- `src/main/kotlin/com/example/aandi_post_web_server/course/entity/CourseWeek.kt`
- `src/main/kotlin/com/example/aandi_post_web_server/user/entity/ReportUser.kt`

### 테스트 추가

- `src/test/kotlin/com/example/aandi_post_web_server/common/architecture/PackageArchitectureRegressionTest.kt`
- `src/test/kotlin/com/example/aandi_post_web_server/common/config/MongoTypeAliasConfigTest.kt`

## 의도

이 단계의 목적은 아직 엔티티 패키지를 옮기지 않고도, 이후 이동 시 `_class` 값이 패키지명에 덜 의존하도록 만드는 것이다.

- alias가 없으면 패키지 이동 시 `_class` 값이 바뀔 수 있다.
- alias를 먼저 고정하면 이후 엔티티 패키지 이동을 더 안전하게 설계할 수 있다.
- 회귀 테스트를 통해 레거시 패키지 import/선언이 다시 들어오는 것도 막는다.

## 검증

다음 검증을 수행했다.

- `./gradlew test --tests '*MongoTypeAliasConfigTest' --tests '*PackageArchitectureRegressionTest'`

## 아직 하지 않은 것

- 운영 DB의 기존 `_class` 값 실측 확인
- 기존 문서가 FQCN으로 저장돼 있을 경우 마이그레이션 여부 결정
- 실제 엔티티 패키지 이동

다음 단계는 운영 데이터에서 `_class` 저장 상태를 확인하고, 그 결과에 따라 엔티티 패키지 이동 또는 마이그레이션 전략을 결정하는 것이다.
