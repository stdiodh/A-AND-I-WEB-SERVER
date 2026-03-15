package com.example.aandi_post_web_server.common.openapi

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "에러 응답 본문")
data class ApiErrorResponse(
    @field:Schema(description = "에러 발생 시각(Asia/Seoul)", example = "2026-03-05T20:00:00+09:00")
    val timestamp: String,
    @field:Schema(description = "요청 경로", example = "/v1/admin/courses")
    val path: String,
    @field:Schema(description = "HTTP 상태 코드", example = "400")
    val status: Int,
    @field:Schema(description = "HTTP 상태 이름", example = "Bad Request")
    val error: String,
    @field:Schema(description = "에러 코드", example = "VALIDATION_ERROR")
    val code: String,
    @field:Schema(description = "바로 이해할 수 있는 에러 메시지", example = "요청 값이 올바르지 않습니다.")
    val message: String,
    @field:Schema(description = "요청 추적 ID", example = "b4ec31d6-29")
    val requestId: String,
    @field:Schema(description = "필드별 상세 오류 목록")
    @field:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val details: List<ApiErrorDetail> = emptyList(),
)

@Schema(description = "상세 오류 항목")
data class ApiErrorDetail(
    @field:Schema(description = "오류가 발생한 필드", example = "metadata.phase")
    val field: String? = null,
    @field:Schema(description = "거부된 값", example = "BAS1C")
    val rejectedValue: Any? = null,
    @field:Schema(description = "왜 실패했는지 설명하는 문장", example = "허용되지 않는 값입니다. 가능한 값: BASIC, CS, FRAMEWORK")
    val reason: String,
    @field:Schema(description = "검증 제약 코드", example = "NotBlank")
    val constraint: String? = null,
)
