package com.example.aandi_post_web_server.common.config

import io.swagger.v3.oas.models.Operation
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
                .title("A&I v1 API 문서")
                .description(
                    """
                    현재 서버의 전체 v1 API 문서입니다.
                    사용자용 코스 조회 API와 관리자용 코스/수강/과제 관리 API를 함께 포함합니다.
                    실제 인증은 `Authorization: Bearer {JWT}` 기준으로 동작하며,
                    응답은 기존 공통 envelope(success/data/error/timestamp)와 문자열 에러 코드를 사용합니다.
                    """.trimIndent()
                )
                .version("v1")
                .license(License().name("Proprietary"))
        }

    fun reportV2InfoCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            openApi.info = Info()
                .title("A&I v2 클라이언트 계약 문서")
                .description(
                    """
                    프론트엔드, 기획, 운영이 함께 보는 A&I v2 클라이언트 계약 문서입니다.
                    이 문서는 현재 서버의 전체 v2 API를 포함하며, `/v2/courses/**`, `/v2/admin/courses/**`, `/v2/assignments/**` 를 함께 제공합니다.

                    모든 v2 엔드포인트는 동일한 A&I v2 통신규약을 사용합니다.
                    - 공통 헤더: `deviceOS`, `Authenticate`, `timestamp`, `salt`
                    - 공통 응답: `success`, `data`, `error`, `timestamp`
                    - 공통 에러: `error.code(int)`, `error.message`, `error.value`, `error.alert`

                    canonical 경로는 리소스 기준으로 정리되어 있습니다.
                    - 조회: `/v2/courses/**`
                    - 관리자: `/v2/admin/courses/**`
                    - 과제 부가 조회: `/v2/assignments/**`
                    """.trimIndent()
                )
                .version("v2")
                .license(License().name("Proprietary"))
        }

    fun reportV1OperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation: Operation, _: HandlerMethod ->
            operation.security(listOf(SecurityRequirement().addList("bearerAuth")))
            val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
            upsertResponse(responses, "400", "잘못된 요청", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("400"))
            upsertResponse(responses, "401", "인증 필요", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("401"))
            upsertResponse(responses, "403", "권한 없음", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("403"))
            upsertResponse(responses, "404", "리소스를 찾을 수 없음", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("404"))
            upsertResponse(responses, "409", "중복 또는 충돌", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("409"))
            upsertResponse(responses, "422", "처리할 수 없는 요청", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("422"))
            upsertResponse(responses, "500", "서버 내부 오류", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("500"))
            operation
        }

    fun reportV2OperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation: Operation, _: HandlerMethod ->
            customizeV2Operation(operation)
        }

    private fun customizeV2Operation(operation: Operation): Operation {
        operation.security(listOf(SecurityRequirement().addList("v2Authenticate")))
        upsertHeaderParameter(
            operation = operation,
            name = "deviceOS",
            description = "호출 기기의 운영체제 정보",
            required = true,
            example = "android",
        )
        upsertHeaderParameter(
            operation = operation,
            name = "timestamp",
            description = "클라이언트가 요청을 생성한 시각. ISO-8601 또는 epoch milliseconds 문자열을 허용합니다.",
            required = true,
            example = "2026-04-13T18:00:00+09:00",
        )
        upsertHeaderParameter(
            operation = operation,
            name = "salt",
            description = "선택 헤더. 설정 시 `MD5(timestamp + saltSecret)` 규칙을 따릅니다.",
            required = false,
            example = "5f4dcc3b5aa765d61d8327deb882cf99",
        )

        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
        upsertResponse(responses, "400", "요청 또는 헤더 오류", "#/components/schemas/V2ErrorEnvelopeDoc", v2ErrorExamples("400"))
        upsertResponse(responses, "401", "인증 실패", "#/components/schemas/V2ErrorEnvelopeDoc", v2ErrorExamples("401"))
        upsertResponse(responses, "403", "권한 없음", "#/components/schemas/V2ErrorEnvelopeDoc", v2ErrorExamples("403"))
        upsertResponse(responses, "404", "리소스를 찾을 수 없음", "#/components/schemas/V2ErrorEnvelopeDoc", v2ErrorExamples("404"))
        upsertResponse(responses, "409", "중복 또는 충돌", "#/components/schemas/V2ErrorEnvelopeDoc", v2ErrorExamples("409"))
        upsertResponse(responses, "422", "처리할 수 없는 요청", "#/components/schemas/V2ErrorEnvelopeDoc", v2ErrorExamples("422"))
        upsertResponse(responses, "500", "서버 내부 오류", "#/components/schemas/V2ErrorEnvelopeDoc", v2ErrorExamples("500"))
        return operation
    }

    private fun upsertHeaderParameter(
        operation: Operation,
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

    private fun upsertResponse(
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

    private fun v2ErrorExamples(statusCode: String): Map<String, Map<String, Any?>> {
        val example = when (statusCode) {
            "400" -> linkedMapOf(
                "success" to false,
                "data" to null,
                "error" to linkedMapOf(
                    "code" to 40301,
                    "message" to "timestamp 헤더는 epoch milliseconds 또는 ISO-8601 형식이어야 합니다.",
                    "value" to "VALIDATE_ERROR",
                    "alert" to "입력값 형식이 올바르지 않습니다.",
                ),
                "timestamp" to "2026-04-13T18:00:00+09:00",
            )
            "401" -> linkedMapOf(
                "success" to false,
                "data" to null,
                "error" to linkedMapOf(
                    "code" to 21101,
                    "message" to "인증 헤더가 없거나 토큰이 유효하지 않습니다.",
                    "value" to "UNAUTHORIZED",
                    "alert" to "로그인이 필요합니다.",
                ),
                "timestamp" to "2026-04-13T18:00:00+09:00",
            )
            "403" -> linkedMapOf(
                "success" to false,
                "data" to null,
                "error" to linkedMapOf(
                    "code" to 21201,
                    "message" to "요청한 리소스에 접근할 권한이 없습니다.",
                    "value" to "FORBIDDEN",
                    "alert" to "접근 권한이 없습니다.",
                ),
                "timestamp" to "2026-04-13T18:00:00+09:00",
            )
            "404" -> linkedMapOf(
                "success" to false,
                "data" to null,
                "error" to linkedMapOf(
                    "code" to 96501,
                    "message" to "요청한 정보를 찾을 수 없습니다.",
                    "value" to "RESOURCE_NOT_FOUND",
                    "alert" to "요청한 정보를 찾을 수 없습니다.",
                ),
                "timestamp" to "2026-04-13T18:00:00+09:00",
            )
            "409" -> linkedMapOf(
                "success" to false,
                "data" to null,
                "error" to linkedMapOf(
                    "code" to 44501,
                    "message" to "이미 존재하거나 충돌하는 리소스입니다.",
                    "value" to "CONFLICT",
                    "alert" to "중복되거나 충돌하는 요청입니다.",
                ),
                "timestamp" to "2026-04-13T18:00:00+09:00",
            )
            "422" -> linkedMapOf(
                "success" to false,
                "data" to null,
                "error" to linkedMapOf(
                    "code" to 40301,
                    "message" to "요청은 유효하지만 처리할 수 없습니다.",
                    "value" to "UNPROCESSABLE_ENTITY",
                    "alert" to "요청 값을 다시 확인해주세요.",
                ),
                "timestamp" to "2026-04-13T18:00:00+09:00",
            )
            else -> linkedMapOf(
                "success" to false,
                "data" to null,
                "error" to linkedMapOf(
                    "code" to 98801,
                    "message" to "예기치 못한 내부 오류가 발생했습니다.",
                    "value" to "INTERNAL_SERVER_ERROR",
                    "alert" to "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                ),
                "timestamp" to "2026-04-13T18:00:00+09:00",
            )
        }
        return linkedMapOf("default" to example)
    }

    private fun v1ErrorExamples(statusCode: String): Map<String, Map<String, Any?>> {
        val code = when (statusCode) {
            "400" -> "BAD_REQUEST"
            "401" -> "UNAUTHORIZED"
            "403" -> "FORBIDDEN"
            "404" -> "NOT_FOUND"
            "409" -> "CONFLICT"
            "422" -> "UNPROCESSABLE_ENTITY"
            else -> "INTERNAL_ERROR"
        }
        return linkedMapOf(
            "default" to linkedMapOf(
                "success" to false,
                "data" to null,
                "error" to linkedMapOf(
                    "code" to code,
                    "message" to "에러 메시지",
                ),
                "timestamp" to "2026-03-09T12:00:00+09:00",
            )
        )
    }
}
