package com.example.aandi_post_web_server.common.config

import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.Operation
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
                    이 문서는 현재 서버의 전체 v2 API를 포함하며, 현재 기준으로 `/v2/report/**`, `/v2/assignments/**`, `/v2/courses/**`, `/v2/admin/courses/**` 를 함께 제공합니다.

                    인증 헤더 규약은 엔드포인트 계열별로 다릅니다.
                    - `/v2/report/**`: `Authenticate` 헤더와 v2 전용 헤더(`deviceOS`, `timestamp`, `salt`)를 사용합니다.
                    - `/v2/assignments/**`, `/v2/courses/**`, `/v2/admin/courses/**`: `Authorization: Bearer {JWT}` 헤더를 사용합니다.

                    응답 구조도 계열별로 다릅니다.
                    - `/v2/report/**`: report 전용 `ReportApiEnvelope` 와 숫자형 에러 코드를 사용합니다.
                    - `/v2/assignments/**`, `/v2/courses/**`, `/v2/admin/courses/**`: 공통 `ApiEnvelope(success/data/error/timestamp)` 와 문자열 에러 코드를 사용합니다.
                    """.trimIndent()
                )
                .version("v2")
                .license(License().name("Proprietary"))
        }

    fun reportV1OperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation: io.swagger.v3.oas.models.Operation, _: HandlerMethod ->
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
        OperationCustomizer { operation: Operation, handlerMethod: HandlerMethod ->
            if (isAssignmentSubmissionStatusV2Handler(handlerMethod)) {
                customizeAssignmentV2Operation(operation)
            } else if (isBearerEnvelopeV2Handler(handlerMethod)) {
                customizeBearerEnvelopeV2Operation(operation)
            } else {
                customizeReportV2Operation(operation)
            }
        }

    private fun customizeAssignmentV2Operation(operation: Operation): Operation {
        operation.security(listOf(SecurityRequirement().addList("bearerAuth")))
        removeHeaderParameter(operation, "deviceOS")
        removeHeaderParameter(operation, "Authenticate")
        removeHeaderParameter(operation, "timestamp")
        removeHeaderParameter(operation, "salt")

        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
        upsertResponse(
            responses = responses,
            statusCode = "200",
            description = "조회 성공",
            schemaRef = "#/components/schemas/AssignmentSubmissionStatusEnvelopeDoc",
            examples = assignmentSubmissionStatusSuccessExamples(),
        )
        upsertResponse(
            responses = responses,
            statusCode = "401",
            description = "인증 실패",
            schemaRef = "#/components/schemas/ErrorEnvelopeDoc",
            examples = assignmentV2ErrorExamples("401"),
        )
        upsertResponse(
            responses = responses,
            statusCode = "404",
            description = "과제를 찾을 수 없거나 접근할 수 없음",
            schemaRef = "#/components/schemas/ErrorEnvelopeDoc",
            examples = assignmentV2ErrorExamples("404"),
        )
        upsertResponse(
            responses = responses,
            statusCode = "500",
            description = "현재 사용자 publicCode projection 누락 또는 서버 내부 오류",
            schemaRef = "#/components/schemas/ErrorEnvelopeDoc",
            examples = assignmentV2ErrorExamples("500"),
        )
        return operation
    }

    private fun customizeBearerEnvelopeV2Operation(operation: Operation): Operation {
        operation.security(listOf(SecurityRequirement().addList("bearerAuth")))
        removeHeaderParameter(operation, "deviceOS")
        removeHeaderParameter(operation, "Authenticate")
        removeHeaderParameter(operation, "timestamp")
        removeHeaderParameter(operation, "salt")

        val responses = operation.responses ?: ApiResponses().also { operation.responses = it }
        upsertResponse(responses, "400", "잘못된 요청", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("400"))
        upsertResponse(responses, "401", "인증 필요", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("401"))
        upsertResponse(responses, "403", "권한 없음", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("403"))
        upsertResponse(responses, "404", "리소스를 찾을 수 없음", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("404"))
        upsertResponse(responses, "409", "중복 또는 충돌", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("409"))
        upsertResponse(responses, "422", "처리할 수 없는 요청", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("422"))
        upsertResponse(responses, "500", "서버 내부 오류", "#/components/schemas/ErrorEnvelopeDoc", v1ErrorExamples("500"))
        return operation
    }

    private fun customizeReportV2Operation(operation: Operation): Operation {
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
            description = "클라이언트가 요청을 생성한 시각. ISO-8601 또는 epoch milliseconds 문자열을 허용합니다.",
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
        upsertResponse(responses, "400", "요청 또는 헤더 오류", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("400"))
        upsertResponse(responses, "401", "인증 실패", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("401"))
        upsertResponse(responses, "403", "권한 없음", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("403"))
        upsertResponse(responses, "404", "리소스 없음", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("404"))
        upsertResponse(responses, "409", "중복 또는 충돌", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("409"))
        upsertResponse(responses, "500", "서버 내부 오류", "#/components/schemas/ReportV2ErrorEnvelopeDoc", v2ErrorExamples("500"))
        return operation
    }

    private fun isAssignmentSubmissionStatusV2Handler(handlerMethod: HandlerMethod): Boolean =
        handlerMethod.beanType.name == "com.example.aandi_post_web_server.assignment.v2.controller.AssignmentSubmissionStatusV2Controller"

    private fun isBearerEnvelopeV2Handler(handlerMethod: HandlerMethod): Boolean =
        handlerMethod.beanType.packageName.contains(".course.v2.")

    private fun removeHeaderParameter(operation: Operation, name: String) {
        operation.parameters = operation.parameters
            ?.filterNot { it.`in` == "header" && it.name == name }
            ?.toMutableList()
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

    private fun assignmentSubmissionStatusSuccessExamples(): Map<String, Map<String, Any?>> =
        linkedMapOf(
            "submitted_true" to linkedMapOf(
                "success" to true,
                "data" to linkedMapOf(
                    "assignmentId" to "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001",
                    "submitted" to true,
                    "firstCompletedAt" to "2026-04-13T08:20:11Z",
                    "lastCompletedAt" to "2026-04-13T08:40:11Z",
                    "latestScore" to 90,
                    "passedCases" to 9,
                    "totalCases" to 10,
                ),
                "error" to null,
                "timestamp" to "2026-04-13T17:40:11+09:00",
            ),
            "submitted_false_projection_not_found" to linkedMapOf(
                "success" to true,
                "data" to linkedMapOf(
                    "assignmentId" to "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001",
                    "submitted" to false,
                    "firstCompletedAt" to null,
                    "lastCompletedAt" to null,
                    "latestScore" to null,
                    "passedCases" to null,
                    "totalCases" to null,
                ),
                "error" to null,
                "timestamp" to "2026-04-13T17:40:11+09:00",
            ),
        )

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
                "VALIDATE_ERROR" to v2ErrorEnvelope(40301, "timestamp header must be epoch milliseconds or ISO-8601.", "VALIDATE_ERROR", "입력값 형식이 올바르지 않습니다."),
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

    private fun assignmentV2ErrorExamples(statusCode: String): Map<String, Map<String, Any?>> {
        return when (statusCode) {
            "401" -> linkedMapOf(
                "UNAUTHORIZED" to v1ErrorEnvelope("UNAUTHORIZED", "인증이 필요하거나 토큰이 유효하지 않습니다."),
            )
            "404" -> linkedMapOf(
                "NOT_FOUND" to v1ErrorEnvelope("NOT_FOUND", "요청한 과제를 찾을 수 없거나 조회 권한이 없습니다."),
            )
            "500" -> linkedMapOf(
                "INTERNAL_ERROR_PUBLIC_CODE_PROJECTION_MISSING" to
                    v1ErrorEnvelope("INTERNAL_ERROR", "현재 사용자 publicCode projection 을 찾을 수 없습니다: user-1"),
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
            "success" to false,
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
