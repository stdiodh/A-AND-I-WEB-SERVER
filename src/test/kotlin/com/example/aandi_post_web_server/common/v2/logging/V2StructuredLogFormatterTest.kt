package com.example.aandi_post_web_server.common.logging.v2

import com.example.aandi_post_web_server.common.error.RequestIdSupport
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import java.time.Instant

class V2StructuredLogFormatterTest : StringSpec({
    val objectMapper = jacksonObjectMapper()
    val formatter = V2StructuredLogFormatter(
        objectMapper = objectMapper,
        sanitizer = V2StructuredLogSanitizer(objectMapper),
        properties = V2StructuredLoggingProperties(env = "prod"),
        environment = MockEnvironment(),
    )

    "성공 응답은 API INFO 로그로 포맷한다" {
        val payload = formatter.formatContext(statusCode = 200)

        payload["logType"].asText() shouldBe "API"
        payload["level"].asText() shouldBe "INFO"
        payload["service"]["name"].asText() shouldBe "report-service"
        payload["service"]["domainCode"].asInt() shouldBe 4
        payload["trace"]["traceId"].asText() shouldBe "0123456789abcdef0123456789abcdef"
        payload["trace"]["requestId"].asText() shouldBe "req-test"
        payload["headers"]["Authenticate"].isNull shouldBe true
        payload["headers"]["salt"].isNull shouldBe true
        payload["@timestamp"].asText().endsWith("+09:00") shouldBe true
        payload["response"]["success"].asBoolean() shouldBe true
        payload["response"]["error"].isNull shouldBe true
    }

    "4xx 응답은 API_ERROR WARN 로그로 포맷한다" {
        val payload = formatter.formatContext(statusCode = 400)

        payload["logType"].asText() shouldBe "API_ERROR"
        payload["level"].asText() shouldBe "WARN"
        payload["message"].asText() shouldBe "HTTP request failed: bad request"
        payload["response"]["error"]["code"].asInt() shouldBe 40001
        payload["response"]["error"]["value"].asText() shouldBe "BAD_REQUEST"
    }

    "5xx 응답은 API_ERROR ERROR 로그로 포맷한다" {
        val payload = formatter.formatContext(statusCode = 500)

        payload["logType"].asText() shouldBe "API_ERROR"
        payload["level"].asText() shouldBe "ERROR"
        payload["message"].asText() shouldBe "HTTP request failed: bad request"
    }
})

private fun V2StructuredLogFormatter.formatContext(statusCode: Int): JsonNode {
    val exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/v2/reports?accessToken=query-token")
            .header("deviceOS", "ios")
            .header("timestamp", "2026-04-14T20:31:12.335+09:00")
            .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
            .build()
    )
    exchange.attributes[RequestIdSupport.ATTRIBUTE_NAME] = "req-test"
    exchange.response.statusCode = org.springframework.http.HttpStatus.valueOf(statusCode)

    val responseBody = if (statusCode < 400) {
        """{"success":true,"data":{"email":"hood@example.com"},"error":null,"timestamp":"2026-04-14T20:31:12.418+09:00"}"""
    } else {
        """{"success":false,"data":null,"error":{"code":40001,"message":"bad request","value":"BAD_REQUEST","alert":"bad"},"timestamp":"2026-04-14T20:31:12.418+09:00"}"""
    }

    val json = format(
        V2StructuredLogFormatter.LoggingContext(
            exchange = exchange,
            actor = V2StructuredAccessLog.Actor(userId = 12345, role = "USER", isAuthenticated = true),
            requestBodySnapshot = V2StructuredLogFormatter.BodySnapshot(
                text = """{"password":"raw-password","email":"hood@example.com"}""",
                totalBytes = 51,
                capturedBytes = 51,
                truncated = false,
                omittedReason = null,
            ),
            responseBodySnapshot = V2StructuredLogFormatter.BodySnapshot(
                text = responseBody,
                totalBytes = responseBody.length,
                capturedBytes = responseBody.length,
                truncated = false,
                omittedReason = null,
            ),
            statusCode = statusCode,
            latencyMs = 83,
            completedAt = Instant.parse("2026-04-14T11:31:12.418Z"),
        )
    )

    return jacksonObjectMapper().readTree(json)
}
