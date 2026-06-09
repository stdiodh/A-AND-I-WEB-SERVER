package com.example.aandi_post_web_server.common.error

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
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
            result.body.error?.message shouldBe "custom reason ${status.value()}"
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
        result.body.error?.message shouldBe "unexpected failure"
        exchange.response.headers.getFirst(RequestIdSupport.HEADER_NAME) shouldBe
            RequestIdSupport.resolveRequestId(exchange)
    }
})

private fun exchange(path: String): MockServerWebExchange =
    MockServerWebExchange.from(
        MockServerHttpRequest.get(path)
            .header(RequestIdSupport.HEADER_NAME, "req-error-factory")
    )

private fun invalidJsonException(): JsonParseException =
    runCatching {
        jacksonObjectMapper().readTree("""{"name": """)
    }.exceptionOrNull() as JsonParseException

private fun invalidEnumException(): InvalidFormatException =
    runCatching {
        jacksonObjectMapper().readValue<EnumHolder>("""{"mode":"BROKEN"}""")
    }.exceptionOrNull() as InvalidFormatException

private data class EnumHolder(
    val mode: DemoMode,
)

private enum class DemoMode {
    PUBLIC,
    HIDDEN,
}
