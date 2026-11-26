package com.example.aandi_post_web_server.common.logging.v2

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class V2StructuredLoggingWebFilterStatusTest : StringSpec({
    val objectMapper = jacksonObjectMapper()
    val logger = LoggerFactory.getLogger(V2StructuredLoggingWebFilter.STRUCTURED_LOGGER_NAME) as Logger
    val appender = ListAppender<ILoggingEvent>()
    val properties = V2StructuredLoggingProperties(env = "test")
    val filter = V2StructuredLoggingWebFilter(
        formatter = V2StructuredLogFormatter(
            objectMapper = objectMapper,
            sanitizer = V2StructuredLogSanitizer(objectMapper),
            properties = properties,
            environment = MockEnvironment(),
        ),
        properties = properties,
    )

    fun chain(status: HttpStatus?, failure: Throwable?): WebFilterChain =
        WebFilterChain { exchange ->
            status?.let { exchange.response.statusCode = it }
            if (failure != null) Mono.error(failure) else Mono.empty()
        }

    fun loggedStatusAndLevel(status: HttpStatus?, failure: Throwable?): Pair<Int, Level> {
        appender.list.clear()
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v2/courses"))
        val result = StepVerifier.create(filter.filter(exchange, chain(status, failure)))
        if (failure == null) {
            result.verifyComplete()
        } else {
            result.verifyErrorMatches { it === failure }
        }
        val event = appender.list.single()
        return objectMapper.readTree(event.formattedMessage)["http"]["statusCode"].asInt() to event.level
    }

    beforeSpec {
        appender.start()
        logger.addAppender(appender)
    }

    afterSpec {
        logger.detachAppender(appender)
        appender.stop()
    }

    "체인이 실패하면 오류 상태가 정해지지 않은 응답을 500으로 기록한다" {
        val failure = IllegalStateException("boom")
        listOf(null, HttpStatus.OK, HttpStatus.FOUND).forEach { status ->
            withClue(status) { loggedStatusAndLevel(status, failure) shouldBe (500 to Level.ERROR) }
        }
    }

    "체인이 실패해도 이미 정해진 오류 상태는 그대로 기록한다" {
        val failure = IllegalStateException("boom")
        loggedStatusAndLevel(HttpStatus.BAD_REQUEST, failure) shouldBe (400 to Level.WARN)
        loggedStatusAndLevel(HttpStatus.SERVICE_UNAVAILABLE, failure) shouldBe (503 to Level.ERROR)
    }

    "체인이 성공하면 응답 상태를 기록하고 상태가 없으면 200으로 기록한다" {
        loggedStatusAndLevel(null, null) shouldBe (200 to Level.INFO)
        loggedStatusAndLevel(HttpStatus.CREATED, null) shouldBe (201 to Level.INFO)
        loggedStatusAndLevel(HttpStatus.NOT_FOUND, null) shouldBe (404 to Level.WARN)
    }
})
