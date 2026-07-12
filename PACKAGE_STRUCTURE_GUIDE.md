# Package Structure Guide

## 목적

이 문서는 `A-AND-I-WEB-SERVER`에서 앞으로 생성되는 코드가 현재 리팩토링 기준을 일관되게 따르도록 하기 위한 기준 문서다.

핵심 목표는 다음과 같다.

- 기능 기준과 계층 기준을 함께 사용하되, 기준이 섞이지 않게 한다.
- `v1`, `v2`는 가능한 한 API 레이어에만 둔다.
- 외부 API 계약은 유지하고, 내부 구조만 점진적으로 정리한다.
- 신규 코드가 어느 패키지에 들어가야 하는지 바로 판단할 수 있게 한다.

## 기본 원칙

### 1. 최상위는 기능 기준

최상위 패키지는 아래 기능 단위로 구분한다.

- `common`
- `assignment`
- `course`
- `user`

### 2. 그 아래는 계층 기준

기능 하위는 가능한 한 아래 계층 기준을 따른다.

- `api`
- `application`
- `domain`
- `infrastructure`

### 3. 기능 버전은 원칙적으로 API 레이어에만 둔다

버전 표기는 아래 위치에만 둔다.

- `feature.api.v1.controller`
- `feature.api.v1.openapi`
- `feature.api.v2.controller`
- `feature.api.v2.openapi`

아래 레이어는 가능한 한 버전 중립으로 유지한다.

- `feature.application`
- `feature.domain`
- `feature.infrastructure`

`common.error.v2`, `common.logging.v2`, `common.security.v2`처럼 여러 기능의 v2 외부 프로토콜을 지원하는 공통 경계는 명시적 예외입니다. 이 예외를 새 기능 내부 레이어로 확장하지 않습니다.

## 현재 기준 구조

```text
com.example.aandi_post_web_server
├─ common
│  ├─ api
│  │  ├─ envelope
│  │  ├─ factory
│  │  └─ header
│  ├─ config
│  ├─ controller
│  ├─ error
│  │  └─ v2
│  ├─ logging
│  │  └─ v2
│  ├─ openapi
│  └─ security
│     └─ v2
│
├─ assignment
│  ├─ api
│  │  ├─ dto
│  │  ├─ filter
│  │  └─ v2
│  │     ├─ controller
│  │     └─ dto
│  ├─ application
│  │  ├─ activation
│  │  ├─ mapper
│  │  ├─ port
│  │  ├─ service
│  │  └─ submission
│  │     ├─ model
│  │     └─ service
│  ├─ domain
│  │  └─ model
│  ├─ entity
│  ├─ infrastructure
│  │  ├─ adapter
│  │  ├─ event
│  │  ├─ jackson
│  │  ├─ repository
│  │  └─ submission
│  │     ├─ adapter
│  │     ├─ event
│  │     └─ repository
│  └─ submission
│     └─ entity
│
├─ course
│  ├─ api
│  │  ├─ dto
│  │  ├─ v1
│  │  │  └─ controller
│  │  └─ v2
│  │     └─ controller
│  ├─ application
│  │  ├─ mapper
│  │  ├─ port
│  │  └─ service
│  ├─ domain
│  │  └─ model
│  ├─ entity
│  └─ infrastructure
│     ├─ adapter
│     └─ repository
│
└─ user
   ├─ application
   │  ├─ model
   │  └─ service
   ├─ entity
   └─ infrastructure
      ├─ adapter
      ├─ config
      ├─ event
      └─ repository
```

## 어디에 둘지 판단 기준

### API 레이어

아래에 해당하면 `api`에 둔다.

- 컨트롤러
- 요청/응답 DTO
- 버전별 OpenAPI 문서
- 외부 계약에 직접 드러나는 모델

예시:

- `course.api.v1.controller.CourseV1Controller`
- `assignment.api.dto.CreateAssignmentRequest`

### Application 레이어

아래에 해당하면 `application`에 둔다.

- 서비스
- 유스케이스 조합
- 트랜잭션 또는 흐름 제어
- 여러 도메인 객체를 엮는 조정 로직
- 다른 feature 구현을 호출하기 위한 outbound port

예시:

- `course.application.service.CourseCommandService`
- `assignment.application.port.AssignmentCoursePort`
- `assignment.application.port.AssignmentProblemSyncPort`
- `assignment.application.service.AssignmentCommandService`
- `assignment.application.submission.service.AssignmentSubmissionStatusProjectionService`

### Domain 레이어

아래에 해당하면 `domain.model`에 둔다.

- 도메인 규칙
- 값 객체
- 계산/검증 로직
- 도메인 관점 enum

예시:

- `assignment.domain.model.AssignmentTestCaseValidator`
- `course.domain.model.CourseStatus`

### Infrastructure 레이어

아래에 해당하면 `infrastructure`에 둔다.

- repository
- application port의 adapter
- 외부 이벤트 발행/소비
- Jackson 지원 코드
- Mongo/AWS 등 외부 기술 의존 구현

예시:

- `assignment.infrastructure.event.SnsAssignmentReportTestCaseEventPublisher`
- `assignment.infrastructure.event.DirectAssignmentProblemSyncAdapter`
- `course.infrastructure.adapter.AssignmentCourseAdapter`
- `user.infrastructure.event.SqsUserEventConsumer`

## 명명 규칙

### DTO

- 신규 코드는 `dtos` 대신 `dto`를 사용한다.
- 외부 계약 모델은 `feature.api.dto`에 둔다.

### Service

- 가능하면 역할 중심 이름을 사용한다.
- `V1Service`, `V2Service` 같은 이름은 가능한 한 늘리지 않는다.

좋은 예:

- `CourseQueryService`
- `CourseCommandService`
- `AssignmentCommandService`

### Version

- 클래스명에 버전이 남아 있어도 되지만, 기능 패키지는 API 레이어에서만 버전 구분을 우선한다.
- 공통 외부 프로토콜 경계의 `common.*.v2`는 위 기본 원칙의 명시적 예외다.

## 금지/주의 규칙

### 금지

- `application`에서 `api.controller` 의존
- `infrastructure`에서 `api.controller` 의존
- 새 레거시 패키지 생성
  - 예: `assignment.dtos`, `course.service`, `report.v2`

### 주의

- `common`은 진짜 공통만 둔다.
- 기능 전용 DTO/로직을 `common`으로 올리지 않는다.
- `assignment.application`에서 `course.entity`와 `course.infrastructure`를 직접 의존하지 않는다. 코스 조회·주차 보장은 `AssignmentCoursePort`를 사용한다.
- assignment application의 problem sync는 `AssignmentProblemSyncPort`를 사용하고 mapper·publisher·wire event 구현을 직접 의존하지 않는다.
- 다른 feature infrastructure가 필요하면 application port와 infrastructure adapter로 경계를 만든다.
- `entity` 패키지는 운영 Mongo 문서 `_class` 확인 전까지 함부로 이동하지 않는다.

## Entity 관련 예외

아래 패키지는 Mongo 문서 호환성 때문에 현재 유지 중이다.

- `assignment.entity`
- `assignment.submission.entity`
- `course.entity`
- `user.entity`

이 영역은 실제 DB의 `_class` 저장 상태를 확인하기 전까지 바로 이동하지 않는다.

## 코드 리뷰 체크리스트

- 이 코드는 어느 feature에 속하는가?
- 외부 계약 모델인가, 내부 로직인가?
- `api`, `application`, `domain`, `infrastructure` 중 어디가 맞는가?
- 버전 구분이 정말 API 레이어에서만 필요한가?
- `common`에 넣을 만큼 진짜 공통인가?
- 기존 레이어 경계를 깨는 import가 생기지 않았는가?

## 함께 봐야 하는 테스트

구조를 건드릴 때 아래 테스트를 함께 확인한다.

- `PackageArchitectureRegressionTest`
- `LayerDependencyRegressionTest`
- `MongoTypeAliasConfigTest`

## 한 줄 기준

신규 코드는 먼저 **feature를 정하고**, 그 다음 **layer를 정하고**, 마지막으로 **API 버전이 필요한지** 판단해서 배치한다.
