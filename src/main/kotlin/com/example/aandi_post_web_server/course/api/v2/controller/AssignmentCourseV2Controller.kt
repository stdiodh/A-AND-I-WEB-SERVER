package com.example.aandi_post_web_server.course.api.v2.controller

import com.example.aandi_post_web_server.common.openapi.V2CourseEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2ErrorEnvelopeDoc
import com.example.aandi_post_web_server.common.api.envelope.V2ApiEnvelope
import com.example.aandi_post_web_server.common.api.factory.V2ApiResponseFactory
import com.example.aandi_post_web_server.course.api.dto.CourseResponse
import com.example.aandi_post_web_server.course.application.service.CourseV1Service
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
    description = "v1 코스 조회 공개 기능을 v2 경로로 확장한 API입니다. A&I v2 공통 헤더(`deviceOS`, `Authenticate`, `timestamp`, `salt`)와 공통 응답 계약을 사용합니다.",
)
@SecurityRequirement(name = "v2Authenticate")
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
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = V2CourseEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "과제 또는 코스를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{assignmentId}/course")
    fun getAssignmentCourse(
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        authentication: Authentication,
    ): Mono<V2ApiEnvelope<CourseResponse>> =
        courseV1Service.getAssignmentCourse(assignmentId, authentication.name).map(V2ApiResponseFactory::success)
}
