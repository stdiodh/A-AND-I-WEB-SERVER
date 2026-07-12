package com.example.aandi_post_web_server.common.error

import com.example.aandi_post_web_server.common.error.v2.AssignmentDeactivatedException
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.core.MethodParameter
import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.codec.CodecException
import org.springframework.core.codec.DecodingException
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.reactive.resource.NoResourceFoundException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException

class ErrorResponseFactoryTest : StringSpec({

    val factory = ErrorResponseFactory()

    "ResponseStatusException status를 v1 공통 에러 코드로 매핑한다" {
        val cases = listOf(
            HttpStatus.BAD_REQUEST to ErrorCode.BAD_REQUEST,
            HttpStatus.UNAUTHORIZED to ErrorCode.UNAUTHORIZED,
            HttpStatus.FORBIDDEN to ErrorCode.FORBIDDEN,
            HttpStatus.NOT_FOUND to ErrorCode.NOT_FOUND,
            HttpStatus.CONFLICT to ErrorCode.CONFLICT,
            HttpStatus.UNPROCESSABLE_ENTITY to ErrorCode.UNPROCESSABLE_ENTITY,
            HttpStatus.INTERNAL_SERVER_ERROR to ErrorCode.INTERNAL_ERROR,
            HttpStatus.SERVICE_UNAVAILABLE to ErrorCode.INTERNAL_ERROR,
        )

        cases.forEach { (status, expectedCode) ->
            val result = factory.fromResponseStatus(
                exchange = exchange("/v1/error/status/${status.value()}"),
                ex = ResponseStatusException(status, "custom reason ${status.value()}"),
            )

            result.status shouldBe status
            result.body.success shouldBe false
            result.body.error?.code shouldBe expectedCode.name
            result.body.error?.message shouldBe if (status.is5xxServerError) {
                ErrorCode.INTERNAL_ERROR.defaultMessage
            } else {
                "custom reason ${status.value()}"
            }
        }
    }

    "필수값 누락 ServerWebInputException은 MISSING_REQUIRED_VALUE로 매핑한다" {
        val result = factory.fromServerWebInput(
            exchange = exchange("/v1/error/missing"),
            ex = ServerWebInputException("Missing request header 'deviceOS'"),
        )

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe ErrorCode.MISSING_REQUIRED_VALUE.name
        result.body.error?.message shouldContain "Missing request header"
    }

    "잘못된 JSON은 위치 정보를 포함한 JSON_PARSE_ERROR로 매핑한다" {
        val parseException = invalidJsonException()

        val result = factory.fromMalformedJson(exchange("/v1/error/json"), parseException)

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe ErrorCode.JSON_PARSE_ERROR.name
        result.body.error?.message shouldContain "line="
        result.body.error?.message shouldContain "column="
    }

    "중첩된 enum InvalidFormatException은 허용값을 포함한 ENUM_MISMATCH로 매핑한다" {
        val invalidFormat = invalidEnumException()

        val result = factory.fromThrowable(
            exchange = exchange("/v1/error/enum"),
            throwable = RuntimeException("wrapper", invalidFormat),
        )

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe ErrorCode.ENUM_MISMATCH.name
        result.body.error?.message shouldContain "mode 값 'BROKEN'"
        result.body.error?.message shouldContain "허용값: [PUBLIC, HIDDEN]"
    }

    "알 수 없는 예외는 INTERNAL_ERROR와 request id header로 매핑한다" {
        val exchange = exchange("/v1/error/internal")

        val result = factory.fromThrowable(exchange, IllegalStateException("unexpected failure"))

        result.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        result.body.error?.code shouldBe ErrorCode.INTERNAL_ERROR.name
        result.body.error?.message shouldBe ErrorCode.INTERNAL_ERROR.defaultMessage
        exchange.response.headers.getFirst(RequestIdSupport.HEADER_NAME) shouldBe
            RequestIdSupport.resolveRequestId(exchange)
    }

    "fromThrowable은 인증, 권한, not found, 과제 비활성 예외를 공통 envelope로 dispatch한다" {
        val cases = listOf(
            BadCredentialsException("bad credentials") to (HttpStatus.UNAUTHORIZED to ErrorCode.UNAUTHORIZED),
            AccessDeniedException("denied") to (HttpStatus.FORBIDDEN to ErrorCode.FORBIDDEN),
            NoResourceFoundException("/missing") to (HttpStatus.NOT_FOUND to ErrorCode.NOT_FOUND),
            AssignmentDeactivatedException("assignment disabled") to (HttpStatus.SERVICE_UNAVAILABLE to ErrorCode.ASSIGNMENT_DEACTIVATED),
        )

        cases.forEach { (throwable, expected) ->
            val result = factory.fromThrowable(exchange("/v1/error/${expected.second.name.lowercase()}"), throwable)

            result.status shouldBe expected.first
            result.body.success shouldBe false
            result.body.error?.code shouldBe expected.second.name
        }
    }

    "명시적인 과제 비활성 예외는 사용자 안내 문구를 유지한다" {
        val result = factory.fromThrowable(
            exchange("/v1/error/assignment-deactivated-message"),
            AssignmentDeactivatedException("과제 제출 기간이 아닙니다."),
        )

        result.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
        result.body.error?.code shouldBe ErrorCode.ASSIGNMENT_DEACTIVATED.name
        result.body.error?.message shouldBe "과제 제출 기간이 아닙니다."
    }

    "fromThrowable은 validation, input, response status, decoding 예외를 전용 mapper로 dispatch한다" {
        val bindingResult = BeanPropertyBindingResult(ValidationTarget(""), "request")
        bindingResult.addError(FieldError("request", "title", "", false, null, null, "title is required"))
        val cases = listOf(
            validationException(bindingResult) to ErrorCode.VALIDATION_ERROR,
            ServerWebInputException("Missing request header 'deviceOS'") to ErrorCode.MISSING_REQUIRED_VALUE,
            ResponseStatusException(HttpStatus.CONFLICT, "duplicate") to ErrorCode.CONFLICT,
            DecodingException("decode failed") to ErrorCode.JSON_PARSE_ERROR,
            CodecException("codec failed") to ErrorCode.JSON_PARSE_ERROR,
        )

        cases.forEach { (throwable, expectedCode) ->
            val result = factory.fromThrowable(exchange("/v1/error/dispatch-${expectedCode.name.lowercase()}"), throwable)

            result.body.success shouldBe false
            result.body.error?.code shouldBe expectedCode.name
        }
    }

    "request id가 없으면 error response 생성 시 새 request id header를 설정한다" {
        val exchange = exchangeWithoutRequestId("/v1/error/not-found")

        val result = factory.fromNotFound(exchange)

        result.status shouldBe HttpStatus.NOT_FOUND
        exchange.response.headers.getFirst(RequestIdSupport.HEADER_NAME).shouldNotBeNull()
    }

    "blank fallback message는 기본 에러 메시지로 대체한다" {
        val unauthorized = factory.unauthorized(exchange("/v1/error/auth"), " ")
        val blankReason = factory.fromResponseStatus(
            exchange = exchange("/v1/error/blank-reason"),
            ex = ResponseStatusException(HttpStatus.BAD_REQUEST, " "),
        )

        unauthorized.body.error?.message shouldBe ErrorCode.UNAUTHORIZED.defaultMessage
        blankReason.body.error?.message shouldBe ErrorCode.BAD_REQUEST.defaultMessage
    }

    "public helper methods produce the documented common error envelopes" {
        val forbidden = factory.forbidden(exchange("/v1/error/forbidden"), "blocked")
        val internal = factory.internalError(exchange("/v1/error/internal-helper"))
        val deactivated = factory.assignmentDeactivated(exchange("/v1/error/deactivated"), null)

        forbidden.status shouldBe HttpStatus.FORBIDDEN
        forbidden.body.error?.code shouldBe ErrorCode.FORBIDDEN.name
        forbidden.body.error?.message shouldBe "blocked"
        internal.status shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        internal.body.error?.message shouldBe ErrorCode.INTERNAL_ERROR.defaultMessage
        deactivated.status shouldBe HttpStatus.SERVICE_UNAVAILABLE
        deactivated.body.error?.code shouldBe ErrorCode.ASSIGNMENT_DEACTIVATED.name
    }

    "field validation 메시지가 비어 있으면 기본 검증 문구를 사용한다" {
        val bindingResult = BeanPropertyBindingResult(ValidationTarget(""), "request")
        bindingResult.addError(FieldError("request", "title", " ", false, null, null, " "))

        val result = factory.fromValidation(exchange("/v1/error/field-validation"), validationException(bindingResult))

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe ErrorCode.VALIDATION_ERROR.name
        result.body.error?.message shouldBe "title: 값이 유효하지 않습니다."
    }

    "object validation 오류는 field 오류가 없을 때 fallback message로 사용된다" {
        val bindingResult = BeanPropertyBindingResult(ValidationTarget("title"), "request")
        bindingResult.addError(ObjectError("request", "global validation failed"))

        val result = factory.fromValidation(exchange("/v1/error/object-validation"), validationException(bindingResult))

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.message shouldBe "global validation failed"
    }

    "ServerWebInputException의 ConversionFailedException enum cause는 ENUM_MISMATCH로 매핑한다" {
        val conversionFailed = ConversionFailedException(
            TypeDescriptor.valueOf(String::class.java),
            TypeDescriptor.valueOf(DemoMode::class.java),
            "BROKEN",
            IllegalArgumentException("bad enum"),
        )
        val exception = ServerWebInputException("type mismatch", null, conversionFailed)

        val result = factory.fromServerWebInput(exchange("/v1/error/conversion-enum"), exception)

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe ErrorCode.ENUM_MISMATCH.name
        result.body.error?.message shouldContain "BROKEN"
        result.body.error?.message shouldContain "PUBLIC"
    }

    "non-enum ConversionFailedException은 INPUT_ERROR로 매핑한다" {
        val conversionFailed = ConversionFailedException(
            TypeDescriptor.valueOf(String::class.java),
            TypeDescriptor.valueOf(Int::class.javaObjectType),
            "abc",
            NumberFormatException("not a number"),
        )

        val result = factory.fromConversionFailed(exchange("/v1/error/conversion-input"), conversionFailed)

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe ErrorCode.INPUT_ERROR.name
        result.body.error?.message shouldContain "Failed to convert"
    }

    "non-enum InvalidFormatException은 INPUT_ERROR로 매핑한다" {
        val invalidFormat = invalidNumberException()

        val result = factory.fromInvalidFormat(exchange("/v1/error/invalid-number"), invalidFormat)

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe ErrorCode.INPUT_ERROR.name
        result.body.error?.message shouldContain "Cannot deserialize"
    }

    "DecodingException은 JSON_PARSE_ERROR로 매핑하고 parse cause가 있으면 위치를 보존한다" {
        val withParseCause = factory.fromThrowable(
            exchange = exchange("/v1/error/decode-with-cause"),
            throwable = DecodingException("decode failed", invalidJsonException()),
        )
        val withoutParseCause = factory.fromJsonDecode(
            exchange = exchange("/v1/error/decode-without-cause"),
            throwable = DecodingException("decode failed"),
        )

        withParseCause.status shouldBe HttpStatus.BAD_REQUEST
        withParseCause.body.error?.code shouldBe ErrorCode.JSON_PARSE_ERROR.name
        withParseCause.body.error?.message shouldContain "line="
        withoutParseCause.body.error?.code shouldBe ErrorCode.JSON_PARSE_ERROR.name
        withoutParseCause.body.error?.message shouldBe "decode failed"
    }

    "fromJsonDecode는 nested JsonParseException cause를 위치 정보가 있는 JSON_PARSE_ERROR로 매핑한다" {
        val result = factory.fromJsonDecode(
            exchange = exchange("/v1/error/nested-json-parse"),
            throwable = IllegalStateException("wrapper", invalidJsonException()),
        )

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.code shouldBe ErrorCode.JSON_PARSE_ERROR.name
        result.body.error?.message shouldContain "line="
        result.body.error?.message shouldContain "column="
    }

    "위치 정보가 없는 JsonParseException은 기본 JSON_PARSE_ERROR 메시지를 사용한다" {
        val result = factory.fromMalformedJson(
            exchange = exchange("/v1/error/json-no-location"),
            ex = JsonParseException(null, "bad json"),
        )

        result.status shouldBe HttpStatus.BAD_REQUEST
        result.body.error?.message shouldBe ErrorCode.JSON_PARSE_ERROR.defaultMessage
    }
})

private fun exchange(path: String): MockServerWebExchange =
    MockServerWebExchange.from(
        MockServerHttpRequest.get(path)
            .header(RequestIdSupport.HEADER_NAME, "req-error-factory")
    )

private fun exchangeWithoutRequestId(path: String): MockServerWebExchange =
    MockServerWebExchange.from(MockServerHttpRequest.get(path))

private fun validationException(bindingResult: BeanPropertyBindingResult): WebExchangeBindException {
    val method = ValidationMethodHolder::class.java.getDeclaredMethod("handle", ValidationTarget::class.java)
    return WebExchangeBindException(MethodParameter(method, 0), bindingResult)
}

private fun invalidJsonException(): JsonParseException =
    runCatching {
        jacksonObjectMapper().readTree("""{"name": """)
    }.exceptionOrNull() as JsonParseException

private fun invalidEnumException(): InvalidFormatException =
    runCatching {
        jacksonObjectMapper().readValue<EnumHolder>("""{"mode":"BROKEN"}""")
    }.exceptionOrNull() as InvalidFormatException

private fun invalidNumberException(): InvalidFormatException =
    runCatching {
        jacksonObjectMapper().readValue<NumberHolder>("""{"count":"abc"}""")
    }.exceptionOrNull() as InvalidFormatException

private data class EnumHolder(
    val mode: DemoMode,
)

private data class NumberHolder(
    val count: Int,
)

private data class ValidationTarget(
    val title: String,
)

private class ValidationMethodHolder {
    @Suppress("UNUSED_PARAMETER")
    fun handle(target: ValidationTarget) {
    }
}

private enum class DemoMode {
    PUBLIC,
    HIDDEN,
}
