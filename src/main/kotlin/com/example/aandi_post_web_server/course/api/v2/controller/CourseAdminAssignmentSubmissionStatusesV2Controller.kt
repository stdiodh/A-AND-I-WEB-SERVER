package com.example.aandi_post_web_server.course.api.v2.controller

import com.example.aandi_post_web_server.assignment.api.v2.dto.AdminAssignmentSubmissionStatusesResponse
import com.example.aandi_post_web_server.assignment.application.service.AdminAssignmentSubmissionStatusesV2Service
import com.example.aandi_post_web_server.common.openapi.V2AdminAssignmentSubmissionStatusesEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.V2ErrorEnvelopeDoc
import com.example.aandi_post_web_server.common.api.envelope.V2ApiEnvelope
import com.example.aandi_post_web_server.common.api.factory.V2ApiResponseFactory
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@Tag(
    name = "코스 관리자 v2 API",
    description = "관리자 전용 코스/수강/과제 관리 API입니다. A&I v2 공통 헤더(`deviceOS`, `Authenticate`, `timestamp`, `salt`)와 공통 응답 계약을 사용하며 ADMIN 권한이 필요합니다.",
)
@SecurityRequirement(name = "v2Authenticate")
@RestController
@RequestMapping("/v2/admin/courses")
class CourseAdminAssignmentSubmissionStatusesV2Controller(
    private val adminAssignmentSubmissionStatusesV2Service: AdminAssignmentSubmissionStatusesV2Service,
) {

    @Operation(
        summary = "관리자용 과제 제출 현황 조회",
        description =
            """
            특정 코스의 수강생 전체를 기준으로 과제 제출 현황을 조회합니다.
            이 API는 OJ `JUDGE_COMPLETED` 이벤트 기반 projection 을 읽고, 코스 수강생 목록과 left join 해서 제출 여부를 계산합니다.
            projection 이 없으면 미제출(`submitted=false`)로 간주합니다.
            제출 횟수를 모두 나열하는 API가 아니며, 현재는 최신 완료 제출 기준 요약 정보(`score`, `passedCases`, `totalCases`, `completedAt`)만 제공합니다.
            A&I v2 공통 헤더와 ADMIN 권한이 필요합니다.
            """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "제출자와 미제출자가 함께 포함된 목록 조회 성공", content = [Content(schema = Schema(implementation = V2AdminAssignmentSubmissionStatusesEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "토큰 없음 또는 인증 실패", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "ADMIN 권한 없음", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스를 찾을 수 없거나, 과제를 찾을 수 없거나, 다른 코스 소속 assignment 임", content = [Content(schema = Schema(implementation = V2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}/submission-statuses")
    fun getSubmissionStatuses(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001")
        @PathVariable assignmentId: String,
    ): Mono<V2ApiEnvelope<AdminAssignmentSubmissionStatusesResponse>> =
        adminAssignmentSubmissionStatusesV2Service.getSubmissionStatuses(courseSlug, assignmentId)
            .map(V2ApiResponseFactory::success)
}
