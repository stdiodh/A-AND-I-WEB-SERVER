package com.example.aandi_post_web_server.common.v2.api

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Schema(description = "A&I v2 공통 API 응답")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class V2ApiEnvelope<T>(
    @field:Schema(description = "요청 성공 여부", example = "true")
    val success: Boolean,
    @field:Schema(description = "성공 시 반환되는 데이터입니다. 실패하면 null입니다.", nullable = true)
    val data: T?,
    @field:Schema(description = "실패 정보입니다. 성공하면 null입니다.", nullable = true)
    val error: V2ApiError?,
    @field:Schema(description = "응답 시각(Asia/Seoul)", example = "2026-04-13T18:00:00+09:00")
    val timestamp: String,
) {
    companion object {
        private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

        fun now(): String = OffsetDateTime.now(seoulZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}

@Schema(description = "A&I v2 공통 에러 정보")
data class V2ApiError(
    @field:Schema(description = "규약형 정수 에러 코드", example = "40301")
    val code: Int,
    @field:Schema(description = "개발자용 상세 메시지", example = "timestamp 헤더는 ISO-8601 또는 epoch milliseconds 형식이어야 합니다.")
    val message: String,
    @field:Schema(description = "에러 식별 값", example = "VALIDATE_ERROR")
    val value: String,
    @field:Schema(description = "사용자 안내 문구", example = "입력값 형식이 올바르지 않습니다.")
    val alert: String,
)
