package com.example.aandi_post_web_server.submission.controller

import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.example.aandi_post_web_server.common.openapi.AssignmentSubmissionAcceptedEnvelopeDoc
import com.example.aandi_post_web_server.common.openapi.ErrorEnvelopeDoc
import com.example.aandi_post_web_server.submission.dtos.AssignmentSubmissionAcceptedResponse
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
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
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
        description = "과제 제출 생성은 WEB-SERVER가 담당하고, 이후 제출 상세 조회/내 제출 목록/스트림 조회는 ONLINE-JUDGE-SERVER 책임으로 위임합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "접수 성공", content = [Content(schema = Schema(implementation = AssignmentSubmissionAcceptedEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "잘못된 assignmentId 또는 요청 값", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "Authorization 헤더 누락", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "코스에 수강 중이 아니거나 공개된 과제를 찾을 수 없음", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "502", description = "OJ 제출 위임 실패", content = [Content(schema = Schema(implementation = ErrorEnvelopeDoc::class))]),
        ],
    )
    @PostMapping("/{courseSlug}/assignments/{assignmentId}/submissions")
    fun createSubmission(
        @Parameter(description = "코스 슬러그", example = "back-basic")
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
            publicCode = resolvePublicCode(authentication),
            authorizationHeader = authorizationHeader,
            request = request,
        ).map { ApiEnvelope.success(it) }
    }

    private fun resolvePublicCode(authentication: Authentication): String {
        val jwtAuthentication = authentication as? JwtAuthenticationToken ?: return authentication.name
        return sequenceOf("publicCode", "public_code")
            .mapNotNull { key -> jwtAuthentication.token.claims[key]?.toString() }
            .firstOrNull { it.isNotBlank() }
            ?: authentication.name
    }
}
