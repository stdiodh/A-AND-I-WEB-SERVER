package com.example.aandi_post_web_server.common.error

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ResponseStatusException
import reactor.test.StepVerifier

class GlobalWebExceptionHandlerTest : StringSpec({
    val objectMapper = jacksonObjectMapper()
    val logger = LoggerFactory.getLogger(GlobalWebExceptionHandler::class.java) as Logger
    val appender = ListAppender<ILoggingEvent>()

    beforeSpec {
        appender.start()
        logger.addAppender(appender)
    }

    afterSpec {
        logger.detachAppender(appender)
        appender.stop()
    }

    beforeTest {
        appender.list.clear()
    }

    "이미 커밋된 응답은 다시 쓰지 않고 원래 예외를 전파한다" {
        val handler = GlobalWebExceptionHandler(ErrorResponseFactory(), objectMapper)
        val exchange = webExchange("/v1/courses")
        exchange.response.setStatusCode(HttpStatus.OK)
        exchange.response.setComplete().block()
        val failure = IllegalStateException("late failure")

        StepVerifier.create(handler.handle(exchange, failure))
            .expectErrorSatisfies { error -> error shouldBeSameInstanceAs failure }
            .verify()

        exchange.response.statusCode shouldBe HttpStatus.OK
        appender.list.size shouldBe 0
    }

    "v1 알 수 없는 예외는 500 JSON envelope와 request id를 쓰고 ERROR 로그를 남긴다" {
        val handler = GlobalWebExceptionHandler(ErrorResponseFactory(), objectMapper)
        val exchange = webExchange("/v1/courses")

        StepVerifier.create(handler.handle(exchange, IllegalStateException("db host=internal"))).verifyComplete()

        exchange.response.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        exchange.response.headers.contentType shouldBe MediaType.APPLICATION_JSON
        exchange.response.headers.getFirst(RequestIdSupport.HEADER_NAME) shouldBe "req-web-1"
        val body = exchange.bodyJson(objectMapper)
        body["success"].asBoolean() shouldBe false
        body["error"]["code"].asText() shouldBe "INTERNAL_ERROR"
        body["error"]["message"].asText() shouldBe ErrorCode.INTERNAL_ERROR.defaultMessage
        val event = appender.list.single()
        event.level shouldBe Level.ERROR
        event.formattedMessage shouldBe "[requestId=req-web-1] GET /v1/courses -> 500 INTERNAL_ERROR (IllegalStateException)"
    }

    "v2 4xx 예외는 v2 envelope로 쓰고 WARN 로그를 남긴다" {
        val handler = GlobalWebExceptionHandler(ErrorResponseFactory(), objectMapper)
        val exchange = webExchange("/v2/courses/missing")

        StepVerifier.create(
            handler.handle(exchange, ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다: missing")),
        ).verifyComplete()

        exchange.response.statusCode shouldBe HttpStatus.NOT_FOUND
        exchange.response.headers.getFirst(RequestIdSupport.HEADER_NAME) shouldBe "req-web-1"
        val body = exchange.bodyJson(objectMapper)
        body["success"].asBoolean() shouldBe false
        body["error"]["code"].asInt() shouldBe 96501
        body["error"]["value"].asText() shouldBe "RESOURCE_NOT_FOUND"
        body["error"]["message"].asText() shouldBe "코스를 찾을 수 없습니다: missing"
        val event = appender.list.single()
        event.level shouldBe Level.WARN
        event.formattedMessage shouldBe "[requestId=req-web-1] GET /v2/courses/missing -> 404 96501 (ResponseStatusException)"
    }

    "v1 응답 직렬화에 실패하면 상태 코드는 유지하고 INTERNAL_ERROR 대체 본문을 쓴다" {
        val handler = GlobalWebExceptionHandler(ErrorResponseFactory(), FailingObjectMapper(failures = Int.MAX_VALUE))
        val exchange = webExchange("/v1/courses/missing")

        StepVerifier.create(
            handler.handle(exchange, ResponseStatusException(HttpStatus.NOT_FOUND, "missing")),
        ).verifyComplete()

        exchange.response.statusCode shouldBe HttpStatus.NOT_FOUND
        val body = exchange.bodyJson(objectMapper)
        body["success"].asBoolean() shouldBe false
        body["data"].isNull shouldBe true
        body["error"]["code"].asText() shouldBe "INTERNAL_ERROR"
        body["error"]["message"].asText() shouldBe "서버 내부 오류가 발생했습니다."
        body["timestamp"].asText().endsWith("+09:00") shouldBe true
        appender.list.single().level shouldBe Level.WARN
    }

    "v2 응답 직렬화에 실패하면 v2 INTERNAL_ERROR 본문으로 대체한다" {
        val handler = GlobalWebExceptionHandler(ErrorResponseFactory(), FailingObjectMapper(failures = 1))
        val exchange = webExchange("/v2/courses")

        StepVerifier.create(
            handler.handle(exchange, ResponseStatusException(HttpStatus.CONFLICT, "duplicate")),
        ).verifyComplete()

        exchange.response.statusCode shouldBe HttpStatus.CONFLICT
        val body = exchange.bodyJson(objectMapper)
        body["error"]["code"].asInt() shouldBe V2ErrorCode.INTERNAL_ERROR.code
        body["error"]["message"].asText() shouldBe V2ErrorCode.INTERNAL_ERROR.messageTemplate
    }
})

private class FailingObjectMapper(private val failures: Int) : ObjectMapper() {
    private var calls = 0

    init {
        registerKotlinModule()
    }

    override fun writeValueAsBytes(value: Any?): ByteArray {
        if (calls++ < failures) {
            throw JsonMappingException(null, "serialization failed")
        }
        return super.writeValueAsBytes(value)
    }
}

private fun webExchange(path: String): MockServerWebExchange =
    MockServerWebExchange.from(
        MockServerHttpRequest.get(path).header(RequestIdSupport.HEADER_NAME, "req-web-1").build(),
    )

private fun MockServerWebExchange.bodyJson(objectMapper: ObjectMapper): JsonNode =
    objectMapper.readTree(requireNotNull(response.bodyAsString.block()))
