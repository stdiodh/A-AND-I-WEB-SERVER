package com.example.aandi_post_web_server.common.error

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import java.util.UUID

class RequestIdSupportTest : StringSpec({
    "request id는 exchange attribute를 header보다 우선한다" {
        val exchange = exchange("X-Request-Id" to "req-header")
        exchange.attributes[RequestIdSupport.ATTRIBUTE_NAME] = "req-attribute"

        RequestIdSupport.resolveRequestId(exchange) shouldBe "req-attribute"
    }

    "blank attribute는 무시하고 header 값을 trim 해 사용한다" {
        val exchange = exchange("X-Request-Id" to "  req-header  ")
        exchange.attributes[RequestIdSupport.ATTRIBUTE_NAME] = " "

        RequestIdSupport.resolveRequestId(exchange) shouldBe "req-header"
    }

    "request id 후보가 모두 비어 있으면 UUID를 새로 만든다" {
        val generated = RequestIdSupport.resolveRequestId(exchange("X-Request-Id" to "   "))

        UUID.fromString(generated).toString() shouldBe generated
    }

    "trace id는 저장된 attribute를 우선 사용한다" {
        val exchange = exchange("X-Trace-Id" to "header-trace")
        exchange.attributes[RequestIdSupport.TRACE_ATTRIBUTE_NAME] = "attribute-trace"

        RequestIdSupport.resolveTraceId(exchange) shouldBe "attribute-trace"
    }

    "X-Trace-Id가 traceId, traceparent보다 우선하고 결과를 attribute에 저장한다" {
        val exchange = exchange(
            "X-Trace-Id" to " explicit-trace ",
            "traceId" to "legacy-trace",
            "traceparent" to "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01",
        )

        RequestIdSupport.resolveTraceId(exchange) shouldBe "explicit-trace"
        exchange.attributes[RequestIdSupport.TRACE_ATTRIBUTE_NAME] shouldBe "explicit-trace"
    }

    "X-Trace-Id가 없으면 traceId header를 사용한다" {
        RequestIdSupport.resolveTraceId(exchange("traceId" to "legacy-trace")) shouldBe "legacy-trace"
    }

    "W3C traceparent의 trace-id 구간을 추출한다" {
        val exchange = exchange("traceparent" to "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")

        RequestIdSupport.resolveTraceId(exchange) shouldBe "4bf92f3577b34da6a3ce929d0e0e4736"
    }

    "형식이 잘못된 traceparent는 무시하고 AWS X-Amzn-Trace-Id Root를 사용한다" {
        val exchange = exchange(
            "traceparent" to "00-not-hex-01",
            "X-Amzn-Trace-Id" to "Self=1-67891234-abcdef012345678912345678;Root=1-5759e988-bd862e3fe1be46a994272793;Sampled=1",
        )

        RequestIdSupport.resolveTraceId(exchange) shouldBe "5759e988bd862e3fe1be46a994272793"
    }

    "Root가 없거나 구간이 부족한 X-Amzn-Trace-Id는 무시하고 새 trace id를 만든다" {
        listOf(
            "Self=1-5759e988-bd862e3fe1be46a994272793",
            "Root=1-5759e988",
            "Root=1-zzzz-bd862e3fe1be46a994272793",
        ).forEach { amznTraceId ->
            val exchange = exchange("X-Amzn-Trace-Id" to amznTraceId)

            val traceId = RequestIdSupport.resolveTraceId(exchange)

            traceId shouldMatch Regex("^[0-9a-f]{32}$")
            exchange.attributes[RequestIdSupport.TRACE_ATTRIBUTE_NAME] shouldBe traceId
        }
    }

    "trace id 후보가 없으면 하이픈 없는 32자리 id를 만들고 이후 호출에서 재사용한다" {
        val exchange = exchange()

        val first = RequestIdSupport.resolveTraceId(exchange)
        val second = RequestIdSupport.resolveTraceId(exchange)

        first shouldMatch Regex("^[0-9a-f]{32}$")
        second shouldBe first
        RequestIdSupport.generateTraceId() shouldNotBe first
    }
})

private fun exchange(vararg headers: Pair<String, String>): MockServerWebExchange {
    val request = MockServerHttpRequest.get("/v2/courses")
    headers.forEach { (name, value) -> request.header(name, value) }
    return MockServerWebExchange.from(request.build())
}
