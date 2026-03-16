package com.example.aandi_post_web_server.common.openapi

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Schema(description = "공통 API 응답")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ApiEnvelope<T>(
    @field:Schema(description = "요청 성공 여부", example = "true")
    val success: Boolean,
    @field:Schema(description = "성공 시 반환되는 데이터입니다. 실패하면 null입니다.", nullable = true)
    val data: T?,
    @field:Schema(
        description = "실패 정보입니다. 성공하면 null입니다.",
        nullable = true,
        example = "null",
    )
    val error: ApiErrorPayload?,
    @field:Schema(description = "응답 시각(Asia/Seoul)", example = "2026-03-06T14:00:00+09:00")
    val timestamp: String,
) {
    companion object {
        private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

        fun <T> success(data: T): ApiEnvelope<T> =
            ApiEnvelope(
                success = true,
                data = data,
                error = null,
                timestamp = now(),
            )

        fun failure(code: String, message: String): ApiEnvelope<Nothing?> =
            ApiEnvelope(
                success = false,
                data = null,
                error = ApiErrorPayload(code = code, message = message),
                timestamp = now(),
            )

        private fun now(): String = OffsetDateTime.now(seoulZone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}

@Schema(description = "공통 에러 정보")
data class ApiErrorPayload(
    @field:Schema(description = "에러 코드", example = "VALIDATION_ERROR")
    val code: String,
    @field:Schema(description = "바로 이해할 수 있는 에러 메시지", example = "요청 값이 올바르지 않습니다.")
    val message: String,
)
