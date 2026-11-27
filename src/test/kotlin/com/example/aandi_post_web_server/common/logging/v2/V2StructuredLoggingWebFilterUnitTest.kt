package com.example.aandi_post_web_server.common.logging.v2

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.aandi_post_web_server.common.error.RequestIdSupport
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.nio.charset.StandardCharsets

class V2StructuredLoggingWebFilterUnitTest : StringSpec({
    val objectMapper = jacksonObjectMapper()
    val logger = LoggerFactory.getLogger(V2StructuredLoggingWebFilter.STRUCTURED_LOGGER_NAME) as Logger
    val appender = ListAppender<ILoggingEvent>()

    fun filter(maxBodyBytes: Int = 1024): V2StructuredLoggingWebFilter {
        val properties = V2StructuredLoggingProperties(env = "test", maxBodyBytes = maxBodyBytes)
        return V2StructuredLoggingWebFilter(
            formatter = V2StructuredLogFormatter(
                objectMapper = objectMapper,
                sanitizer = V2StructuredLogSanitizer(objectMapper),
                properties = properties,
                environment = MockEnvironment(),
            ),
            properties = properties,
        )
    }

    fun loggedJson(): JsonNode = objectMapper.readTree(appender.list.single().formattedMessage)

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

    "v2 경로가 아니면 원래 exchange로 체인을 호출하고 로그를 남기지 않는다" {
        val exchange = filterExchange(MockServerHttpRequest.get("/v1/courses"))
        var passedExchange: ServerWebExchange? = null

        StepVerifier.create(
            filter().filter(exchange) { ex ->
                passedExchange = ex
                Mono.empty()
            },
        ).verifyComplete()

        passedExchange shouldBeSameInstanceAs exchange
        appender.list.shouldBeEmpty()
    }

    "요청과 JSON 응답 본문을 캡처해 마스킹된 INFO 로그를 남긴다" {
        val exchange = filterExchange(
            MockServerHttpRequest.post("/v2/admin/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"slug":"back-basic","password":"pw"}"""),
        )

        StepVerifier.create(
            filter().filter(exchange, respondingChain(HttpStatus.CREATED, MediaType.APPLICATION_JSON, """{"success":true,"data":{"id":"c-1"}}""")),
        ).verifyComplete()

        val payload = loggedJson()
        appender.list.single().level shouldBe Level.INFO
        payload["http"]["statusCode"].asInt() shouldBe 201
        payload["request"]["body"]["slug"].asText() shouldBe "back-basic"
        payload["request"]["body"]["password"].asText() shouldBe "****"
        payload["response"]["success"].asBoolean() shouldBe true
        payload["response"]["data"]["reason"].asText() shouldBe "response-data-omitted"
        payload["actor"]["isAuthenticated"].asBoolean() shouldBe false
        payload["actor"]["userId"].isNull shouldBe true
    }

    "최대 크기를 넘는 요청 본문은 잘라내고 전체 바이트 수와 size-limit 요약만 남긴다" {
        val exchange = filterExchange(
            MockServerHttpRequest.post("/v2/admin/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Flux.just(buffer("""{"title":"12345"""), buffer("""67890","x":1}"""))),
        )

        StepVerifier.create(filter(maxBodyBytes = 8).filter(exchange, respondingChain(HttpStatus.OK, null, null))).verifyComplete()

        val body = loggedJson()["request"]["body"]
        body["omitted"].asBoolean() shouldBe true
        body["reason"].asText() shouldBe "size-limit"
        body["truncated"].asBoolean() shouldBe true
        body["capturedBytes"].asInt() shouldBe 8
        body["totalBytes"].asInt() shouldBe 28
    }

    "multipart와 octet-stream 요청 본문은 내용을 캡처하지 않는다" {
        listOf(MediaType.MULTIPART_FORM_DATA, MediaType.APPLICATION_OCTET_STREAM, MediaType.IMAGE_PNG).forEach { contentType ->
            appender.list.clear()
            val exchange = filterExchange(
                MockServerHttpRequest.post("/v2/uploads").contentType(contentType).body("secret-binary-content"),
            )

            StepVerifier.create(filter().filter(exchange, respondingChain(HttpStatus.OK, null, null))).verifyComplete()

            val body = loggedJson()["request"]["body"]
            body["reason"].asText() shouldBe "unsupported-content-type"
            body["capturedBytes"].asInt() shouldBe 0
            appender.list.single().formattedMessage.contains("secret-binary-content") shouldBe false
        }
    }

    "캡처하지 않는 응답 content type은 본문을 남기지 않고 실패 시 생략 사유를 기록한다" {
        listOf(MediaType.IMAGE_PNG, MediaType.APPLICATION_XML, MediaType.MULTIPART_FORM_DATA, MediaType("video", "mp4")).forEach { contentType ->
            appender.list.clear()
            val exchange = filterExchange(MockServerHttpRequest.get("/v2/files/1"))

            StepVerifier.create(
                filter().filter(exchange, respondingChain(HttpStatus.BAD_REQUEST, contentType, """{"success":false}""")),
            ).verifyComplete()

            val payload = loggedJson()
            appender.list.single().level shouldBe Level.WARN
            payload["response"]["error"]["message"].asText() shouldBe "unsupported-content-type"
        }
    }

    "problem+json, text, form 응답과 content type 없는 응답은 본문을 캡처한다" {
        listOf(
            MediaType.APPLICATION_PROBLEM_JSON,
            MediaType.TEXT_PLAIN,
            MediaType.APPLICATION_FORM_URLENCODED,
            null,
        ).forEach { contentType ->
            appender.list.clear()
            val exchange = filterExchange(MockServerHttpRequest.get("/v2/courses/missing"))
            val body = """{"success":false,"error":{"code":96501,"message":"없음","value":"RESOURCE_NOT_FOUND","alert":"없음"}}"""

            StepVerifier.create(filter().filter(exchange, respondingChain(HttpStatus.NOT_FOUND, contentType, body))).verifyComplete()

            loggedJson()["response"]["error"]["code"].asInt() shouldBe 96501
        }
    }

    "writeAndFlushWith 응답도 캡처하고 캡처 불가 타입은 생략한다" {
        val captured = filterExchange(MockServerHttpRequest.get("/v2/stream"))
        StepVerifier.create(
            filter().filter(captured) { ex ->
                ex.response.statusCode = HttpStatus.CONFLICT
                ex.response.headers.contentType = MediaType.APPLICATION_JSON
                ex.response.writeAndFlushWith(
                    Flux.just(Flux.just(buffer("""{"success":false,"error":{"code":44501,"message":"충돌"}}"""))),
                )
            },
        ).verifyComplete()
        loggedJson()["response"]["error"]["message"].asText() shouldBe "충돌"

        appender.list.clear()
        val omitted = filterExchange(MockServerHttpRequest.get("/v2/stream"))
        StepVerifier.create(
            filter().filter(omitted) { ex ->
                ex.response.statusCode = HttpStatus.CONFLICT
                ex.response.headers.contentType = MediaType("audio", "mpeg")
                ex.response.writeAndFlushWith(Flux.just(Flux.just(buffer("raw-audio"))))
            },
        ).verifyComplete()
        loggedJson()["response"]["error"]["message"].asText() shouldBe "unsupported-content-type"
    }

    "체인이 상태 없이 실패하면 500 ERROR 로그를 남기고 원래 예외를 전파한다" {
        val failure = IllegalStateException("boom")
        val exchange = filterExchange(MockServerHttpRequest.get("/v2/courses"))

        StepVerifier.create(filter().filter(exchange) { Mono.error(failure) })
            .expectErrorSatisfies { error -> error shouldBeSameInstanceAs failure }
            .verify()

        appender.list shouldHaveSize 1
        appender.list.single().level shouldBe Level.ERROR
        loggedJson()["http"]["statusCode"].asInt() shouldBe 500
        loggedJson()["logType"].asText() shouldBe "API_ERROR"
    }

    "체인 실패 전에 4xx 상태가 정해졌으면 그 상태로 WARN 로그를 남긴다" {
        val exchange = filterExchange(MockServerHttpRequest.get("/v2/courses"))

        StepVerifier.create(
            filter().filter(exchange) { ex ->
                ex.response.statusCode = HttpStatus.NOT_FOUND
                Mono.error(IllegalArgumentException("not found"))
            },
        ).expectError(IllegalArgumentException::class.java).verify()

        appender.list.single().level shouldBe Level.WARN
        loggedJson()["http"]["statusCode"].asInt() shouldBe 404
    }

    "체인 실패 전에 2xx 상태가 정해졌어도 실패는 500으로 기록한다" {
        val exchange = filterExchange(MockServerHttpRequest.get("/v2/courses"))

        StepVerifier.create(
            filter().filter(exchange) { ex ->
                ex.response.statusCode = HttpStatus.OK
                Mono.error(IllegalStateException("late"))
            },
        ).expectError(IllegalStateException::class.java).verify()

        loggedJson()["http"]["statusCode"].asInt() shouldBe 500
    }

    "응답 상태가 없으면 200으로 기록한다" {
        val exchange = filterExchange(MockServerHttpRequest.get("/v2/courses"))

        StepVerifier.create(filter().filter(exchange) { Mono.empty() }).verifyComplete()

        loggedJson()["http"]["statusCode"].asInt() shouldBe 200
        loggedJson()["level"].asText() shouldBe "INFO"
    }

    "security context 인증 정보로 actor를 기록하고 가장 높은 역할을 고른다" {
        val exchange = filterExchange(MockServerHttpRequest.get("/v2/courses"))
        val authentication = TestingAuthenticationToken("8ee88b63-526d-49dc-9e72-a96be0f81385", null, "ROLE_USER", "ROLE_ORGANIZER")

        StepVerifier.create(
            filter().filter(exchange, respondingChain(HttpStatus.OK, null, null))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)),
        ).verifyComplete()

        val actor = loggedJson()["actor"]
        actor["userId"].asText() shouldBe "8ee88b63-526d-49dc-9e72-a96be0f81385"
        actor["role"].asText() shouldBe "ORGANIZER"
        actor["isAuthenticated"].asBoolean() shouldBe true
    }

    "security context가 없으면 exchange principal을 사용하고 숫자 id는 숫자로 기록한다" {
        val authentication = TestingAuthenticationToken("12345", null, "ROLE_ADMIN", "ROLE_USER")
        val exchange = filterExchange(MockServerHttpRequest.get("/v2/courses"))
            .mutate()
            .principal(Mono.just(authentication))
            .build()

        StepVerifier.create(filter().filter(exchange, respondingChain(HttpStatus.OK, null, null))).verifyComplete()

        val actor = loggedJson()["actor"]
        actor["userId"].isNumber shouldBe true
        actor["userId"].asLong() shouldBe 12345L
        actor["role"].asText() shouldBe "ADMIN"
    }

    "알려진 역할이 없으면 actor role은 null이다" {
        val authentication = TestingAuthenticationToken("svc", null, "SCOPE_read")
        val exchange = filterExchange(MockServerHttpRequest.get("/v2/courses"))

        StepVerifier.create(
            filter().filter(exchange, respondingChain(HttpStatus.OK, null, null))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication)),
        ).verifyComplete()

        loggedJson()["actor"]["role"].isNull shouldBe true
    }
})

private val bufferFactory = DefaultDataBufferFactory()

private fun buffer(text: String): DataBuffer = bufferFactory.wrap(text.toByteArray(StandardCharsets.UTF_8))

private fun filterExchange(request: MockServerHttpRequest.BaseBuilder<*>): MockServerWebExchange =
    filterExchange(request.build())

private fun filterExchange(request: MockServerHttpRequest): MockServerWebExchange =
    MockServerWebExchange.from(request).apply {
        attributes[RequestIdSupport.ATTRIBUTE_NAME] = "req-filter"
    }

private fun respondingChain(status: HttpStatus, contentType: MediaType?, body: String?): WebFilterChain =
    WebFilterChain { ex ->
        ex.request.body.then(
            Mono.defer {
                ex.response.statusCode = status
                contentType?.let { ex.response.headers.contentType = it }
                if (body == null) {
                    ex.response.setComplete()
                } else {
                    ex.response.writeWith(Mono.just(buffer(body)))
                }
            },
        )
    }
