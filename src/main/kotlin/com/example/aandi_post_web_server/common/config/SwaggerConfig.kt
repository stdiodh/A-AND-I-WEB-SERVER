package com.example.aandi_post_web_server.common.config

import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.method.HandlerMethod

@Configuration
class SwaggerConfig(
    @Value("\${swagger.server.url}") private val serverUrl: String,
) {
    @Bean
    fun openApi(): OpenAPI {
        return OpenAPI()
            .addServersItem(Server().url(serverUrl))
            .components(
                Components().addSecuritySchemes(
                    "bearerAuth",
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
            .info(swaggerInfo())
    }

    private fun swaggerInfo(): Info = Info()
        .title("Report Service API")
        .description(
            "코스, 수강, 과제 API 문서입니다. " +
                "모든 API는 Bearer JWT를 사용합니다. " +
                "과제 관련 assignmentId는 UUID를 사용하며, 사용자에게는 startAt이 지난 과제만 공개됩니다. " +
                "수강생 등록은 auth 이벤트로 report 서버에 동기화된 users 데이터를 기준으로 처리합니다.",
        )
        .version("v1")
        .license(License().name("Proprietary"))

    @Bean
    fun globalErrorResponseCustomizer(): OperationCustomizer {
        return OperationCustomizer { operation: Operation, _: HandlerMethod ->
            val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
            upsertErrorResponse(responses, "400", "잘못된 요청")
            upsertErrorResponse(responses, "401", "인증 필요")
            upsertErrorResponse(responses, "403", "권한 없음")
            upsertErrorResponse(responses, "404", "리소스를 찾을 수 없음")
            upsertErrorResponse(responses, "409", "중복 또는 충돌")
            upsertErrorResponse(responses, "422", "처리할 수 없는 요청")
            upsertErrorResponse(responses, "500", "서버 내부 오류")
            operation
        }
    }

    private fun upsertErrorResponse(responses: ApiResponses, statusCode: String, description: String) {
        val apiResponse = responses[statusCode] ?: ApiResponse().description(description).also {
            responses.addApiResponse(statusCode, it)
        }

        if (apiResponse.description.isNullOrBlank()) {
            apiResponse.description = description
        }

        apiResponse.content = Content().addMediaType(APPLICATION_JSON_VALUE, errorMediaType(statusCode))
    }

    private fun errorMediaType(statusCode: String): MediaType {
        val mediaType = MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/ErrorEnvelopeDoc"))
        errorExamples(statusCode).forEach { (name, value) ->
            mediaType.addExamples(name, Example().summary(name).value(value))
        }
        return mediaType
    }

    private fun errorExamples(statusCode: String): Map<String, Map<String, Any?>> {
        return when (statusCode) {
            "400" -> linkedMapOf(
                "VALIDATION_ERROR" to errorEnvelope("VALIDATION_ERROR", "요청 값이 올바르지 않습니다."),
                "INPUT_ERROR" to errorEnvelope("INPUT_ERROR", "요청 입력을 처리할 수 없습니다."),
                "JSON_PARSE_ERROR" to errorEnvelope("JSON_PARSE_ERROR", "요청 본문(JSON) 형식이 올바르지 않습니다."),
                "ENUM_MISMATCH_PHASE" to errorEnvelope(
                    "ENUM_MISMATCH",
                    "metadata.phase 값 'BAS1C' 은(는) 올바르지 않습니다. 허용값: [BASIC, CS, FRAMEWORK]",
                ),
                "ENUM_MISMATCH_STATUS" to errorEnvelope(
                    "ENUM_MISMATCH",
                    "status 값 'ACT1VE' 은(는) 올바르지 않습니다. 허용값: [DRAFT, PUBLISHED]",
                ),
                "ENUM_MISMATCH_TRACK" to errorEnvelope(
                    "ENUM_MISMATCH",
                    "track 값 'FLL' 은(는) 올바르지 않습니다. 허용값: [NO, FL, SP]",
                ),
                "INVALID_ASSIGNMENT_ID" to errorEnvelope(
                    "BAD_REQUEST",
                    "assignmentId는 UUID 형식이어야 합니다.",
                ),
                "MISSING_REQUIRED_VALUE" to errorEnvelope("MISSING_REQUIRED_VALUE", "필수 요청 값이 누락되었습니다."),
                "BAD_REQUEST" to errorEnvelope("BAD_REQUEST", "잘못된 요청입니다."),
            )

            "401" -> linkedMapOf(
                "UNAUTHORIZED" to errorEnvelope("UNAUTHORIZED", "인증이 필요하거나 토큰이 유효하지 않습니다."),
            )

            "403" -> linkedMapOf(
                "FORBIDDEN" to errorEnvelope("FORBIDDEN", "요청을 수행할 권한이 없습니다."),
            )

            "404" -> linkedMapOf(
                "NOT_FOUND" to errorEnvelope("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
            )

            "409" -> linkedMapOf(
                "CONFLICT" to errorEnvelope("CONFLICT", "이미 존재하는 리소스입니다."),
            )

            "422" -> linkedMapOf(
                "UNPROCESSABLE_ENTITY" to errorEnvelope("UNPROCESSABLE_ENTITY", "요청은 유효하지만 처리할 수 없습니다."),
            )

            "500" -> linkedMapOf(
                "INTERNAL_ERROR" to errorEnvelope("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),
            )

            else -> emptyMap()
        }
    }

    private fun errorEnvelope(code: String, message: String): Map<String, Any?> {
        return linkedMapOf(
            "success" to false,
            "data" to null,
            "error" to linkedMapOf(
                "code" to code,
                "message" to message,
            ),
            "timestamp" to "2026-03-09T12:00:00+09:00",
        )
    }
}
