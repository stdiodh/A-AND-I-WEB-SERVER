package com.example.aandi_post_web_server.report.api.v2.openapi

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentSummaryResponse
import com.example.aandi_post_web_server.report.api.v2.ReportApiEnvelope
import com.example.aandi_post_web_server.report.api.v2.ReportApiError
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    description = "report v2 실패 응답",
    example =
        """
        {
          "success": false,
          "data": null,
          "error": {
            "code": 40301,
            "message": "timestamp header must be epoch milliseconds or ISO-8601.",
            "value": "VALIDATE_ERROR",
            "alert": "입력값 형식이 올바르지 않습니다."
          },
          "timestamp": "2026-03-09T12:00:00+09:00"
        }
        """,
)
data class ReportV2ErrorEnvelopeDoc(
    @field:Schema(description = "요청 성공 여부(Boolean)", example = "false")
    val success: Boolean = ReportApiEnvelope.FAIL,
    val data: Any? = null,
    val error: ReportApiError = ReportApiError(
        code = 40301,
        message = "timestamp header must be epoch milliseconds or ISO-8601.",
        value = "VALIDATE_ERROR",
        alert = "요청 값을 확인해주세요.",
    ),
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "report v2 과제 목록 성공 응답",
    example = """{"success":true,"data":[{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","requirements":[],"learningGoals":[],"testCases":[],"codeTemplates":[]}}],"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class ReportV2AssignmentSummaryListEnvelopeDoc(
    @field:Schema(description = "요청 성공 여부(Boolean)", example = "true")
    val success: Boolean = ReportApiEnvelope.SUCCESS,
    val data: List<AssignmentSummaryResponse> = emptyList(),
    val error: ReportApiError? = null,
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)

@Schema(
    description = "report v2 과제 상세 성공 응답",
    example = """{"success":true,"data":{"assignmentId":"8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111","courseSlug":"back-basic","weekNo":1,"orderInWeek":1,"startAt":"2026-03-03T00:00:00Z","endAt":"2026-03-11T00:00:00Z","status":"PUBLISHED","publishedAt":"2026-03-03T00:00:00Z","metadata":{"title":"터미널 계산기","difficulty":"MID","description":"# 문제 설명","requirements":[],"learningGoals":[],"testCases":[],"codeTemplates":[]}},"error":null,"timestamp":"2026-03-09T12:00:00+09:00"}""",
)
data class ReportV2AssignmentDetailEnvelopeDoc(
    @field:Schema(description = "요청 성공 여부(Boolean)", example = "true")
    val success: Boolean = ReportApiEnvelope.SUCCESS,
    val data: AssignmentDetailResponse? = null,
    val error: ReportApiError? = null,
    val timestamp: String = "2026-03-09T12:00:00+09:00",
)
