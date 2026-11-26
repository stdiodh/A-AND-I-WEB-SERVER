package com.example.aandi_post_web_server.common.logging.v2

import com.example.aandi_post_web_server.common.api.header.V2HeaderContext
import com.example.aandi_post_web_server.common.error.RequestIdSupport
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.HandlerMapping
import java.net.InetSocketAddress
import java.time.Instant

class V2StructuredLogFormatterBranchTest : StringSpec({
    val objectMapper = jacksonObjectMapper()

    fun formatter(
        env: String = "test",
        environment: MockEnvironment = MockEnvironment(),
    ) = V2StructuredLogFormatter(
        objectMapper = objectMapper,
        sanitizer = V2StructuredLogSanitizer(objectMapper),
        properties = V2StructuredLoggingProperties(env = env),
        environment = environment,
    )

    fun V2StructuredLogFormatter.log(
        exchange: MockServerWebExchange = logExchange(MockServerHttpRequest.get("/v2/courses")),
        requestBody: V2StructuredLogFormatter.BodySnapshot = snapshot(null),
        responseBody: V2StructuredLogFormatter.BodySnapshot = snapshot(null),
        statusCode: Int = 200,
    ): JsonNode = objectMapper.readTree(
        format(
            V2StructuredLogFormatter.LoggingContext(
                exchange = exchange,
                actor = V2StructuredAccessLog.Actor(userId = null, role = null, isAuthenticated = false),
                requestBodySnapshot = requestBody,
                responseBodySnapshot = responseBody,
                statusCode = statusCode,
                latencyMs = 12,
                completedAt = COMPLETED_AT,
            ),
        ),
    )

    "v2 header context가 있으면 header 원문 대신 검증된 값을 쓰고 인증 값은 남기지 않는다" {
        val exchange = logExchange(
            MockServerHttpRequest.get("/v2/courses")
                .header("deviceOS", "raw-os")
                .header("timestamp", "raw-timestamp")
                .header("Authenticate", "Bearer secret-token")
                .header("salt", "raw-salt"),
        )
        exchange.attributes[V2HeaderContext.ATTRIBUTE_NAME] = V2HeaderContext(
            deviceOS = "android",
            authenticate = "Bearer secret-token",
            timestamp = "2026-04-15T10:15:30+09:00",
            salt = "salt-value",
        )

        val payload = formatter().log(exchange = exchange)

        payload["headers"]["deviceOS"].asText() shouldBe "android"
        payload["headers"]["timestamp"].asText() shouldBe "2026-04-15T10:15:30+09:00"
        payload["headers"]["Authenticate"].isNull shouldBe true
        payload["headers"]["salt"].isNull shouldBe true
        payload.toString().contains("secret-token") shouldBe false
    }

    "client ip는 X-Forwarded-For 첫 값을 우선하고 appVersion header를 user-agent보다 우선한다" {
        val payload = formatter().log(
            exchange = logExchange(
                MockServerHttpRequest.get("/v2/courses")
                    .header("X-Forwarded-For", " 203.0.113.7 , 10.0.0.1")
                    .header("X-Real-IP", "198.51.100.2")
                    .header("X-App-Version", " 3.1.0 ")
                    .header(HttpHeaders.USER_AGENT, "A&IApp/2.0.1 (iPhone)")
                    .remoteAddress(InetSocketAddress("192.0.2.10", 443)),
            ),
        )

        payload["client"]["ip"].asText() shouldBe "203.0.113.7"
        payload["client"]["appVersion"].asText() shouldBe "3.1.0"
        payload["client"]["userAgent"].asText() shouldBe "A&IApp/2.0.1 (iPhone)"
    }

    "X-Forwarded-For 첫 값이 비어 있으면 X-Real-IP를 사용하고 버전 없는 user-agent는 appVersion이 null이다" {
        val payload = formatter().log(
            exchange = logExchange(
                MockServerHttpRequest.get("/v2/courses")
                    .header("X-Forwarded-For", " , 10.0.0.1")
                    .header("X-Real-IP", " 198.51.100.2 ")
                    .header(HttpHeaders.USER_AGENT, "curl")
                    .remoteAddress(InetSocketAddress("192.0.2.10", 443)),
            ),
        )

        payload["client"]["ip"].asText() shouldBe "198.51.100.2"
        payload["client"]["appVersion"].isNull shouldBe true
    }

    "프록시 header가 없으면 remote address를 사용하고 user-agent가 없으면 appVersion도 null이다" {
        val payload = formatter().log(
            exchange = logExchange(
                MockServerHttpRequest.get("/v2/courses").remoteAddress(InetSocketAddress("192.0.2.10", 443)),
            ),
        )

        payload["client"]["ip"].asText() shouldBe "192.0.2.10"
        payload["client"]["userAgent"].isNull shouldBe true
        payload["client"]["appVersion"].isNull shouldBe true
    }

    "best matching pattern을 route로 쓰고 path variable과 v2 prefix를 tag에서 제외한다" {
        val exchange = logExchange(MockServerHttpRequest.get("/v2/courses/back-basic/assignments/a-1"))
        exchange.attributes[HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE] =
            "/v2/courses/{courseSlug}/assignments/{assignmentId}"

        val payload = formatter().log(exchange = exchange)

        payload["http"]["path"].asText() shouldBe "/v2/courses/back-basic/assignments/a-1"
        payload["http"]["route"].asText() shouldBe "/v2/courses/{courseSlug}/assignments/{assignmentId}"
        payload["tags"].map { it.asText() } shouldBe listOf("courses", "assignments", "success")
    }

    "route tag는 최대 네 segment까지만 소문자로 남기고 실패 tag를 붙인다" {
        val payload = formatter().log(
            exchange = logExchange(MockServerHttpRequest.get("/v2/Admin/Courses/Weeks/Assignments/Copies")),
            statusCode = 404,
        )

        payload["tags"].map { it.asText() } shouldBe listOf("admin", "courses", "weeks", "assignments", "fail")
    }

    "query는 다중 값을 배열로, 단일 값은 문자열로 남기고 민감 키는 마스킹한다" {
        val payload = formatter().log(
            exchange = logExchange(
                MockServerHttpRequest.get("/v2/courses?status=PUBLISHED&tag=a&tag=b&refreshToken=abc&username=hongildong"),
            ),
        )

        val query = payload["request"]["query"]
        query["status"].asText() shouldBe "PUBLISHED"
        query["tag"].map { it.asText() } shouldBe listOf("a", "b")
        query["refreshToken"].asText() shouldBe "****"
        query["username"].asText() shouldBe "hon*******"
    }

    "요청 본문이 생략, 공백, 크기 초과, 파싱 불가인 경우 원문 대신 요약을 남긴다" {
        val omitted = formatter().log(
            requestBody = snapshot("binary", total = 2048, captured = 0, omittedReason = "unsupported-content-type"),
        )["request"]["body"]
        omitted["omitted"].asBoolean() shouldBe true
        omitted["reason"].asText() shouldBe "unsupported-content-type"
        omitted["totalBytes"].asInt() shouldBe 2048

        formatter().log(requestBody = snapshot("   "))["request"]["body"].isNull shouldBe true

        val truncated = formatter().log(
            requestBody = snapshot("""{"password":"secr""", total = 9000, captured = 16, truncated = true),
        )["request"]["body"]
        truncated["reason"].asText() shouldBe "size-limit"
        truncated["truncated"].asBoolean() shouldBe true
        truncated["capturedBytes"].asInt() shouldBe 16

        val unparseable = formatter().log(requestBody = snapshot("{not-json"))["request"]["body"]
        unparseable["reason"].asText() shouldBe "unparseable"
        unparseable.toString().contains("not-json") shouldBe false
    }

    "JSON 요청 본문은 비밀값과 코드, 개인정보를 마스킹한다" {
        val body = formatter().log(
            requestBody = snapshot(
                """{"loginId":"hood1234","password":"pw","sourceCode":"print(1)","phone":"01012345678","nested":{"clientSecret":"s"}}""",
            ),
        )["request"]["body"]

        body["loginId"].asText() shouldBe "hoo*****"
        body["password"].asText() shouldBe "****"
        body["sourceCode"].asText() shouldBe "****"
        body["phone"].asText() shouldBe "010******78"
        body["nested"]["clientSecret"].asText() shouldBe "****"
    }

    "성공 응답이 크기 제한으로 잘리면 data 요약에 size-limit 사유를 남기고 완료 시각을 timestamp로 쓴다" {
        val response = formatter().log(
            responseBody = snapshot("""{"success":true,"data":{"items":[""", total = 50_000, captured = 8192, truncated = true),
        )["response"]

        response["success"].asBoolean() shouldBe true
        response["data"]["reason"].asText() shouldBe "size-limit"
        response["data"]["totalBytes"].asInt() shouldBe 50_000
        response["error"].isNull shouldBe true
        response["timestamp"].asText() shouldBe "2026-04-14T20:31:12.418+09:00"
    }

    "성공 응답 본문이 생략되면 data 요약에 생략 사유를 남긴다" {
        val response = formatter().log(
            responseBody = snapshot("<binary>", total = 10, captured = 8, omittedReason = "unsupported-content-type"),
        )["response"]

        response["data"]["reason"].asText() shouldBe "unsupported-content-type"
    }

    "JSON이 아닌 성공 응답과 data가 null인 성공 응답은 data를 남기지 않는다" {
        formatter().log(responseBody = snapshot("pong"))["response"]["data"].isNull shouldBe true

        val nullData = formatter().log(
            responseBody = snapshot("""{"success":true,"data":null,"error":null}"""),
        )["response"]
        nullData["data"].isNull shouldBe true
        nullData["timestamp"].asText() shouldBe "2026-04-14T20:31:12.418+09:00"
    }

    "실패 응답 본문이 없으면 response body unavailable 오류로 기록한다" {
        val payload = formatter().log(statusCode = 503)

        payload["level"].asText() shouldBe "ERROR"
        payload["message"].asText() shouldBe "HTTP request failed: response body unavailable"
        payload["response"]["success"].asBoolean() shouldBe false
        payload["response"]["error"]["message"].asText() shouldBe "response body unavailable"
        payload["response"]["error"]["code"].isNull shouldBe true
    }

    "생략된 실패 응답은 생략 사유를 오류 메시지로 기록한다" {
        val payload = formatter().log(
            statusCode = 415,
            responseBody = snapshot(null, total = 100, omittedReason = "unsupported-content-type"),
        )

        payload["response"]["error"]["message"].asText() shouldBe "unsupported-content-type"
        payload["message"].asText() shouldBe "HTTP request failed: unsupported-content-type"
    }

    "error 필드가 비어 있는 실패 응답은 기본 실패 메시지를 쓴다" {
        val payload = formatter().log(
            statusCode = 409,
            responseBody = snapshot("""{"success":false,"data":null,"error":{"code":null},"timestamp":null}"""),
        )

        payload["message"].asText() shouldBe "HTTP request failed"
        payload["response"]["error"]["code"].isNull shouldBe true
        payload["response"]["error"]["message"].isNull shouldBe true
        payload["response"]["error"]["value"].isNull shouldBe true
        payload["response"]["timestamp"].asText() shouldBe "2026-04-14T20:31:12.418+09:00"
    }

    "2xx 상태라도 본문 success가 false면 실패 응답 오류를 기록한다" {
        val payload = formatter().log(
            statusCode = 200,
            responseBody = snapshot(
                """{"success":false,"data":{"ignored":true},"error":{"code":40301,"message":"검증 실패","value":"VALIDATE_ERROR","alert":"확인"}}""",
            ),
        )

        payload["logType"].asText() shouldBe "API"
        payload["response"]["success"].asBoolean() shouldBe false
        payload["response"]["data"].isNull shouldBe true
        payload["response"]["error"]["code"].asInt() shouldBe 40301
        payload["response"]["error"]["alert"].asText() shouldBe "확인"
    }

    "env 설정이 비어 있으면 첫 active profile, profile도 없으면 local을 쓴다" {
        val profiled = MockEnvironment().apply { setActiveProfiles("staging", "aws") }

        formatter(env = "", environment = profiled).log()["env"].asText() shouldBe "staging"
        formatter(env = " ", environment = profiled).log()["env"].asText() shouldBe "staging"
        formatter(env = "", environment = MockEnvironment()).log()["env"].asText() shouldBe "local"
    }
})

private val COMPLETED_AT: Instant = Instant.parse("2026-04-14T11:31:12.418Z")

private fun logExchange(request: MockServerHttpRequest.BaseBuilder<*>): MockServerWebExchange =
    MockServerWebExchange.from(request.build()).apply {
        attributes[RequestIdSupport.ATTRIBUTE_NAME] = "req-format"
        attributes[RequestIdSupport.TRACE_ATTRIBUTE_NAME] = "trace-format"
    }

private fun snapshot(
    text: String?,
    total: Int = text?.length ?: 0,
    captured: Int = text?.length ?: 0,
    truncated: Boolean = false,
    omittedReason: String? = null,
): V2StructuredLogFormatter.BodySnapshot =
    V2StructuredLogFormatter.BodySnapshot(
        text = text,
        totalBytes = total,
        capturedBytes = captured,
        truncated = truncated,
        omittedReason = omittedReason,
    )
