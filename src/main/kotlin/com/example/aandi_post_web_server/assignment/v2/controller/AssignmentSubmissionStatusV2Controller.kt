package com.example.aandi_post_web_server.assignment.v2.controller

import com.example.aandi_post_web_server.assignment.v2.dto.AssignmentSubmissionStatusResponse
import com.example.aandi_post_web_server.assignment.v2.service.AssignmentSubmissionStatusV2Service
import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.openapi.AssignmentSubmissionStatusEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.ErrorEnvelopeDoc
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
    name = "과제 제출 여부 v2 API",
    description = "현재 로그인한 사용자의 과제 제출 여부를 조회하는 사용자 API입니다. 관리자 조회 API가 아니며, 채점 완료 제출 이력 projection 을 읽는 API입니다.",
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v2/assignments")
class AssignmentSubmissionStatusV2Controller(
    private val assignmentSubmissionStatusV2Service: AssignmentSubmissionStatusV2Service,
) {

    @Operation(
        summary = "내 과제 제출 여부 조회",
        description =
            """
            현재 로그인한 사용자가 이 과제를 이미 제출 완료했는지 확인합니다.
            이 API는 OJ의 `JUDGE_COMPLETED` 이벤트를 기준으로 적재된 projection 을 조회하며, 제출 횟수를 계산하는 API가 아닙니다.
            projection 이 있으면 `submitted=true`, projection 이 없으면 `submitted=false` 를 반환합니다.
            `submitted=true` 는 현재 사용자 기준으로 채점 완료된 제출 이력이 최소 1건 이상 존재한다는 의미입니다.
            관리자용 코스 관리 API가 아니라 현재 사용자 본인의 상태를 확인하는 API이므로 `/v2/assignments/{assignmentId}/submission-status/me` 경로를 유지합니다.
            """,
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentSubmissionStatusEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "인증 실패", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "과제를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "500", description = "현재 사용자 publicCode projection 누락 또는 서버 내부 오류", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{assignmentId}/submission-status/me")
    fun getMySubmissionStatus(
        @Parameter(description = "과제 UUID", example = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001")
        @PathVariable assignmentId: String,
        authentication: Authentication,
    ): Mono<ApiEnvelope<AssignmentSubmissionStatusResponse>> =
        assignmentSubmissionStatusV2Service.getMySubmissionStatus(assignmentId, authentication.name)
            .map { response -> ApiEnvelope.success(response) }
}
