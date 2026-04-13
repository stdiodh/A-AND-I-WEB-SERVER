package com.example.aandi_post_web_server.course.v2.controller

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.openapi.AssignmentDetailEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.AssignmentSummaryListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseOutlineEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.CourseWeekListEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.ErrorEnvelopeDoc
import com.example.aandi_post_web_server.course.dtos.CourseOutlineResponse
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseWeekResponse
import com.example.aandi_post_web_server.course.service.CourseV1Service
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@Tag(
    name = "코스 조회 v2 API",
    description = "v1 코스 조회 공개 기능을 v2 경로로 확장한 API입니다. `Authorization: Bearer {JWT}` 인증과 공통 `ApiEnvelope(success/data/error/timestamp)` 응답을 사용합니다.",
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v2/courses")
class CourseQueryV2Controller(
    private val courseV1Service: CourseV1Service,
) {

    @Operation(
        summary = "내 코스 목록 조회",
        description = "v1 `GET /v1/courses` 대응 API입니다. 로그인한 사용자의 수강 중 코스 목록을 v2 경로로 제공합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = CourseListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "인증 실패", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping
    fun getCourses(authentication: Authentication): Mono<ApiEnvelope<List<CourseResponse>>> =
        courseV1Service.getCourses(authentication.name)
            .collectList()
            .map { ApiEnvelope.success(it) }

    @Operation(
        summary = "코스 상세 조회",
        description = "v1 `GET /v1/courses/{courseSlug}` 대응 API입니다. 수강 중이 아니거나 차단된 코스는 404로 응답합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = CourseEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}")
    fun getCourse(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<CourseResponse>> =
        courseV1Service.getCourse(courseSlug, authentication.name).map { ApiEnvelope.success(it) }

    @Operation(
        summary = "코스 목차 조회",
        description = "v1 `GET /v1/courses/{courseSlug}/outline` 대응 API입니다. 코스 헤더와 과제 요약을 함께 제공합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = CourseOutlineEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/outline")
    fun getCourseOutline(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<CourseOutlineResponse>> =
        courseV1Service.getCourseOutline(courseSlug, authentication.name).map { ApiEnvelope.success(it) }

    @Operation(
        summary = "코스 주차 목록 조회",
        description = "v1 `GET /v1/courses/{courseSlug}/weeks` 대응 API입니다. 접근 가능한 코스의 주차 목록을 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = CourseWeekListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/weeks")
    fun getWeeks(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<List<CourseWeekResponse>>> =
        courseV1Service.getWeeks(courseSlug, authentication.name)
            .collectList()
            .map { ApiEnvelope.success(it) }

    @Operation(
        summary = "주차별 과제 목록 조회",
        description = "v1 `GET /v1/courses/{courseSlug}/weeks/{weekNo}/assignments` 대응 API입니다. 사용자에게 공개된 과제만 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentSummaryListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "weekNo 또는 status 값이 올바르지 않음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/weeks/{weekNo}/assignments")
    fun getAssignmentsByWeek(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "주차 번호", example = "1")
        @PathVariable weekNo: Int,
        @Parameter(description = "과제 상태", example = "PUBLISHED")
        @RequestParam(required = false) status: AssignmentStatus?,
        authentication: Authentication,
    ): Mono<ApiEnvelope<List<AssignmentSummaryResponse>>> =
        courseV1Service.getAssignmentsByWeek(
            courseSlug = courseSlug,
            weekNo = weekNo,
            status = status,
            userId = authentication.name,
        )
            .collectList()
            .map { ApiEnvelope.success(it) }

    @Operation(
        summary = "코스 과제 목록 조회",
        description = "v1 `GET /v1/courses/{courseSlug}/assignments` 대응 API입니다. 사용자에게 공개된 과제만 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentSummaryListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "weekNo 또는 status 값이 올바르지 않음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments")
    fun getAssignments(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "주차 번호", example = "1")
        @RequestParam(name = "weekNo", required = false) weekNo: Int?,
        @Parameter(description = "과제 상태", example = "PUBLISHED")
        @RequestParam(required = false) status: AssignmentStatus?,
        authentication: Authentication,
    ): Mono<ApiEnvelope<List<AssignmentSummaryResponse>>> =
        courseV1Service.getAssignments(
            courseSlug = courseSlug,
            weekNo = weekNo,
            status = status,
            userId = authentication.name,
        )
            .collectList()
            .map { ApiEnvelope.success(it) }

    @Operation(
        summary = "과제 상세 조회",
        description = "v1 `GET /v1/courses/{courseSlug}/assignments/{assignmentId}` 대응 API입니다. assignmentId는 과제 UUID를 사용합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}")
    fun getAssignmentDetail(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<AssignmentDetailResponse>> =
        courseV1Service.getAssignmentDetail(
            courseSlug = courseSlug,
            assignmentId = assignmentId,
            userId = authentication.name,
        ).map { ApiEnvelope.success(it) }
}
