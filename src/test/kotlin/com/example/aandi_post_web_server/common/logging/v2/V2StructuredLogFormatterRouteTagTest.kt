package com.example.aandi_post_web_server.common.logging.v2

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerMapping
import java.time.Instant

class V2StructuredLogFormatterRouteTagTest : StringSpec({
    val objectMapper = jacksonObjectMapper()
    val formatter = V2StructuredLogFormatter(
        objectMapper = objectMapper,
        sanitizer = V2StructuredLogSanitizer(objectMapper),
        properties = V2StructuredLoggingProperties(env = "test"),
        environment = MockEnvironment(),
    )

    val emptySnapshot = V2StructuredLogFormatter.BodySnapshot(
        text = null,
        totalBytes = 0,
        capturedBytes = 0,
        truncated = false,
        omittedReason = null,
    )

    fun tagsFor(path: String, routePattern: String?, statusCode: Int): List<String> {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build())
        routePattern?.let { exchange.attributes[HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE] = it }
        exchange.response.statusCode = HttpStatus.valueOf(statusCode)
        val json = formatter.format(
            V2StructuredLogFormatter.LoggingContext(
                exchange = exchange,
                actor = V2StructuredAccessLog.Actor(userId = null, role = null, isAuthenticated = false),
                requestBodySnapshot = emptySnapshot,
                responseBodySnapshot = emptySnapshot,
                statusCode = statusCode,
                latencyMs = 1,
                completedAt = Instant.parse("2026-04-14T11:31:12.418Z"),
            ),
        )
        return objectMapper.readTree(json)["tags"].map { it.asText() }
    }

    "태그는 버전 세그먼트와 경로 변수를 빼고 앞 네 세그먼트와 결과를 담는다" {
        tagsFor(
            path = "/api/v2/admin/courses/back/assignments/a-1",
            routePattern = "/api/v2/admin/courses/{courseSlug}/assignments/{assignmentId}",
            statusCode = 200,
        ) shouldBe listOf("api", "admin", "courses", "assignments", "success")
    }

    "대문자 버전 세그먼트와 닫히지 않은 중괄호 세그먼트는 소문자로 남긴다" {
        tagsFor(
            path = "/V2/Courses/a/b/c",
            routePattern = "/V2/Courses/{}/{slug}/{open",
            statusCode = 404,
        ) shouldBe listOf("v2", "courses", "{open", "fail")
    }

    "중복 태그는 하나로 합치고 라우트 패턴이 없으면 실제 경로를 쓴다" {
        tagsFor(
            path = "/v2/courses/courses",
            routePattern = null,
            statusCode = 500,
        ) shouldBe listOf("courses", "fail")
    }
})
