package com.example.aandi_post_web_server.report.v2.controller

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentSummaryResponse
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.report.v2.api.ReportApiEnvelope
import com.example.aandi_post_web_server.report.v2.api.ReportApiResponseFactory
import com.example.aandi_post_web_server.report.v2.mapper.ReportResponseMapper
import com.example.aandi_post_web_server.report.v2.openapi.ReportV2AssignmentDetailEnvelopeDoc
import com.example.aandi_post_web_server.report.v2.openapi.ReportV2AssignmentSummaryListEnvelopeDoc
import com.example.aandi_post_web_server.report.v2.openapi.ReportV2ErrorEnvelopeDoc
import com.example.aandi_post_web_server.report.v2.service.ReportFacadeService
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

@Tag(name = "리포트 v2 API", description = "동일한 코스/과제 조회 기능을 report v2 헤더 규약과 `ReportApiEnvelope` 로 제공하는 API입니다.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v2/report")
class ReportQueryV2Controller(
    private val reportFacadeService: ReportFacadeService,
) {
    @Operation(
        summary = "코스 과제 목록 조회",
        description = "`GET /v2/courses/{courseSlug}/assignments` 와 동일한 조회 기능을 report v2 계약으로 제공합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ReportV2AssignmentSummaryListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청 또는 헤더 오류", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "인증 실패", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "권한 없음", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "리소스 없음", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments")
    fun getAssignments(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "주차 번호", example = "1")
        @RequestParam(required = false) weekNo: Int?,
        @Parameter(description = "과제 상태", example = "PUBLISHED")
        @RequestParam(required = false) status: AssignmentStatus?,
        authentication: Authentication,
    ): Mono<ReportApiEnvelope<List<AssignmentSummaryResponse>>> =
        reportFacadeService.getAssignments(courseSlug, weekNo, status, authentication)
            .collectList()
            .map(ReportResponseMapper::assignmentSummaryList)
            .map(ReportApiResponseFactory::success)

    @Operation(
        summary = "주차별 과제 목록 조회",
        description = "`GET /v2/courses/{courseSlug}/weeks/{weekNo}/assignments` 와 동일한 조회 기능을 report v2 계약으로 제공합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ReportV2AssignmentSummaryListEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청 또는 헤더 오류", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "인증 실패", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "권한 없음", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "리소스 없음", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
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
    ): Mono<ReportApiEnvelope<List<AssignmentSummaryResponse>>> =
        reportFacadeService.getAssignmentsByWeek(courseSlug, weekNo, status, authentication)
            .collectList()
            .map(ReportResponseMapper::assignmentSummaryList)
            .map(ReportApiResponseFactory::success)

    @Operation(
        summary = "과제 상세 조회",
        description = "`GET /v2/courses/{courseSlug}/assignments/{assignmentId}` 와 동일한 조회 기능을 report v2 계약으로 제공합니다.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "조회 성공", content = [Content(schema = Schema(implementation = ReportV2AssignmentDetailEnvelopeDoc::class))]),
            ApiResponse(responseCode = "400", description = "요청 또는 헤더 오류", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "401", description = "인증 실패", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "403", description = "권한 없음", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
            ApiResponse(responseCode = "404", description = "리소스 없음", content = [Content(schema = Schema(implementation = ReportV2ErrorEnvelopeDoc::class))]),
        ],
    )
    @GetMapping("/{courseSlug}/assignments/{assignmentId}")
    fun getAssignmentDetail(
        @Parameter(description = "코스를 구분하는 슬러그", example = "back-basic")
        @PathVariable courseSlug: String,
        @Parameter(description = "과제 UUID", example = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
        @PathVariable assignmentId: String,
        authentication: Authentication,
    ): Mono<ReportApiEnvelope<AssignmentDetailResponse>> =
        reportFacadeService.getAssignmentDetail(courseSlug, assignmentId, authentication)
            .map(ReportResponseMapper::assignmentDetail)
            .map(ReportApiResponseFactory::success)
}
