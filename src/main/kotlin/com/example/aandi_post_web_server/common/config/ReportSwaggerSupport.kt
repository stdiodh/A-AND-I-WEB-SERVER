package com.example.aandi_post_web_server.common.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.responses.ApiResponses
import io.swagger.v3.oas.models.security.SecurityRequirement
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.method.HandlerMethod

internal object ReportSwaggerSupport {
    fun reportV1InfoCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.info = Info()
                .title("A&I Report API v1")
                .description(
                    "기존 report/courses 조회 API 문서입니다. " +
                        "실제 인증은 Authorization Bearer JWT 기준으로 동작하며, " +
                        "응답은 기존 공통 envelope(success/data/error/timestamp)와 문자열 에러 코드를 사용합니다."
                )
                .version("v1")
                .license(License().name("Proprietary"))
        }

    fun reportV2InfoCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.info = Info()
                .title("A&I Report API v2")
                .description(
                    "A&I 통신 규약을 따르는 report v2 문서입니다. " +
                        "공통 응답 구조는 success, data, error, timestamp 이며, " +
                        "error 는 code, message, value, alert 를 포함합니다. " +
                        "인증 헤더는 규약 기준의 Authenticate 헤더를 우선 문서화합니다."
                )
                .version("v2")
                .license(License().name("Proprietary"))
        }

    fun reportV1OperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation: io.swagger.v3.oas.models.Operation, _: HandlerMethod ->
            operation.security(listOf(SecurityRequirement().addList("bearerAuth")))
            val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
            upsertErrorResponse(responses, "400", "잘못된 요청", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("400"))
            upsertErrorResponse(responses, "401", "인증 필요", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("401"))
            upsertErrorResponse(responses, "403", "권한 없음", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("403"))
            upsertErrorResponse(responses, "404", "리소스를 찾을 수 없음", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("404"))
            upsertErrorResponse(responses, "409", "중복 또는 충돌", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("409"))
            upsertErrorResponse(responses, "422", "처리할 수 없는 요청", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("422"))
            upsertErrorResponse(responses, "500", "서버 내부 오류", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("500"))
            operation
        }

    fun reportV2OperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation: io.swagger.v3.oas.models.Operation, _: HandlerMethod ->
            operation.security(listOf(SecurityRequirement().addList("reportV2Authenticate")))
            upsertHeaderParameter(
                operation = operation,
                name = "deviceOS",
                description = "호출 기기의 운영체제 정보",
                required = true,
                example = "android",
            )
            upsertHeaderParameter(
                operation = operation,
                name = "Authenticate",
                description = "A&I v2 인증 헤더. `Bearer {accessToken}` 형식으로 전달합니다.",
                required = true,
                example = "Bearer eyJhbGciOiJIUzI1Ni...",
            )
            upsertHeaderParameter(
                operation = operation,
                name = "timestamp",
                description = "클라이언트가 요청을 생성한 시각(ISO-8601)",
                required = true,
                example = "2026-03-25T21:23:36.958558466+09:00",
            )
            upsertHeaderParameter(
                operation = operation,
                name = "salt",
                description = "선택 헤더. 설정 시 `MD5(timestamp + saltSecret)` 규칙을 따릅니다.",
                required = false,
                example = "5f4dcc3b5aa765d61d8327deb882cf99",
            )
            val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
            upsertErrorResponse(responses, "400", "요청 또는 헤더 오류", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("400"))
            upsertErrorResponse(responses, "401", "인증 실패", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("401"))
            upsertErrorResponse(responses, "403", "권한 없음", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("403"))
            upsertErrorResponse(responses, "404", "리소스 없음", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("404"))
            upsertErrorResponse(responses, "409", "중복 또는 충돌", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("409"))
            upsertErrorResponse(responses, "500", "서버 내부 오류", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("500"))
            operation
        }

    private fun upsertHeaderParameter(
        operation: io.swagger.v3.oas.models.Operation,
        name: String,
        description: String,
        required: Boolean,
        example: String,
    ) {
        val parameters = operation.parameters ?: mutableListOf<Parameter>().also { operation.parameters = it }
        val existing = parameters.firstOrNull { it.`in` == "header" && it.name == name }
        if (existing != null) {
            return
        }
        parameters.add(
            Parameter()
                .`in`("header")
                .name(name)
                .required(required)
                .description(description)
                .schema(StringSchema().example(example))
        )
    }

    private fun upsertErrorResponse(
        responses: ApiResponses,
        statusCode: String,
        description: String,
        schemaRef: String,
        examples: Map<String, Map<String, Any?>>,
    ) {
        val apiResponse = responses[statusCode] ?: ApiResponse().description(description).also {
            responses.addApiResponse(statusCode, it)
        }
        if (apiResponse.description.isNullOrBlank()) {
            apiResponse.description = description
        }
        apiResponse.content = Content().addMediaType(
            APPLICATION_JSON_VALUE,
            MediaType().schema(Schema<Any>().`$ref`(schemaRef)).also { mediaType ->
                examples.forEach { (name, value) ->
                    mediaType.addExamples(name, Example().summary(name).value(value))
                }
            }
        )
    }

    private fun v1ErrorExamples(statusCode: String): Map<String, Map<String, Any?>> {
        return when (statusCode) {
            "400" -> linkedMapOf(
                "VALIDATION_ERROR" to v1ErrorEnvelope("VALIDATION_ERROR", "요청 값이 올바르지 않습니다."),
                "INPUT_ERROR" to v1ErrorEnvelope("INPUT_ERROR", "요청 입력을 처리할 수 없습니다."),
            )
            "401" -> linkedMapOf(
                "UNAUTHORIZED" to v1ErrorEnvelope("UNAUTHORIZED", "인증이 필요하거나 토큰이 유효하지 않습니다."),
            )
            "403" -> linkedMapOf(
                "FORBIDDEN" to v1ErrorEnvelope("FORBIDDEN", "요청을 수행할 권한이 없습니다."),
            )
            "404" -> linkedMapOf(
                "NOT_FOUND" to v1ErrorEnvelope("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."),
            )
            "409" -> linkedMapOf(
                "CONFLICT" to v1ErrorEnvelope("CONFLICT", "이미 존재하는 리소스입니다."),
            )
            "422" -> linkedMapOf(
                "UNPROCESSABLE_ENTITY" to v1ErrorEnvelope("UNPROCESSABLE_ENTITY", "요청은 유효하지만 처리할 수 없습니다."),
            )
            "500" -> linkedMapOf(
                "INTERNAL_ERROR" to v1ErrorEnvelope("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),
            )
            else -> emptyMap()
        }
    }

    private fun v2ErrorExamples(statusCode: String): Map<String, Map<String, Any?>> {
        return when (statusCode) {
            "400" -> linkedMapOf(
                "VALIDATE_ERROR" to v2ErrorEnvelope(40301, "weekNo field is invalid", "VALIDATE_ERROR", "입력값 형식이 올바르지 않습니다."),
            )
            "401" -> linkedMapOf(
                "UNAUTHORIZED" to v2ErrorEnvelope(21101, "access token is invalid", "UNAUTHORIZED", "로그인이 필요합니다."),
            )
            "403" -> linkedMapOf(
                "FORBIDDEN" to v2ErrorEnvelope(21201, "user does not have permission for this resource", "FORBIDDEN", "접근 권한이 없습니다."),
            )
            "404" -> linkedMapOf(
                "RESOURCE_NOT_FOUND" to v2ErrorEnvelope(96501, "requested resource does not exist", "RESOURCE_NOT_FOUND", "요청한 정보를 찾을 수 없습니다."),
            )
            "409" -> linkedMapOf(
                "ASSIGNMENT_ALREADY_SUBMITTED" to v2ErrorEnvelope(44501, "assignment has already been submitted", "ASSIGNMENT_ALREADY_SUBMITTED", "이미 제출된 과제입니다."),
            )
            "500" -> linkedMapOf(
                "INTERNAL_SERVER_ERROR" to v2ErrorEnvelope(98801, "unexpected server exception occurred", "INTERNAL_SERVER_ERROR", "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."),
            )
            else -> emptyMap()
        }
    }

    private fun v1ErrorEnvelope(code: String, message: String): Map<String, Any?> {
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

    private fun v2ErrorEnvelope(code: Int, message: String, value: String, alert: String): Map<String, Any?> {
        return linkedMapOf(
            "success" to "FAIL",
            "data" to null,
            "error" to linkedMapOf(
                "code" to code,
                "message" to message,
                "value" to value,
                "alert" to alert,
            ),
            "timestamp" to "2026-03-25T21:23:36.958558466+09:00",
        )
    }
}
