package com.example.aandi_post_web_server.report.v2.api

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Schema(description = "report v2 공통 API 응답")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ReportApiEnvelope<T>(
    @field:Schema(description = "요청 성공 여부(Boolean)", example = "true")
    val success: Boolean,
    @field:Schema(description = "성공 시 반환되는 데이터입니다. 실패하면 null입니다.", nullable = true)
    val data: T?,
    @field:Schema(description = "실패 정보입니다. 성공하면 null입니다.", nullable = true)
    val error: ReportApiError?,
    @field:Schema(description = "응답 시각(Asia/Seoul)", example = "2026-03-06T14:00:00+09:00")
    val timestamp: String,
) {
    companion object {
        private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")
        const val SUCCESS: Boolean = true
        const val FAIL: Boolean = false

        internal fun now(): String = OffsetDateTime.now(seoulZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}

@Schema(description = "report v2 에러 정보")
data class ReportApiError(
    @field:Schema(description = "규약형 정수 에러 코드", example = "40301")
    val code: Int,
    @field:Schema(description = "개발자용 상세 메시지", example = "metadata.title: must not be blank")
    val message: String,
    @field:Schema(description = "에러 식별 값", example = "VALIDATE_ERROR")
    val value: String,
    @field:Schema(description = "사용자 안내 문구", example = "요청 값을 확인해주세요.")
    val alert: String,
)
