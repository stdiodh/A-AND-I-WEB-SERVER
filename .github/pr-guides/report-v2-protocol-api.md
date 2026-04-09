# Title

feat: add report v2 protocol api

## Description

- 최신 배포 기준점은 `upstream/main`의 `v1.1.18` (`93fe82f`) 이며, 현재 `develop`은 이 배포 기준에서 `4ba2b52 feat: add report v2 protocol api` 1개 커밋만큼 앞서 있다.
- 기존 `/v1/report/**`, `/v1/courses/**` 동작은 유지하고, 동일한 조회 기능을 `/v2/report/**` 로 신규 노출했다.
- `/v2/report/**` 는 업로드된 A&I 통신 규약에 맞춰 `success/data/error/timestamp` envelope, `code/message/value/alert` 에러 모델, `Authenticate` 헤더 브리지, report v2 전용 예외 매핑을 제공한다.
- `salt` 는 PDF 규약에 맞춰 선택 헤더로 처리하고, `APP_REPORT_V2_SALT_SECRET` 이 설정된 경우 `MD5(timestamp + secret)` 검증을 수행한다.

## Key Code (Before & After)

- Before

```kotlin
it.pathMatchers("/v1/report/**", "/v1/courses/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")

val result = errorResponseFactory.unauthorized(exchange, authException.message)
writeErrorResponse(exchange, objectMapper, result)
```

- After

```kotlin
it.pathMatchers("/v1/report/**", "/v1/courses/**", "/v2/report/**").hasAnyRole("USER", "ORGANIZER", "ADMIN")

.addFilterBefore(AuthenticateHeaderBridgeFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
.addFilterAfter(
    ReportHeaderValidationFilter(reportV2SecurityProperties.saltSecret),
    SecurityWebFiltersOrder.AUTHENTICATION,
)

if (ReportPathMatcher.isReportV2Path(exchange.request.path.pathWithinApplication().value())) {
    val result = ReportExceptionMapper.fromThrowable(authException)
    writeReportErrorResponse(exchange, objectMapper, result)
}
```

- Before

```json
{
  "success": true,
  "data": { "...": "..." },
  "error": null,
  "timestamp": "..."
}
```

- After

```json
{
  "success": "SUCCESS",
  "data": { "...": "..." },
  "error": null,
  "timestamp": "2026-03-25T21:23:36.958558466+09:00"
}
```

- New error shape

```json
{
  "success": "FAIL",
  "data": null,
  "error": {
    "code": 40301,
    "message": "weekNo field is invalid",
    "value": "VALIDATE_ERROR",
    "alert": "입력값 형식이 올바르지 않습니다."
  },
  "timestamp": "2026-03-25T21:23:36.958558466+09:00"
}
```

## Reason for Change

- 현재 배포본은 report 전용 v2 계약이 없고, 공통 응답도 문자열형 5자리 규약 코드를 직접 표현하지 않는다.
- 이번 변경은 기존 비즈니스 로직을 건드리지 않고, assignment/course 조회 로직을 재사용하는 report v2 외부 인터페이스를 추가하는 데 목적이 있다.
- 보안 흐름도 표준 JWT 리소스 서버를 유지한 채 `Authenticate` 헤더를 `Authorization` 으로 브리지하도록 설계해 침습을 줄였다.
- PDF 원문이 `success` 타입과 에러 코드 설명에서 일부 모순을 가지므로, 이번 구현은 문서의 실제 예시값을 우선 기준으로 맞췄다.
- 최신 배포 대비 변경 파일은 총 21개이며, 신규 `report/v2` 계층 추가와 `SecurityConfig`, 전역 예외 처리기의 report v2 분기가 핵심 변경 지점이다.

## To Reviewer

- `/v2/report/**` 가 기존 `/v1` 동작을 침범하지 않고 report v2 계약만 별도로 적용되는지 봐주세요.
- `salt` 선택 처리와 `APP_REPORT_V2_SALT_SECRET` 기반 검증 방식이 인증 서버 연동 전 임시 전략으로 적절한지 확인 부탁드립니다.
- PDF 규약의 모순 때문에 적용한 에러 코드 매핑(`21101`, `21201`, `40301`, `44501`, `96501`, `98801`)이 팀 해석과 맞는지 봐주세요.
- 조회 API 범위만 먼저 열어둔 상태이므로, 이후 `/v2/admin/report/**` 와 제출 계열 API를 같은 패턴으로 확장해도 무리가 없는지 확인 부탁드립니다.

## Validation

- `./gradlew test --no-daemon --console=plain --tests 'com.example.aandi_post_web_server.report.v2.ReportV2ContractTest'`
- `./gradlew compileKotlin --no-daemon --console=plain`
- `./gradlew --stop && ./gradlew test --no-daemon --console=plain --tests 'com.example.aandi_post_web_server.course.controller.CourseApiRoutingWebFluxTest' --tests 'com.example.aandi_post_web_server.common.error.ErrorHandlingWebFluxTest'`
