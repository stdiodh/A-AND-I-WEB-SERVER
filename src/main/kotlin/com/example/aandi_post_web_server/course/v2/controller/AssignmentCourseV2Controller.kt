package com.example.aandi_post_web_server.course.v2.controller

import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.openapi.CourseEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.ErrorEnvelopeDoc
import com.example.aandi_post_web_server.course.dtos.CourseResponse
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
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@Tag(
    name = "코스 조회 v2 API",
    description = "v1 코스 조회 공개 기능을 v2 경로로 확장한 API입니다. `Authorization: Bearer {JWT}` 인증과 공통 `ApiEnvelope(success/data/error/timestamp)` 응답을 사용합니다.",
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v2/assignments")
class AssignmentCourseV2Controller(
    private val courseV1Service: CourseV1Service,
) {

    @Operation(
        summary = "과제가 속한 코스 조회",
        description = "v1 `GET /v1/courses/assignments/{assignmentId}/course` 대응 API입니다. 사용자가 접근 가능한 코스만 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = CourseEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "과제 또는 코스를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{assignmentId}/course")
    fun getAssignmentCourse(
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<CourseResponse>> =
        courseV1Service.getAssignmentCourse(assignmentId, authentication.name).map { ApiEnvelope.success(it) }
}
