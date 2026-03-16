package com.example.aandi_post_web_server.submission.controller

import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.openapi.AssignmentSubmissionAcceptedEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.AssignmentSubmissionResultEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.ErrorEnvelopeDoc
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionAcceptedResponse
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionResultResponse
import com.example.aandi_post_web_server.submission.dtos.CreateAssignmentSubmissionRequest
import com.example.aandi_post_web_server.submission.service.AssignmentSubmissionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(name = "코스 조회 API", description = "트랙/과정/코스/과제 조회 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/courses")
class AssignmentSubmissionController(
    private val assignmentSubmissionService: AssignmentSubmissionService,
) {

    @Operation(
        summary = "과제 제출 생성",
        description = "수강 중인 사용자가 과제 코드를 제출합니다. assignmentId는 과제 UUID를 사용하며, 공개 시작 시간이 지난 과제만 제출할 수 있습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "접수 성공", content = [Content(schema = Schema(implementation = AssignmentSubmissionAcceptedEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "assignmentId 또는 요청 값이 올바르지 않음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스 또는 과제를 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "409", description = "제출 가능 시간이 지났거나 채점 케이스가 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/assignments/{assignmentId}/submissions")
    fun createSubmission(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        @Valid @RequestBody request: CreateAssignmentSubmissionRequest,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorizationHeader: String?,
        authentication: Authentication,
    ): Mono<ApiEnvelope<AssignmentSubmissionAcceptedResponse>> {
        return assignmentSubmissionService.createSubmission(
            courseSlug = courseSlug,
            assignmentId = assignmentId,
            userId = authentication.name,
            authorizationHeader = authorizationHeader,
            request = request,
        ).map { ApiEnvelope.success(it) }
    }

    @Operation(
        summary = "과제 제출 결과 조회",
        description = "내가 제출한 과제의 현재 채점 결과를 보여줍니다. 아직 채점이 끝나지 않았으면 PENDING 또는 RUNNING 상태로 반환합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = AssignmentSubmissionResultEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "assignmentId가 올바르지 않음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스, 과제 또는 제출을 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}/submissions/{submissionId}")
    fun getSubmissionResult(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        @Parameter(description = "제출 ID", example = "67d3a37e5f0c1e42c0f4a123")
        @PathVariable submissionId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorizationHeader: String?,
        authentication: Authentication,
    ): Mono<ApiEnvelope<AssignmentSubmissionResultResponse>> {
        return assignmentSubmissionService.getSubmissionResult(
            courseSlug = courseSlug,
            assignmentId = assignmentId,
            submissionId = submissionId,
            userId = authentication.name,
            authorizationHeader = authorizationHeader,
        ).map { ApiEnvelope.success(it) }
    }

    @Operation(
        summary = "과제 제출 스트림 조회",
        description = "내가 제출한 과제의 채점 진행 상황을 SSE로 구독합니다. 테스트 케이스 결과와 최종 완료 이벤트를 순서대로 받습니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "스트림 연결 성공"),
            ApiResponse(responseCode = "400", description = "assignmentId가 올바르지 않음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스, 과제 또는 제출을 찾을 수 없거나 접근할 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping(
        value = ["/{courseSlug}/assignments/{assignmentId}/submissions/{submissionId}/stream"],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    fun streamSubmissionResult(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        @Parameter(description = "제출 ID", example = "67d3a37e5f0c1e42c0f4a123")
        @PathVariable submissionId: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorizationHeader: String?,
        authentication: Authentication,
    ): Flux<ServerSentEvent<String>> {
        return assignmentSubmissionService.streamSubmissionResult(
            courseSlug = courseSlug,
            assignmentId = assignmentId,
            submissionId = submissionId,
            userId = authentication.name,
            authorizationHeader = authorizationHeader,
        )
    }
}
