package com.example.aandi_post_web_server.common.security.v2

import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode
import com.example.aandi_post_web_server.common.error.v2.V2ValidationException
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class V2AuthenticateHeaderBridgeFilterTest : StringSpec({
    val filter = V2AuthenticateHeaderBridgeFilter()

    "non-v2 requests bypass Authenticate header bridging" {
        val chain = RecordingWebFilterChain()
        val exchange = exchange("/v1/courses", authenticate = "Bearer v2-token")

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        chain.calls shouldBe 1
        chain.exchange?.request?.headers?.getFirst(HttpHeaders.AUTHORIZATION) shouldBe null
    }

    "v2 requests without Authenticate header pass through unchanged" {
        val chain = RecordingWebFilterChain()
        val exchange = exchange("/v2/courses")

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        chain.calls shouldBe 1
        chain.exchange?.request?.headers?.getFirst(HttpHeaders.AUTHORIZATION) shouldBe null
    }

    "invalid Authenticate header fails before reaching the chain" {
        val chain = RecordingWebFilterChain()
        val exchange = exchange("/v2/courses", authenticate = "Token v2-token")

        StepVerifier.create(filter.filter(exchange, chain))
            .expectErrorSatisfies { error ->
                val exception = error as V2ValidationException
                exception.errorCode shouldBe V2ErrorCode.HEADER_INVALID
            }
            .verify()

        chain.calls shouldBe 0
    }

    "valid Authenticate header is copied to Authorization when Authorization is absent" {
        val chain = RecordingWebFilterChain()
        val exchange = exchange("/v2/courses", authenticate = "Bearer v2-token")

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        chain.calls shouldBe 1
        chain.exchange?.request?.headers?.getFirst(HttpHeaders.AUTHORIZATION) shouldBe "Bearer v2-token"
    }

    "existing Authorization header is not overwritten by Authenticate" {
        val chain = RecordingWebFilterChain()
        val exchange = exchange(
            path = "/v2/courses",
            authenticate = "Bearer authenticate-token",
            authorization = "Bearer authorization-token",
        )

        StepVerifier.create(filter.filter(exchange, chain))
            .verifyComplete()

        chain.calls shouldBe 1
        chain.exchange?.request?.headers?.getFirst(HttpHeaders.AUTHORIZATION) shouldBe "Bearer authorization-token"
    }
})

private fun exchange(
    path: String,
    authenticate: String? = null,
    authorization: String? = null,
): MockServerWebExchange {
    val request = MockServerHttpRequest.get(path)
    authenticate?.let { request.header(V2HeaderNames.AUTHENTICATE, it) }
    authorization?.let { request.header(HttpHeaders.AUTHORIZATION, it) }
    return MockServerWebExchange.from(request)
}

private class RecordingWebFilterChain : WebFilterChain {
    var calls: Int = 0
        private set
    var exchange: ServerWebExchange? = null
        private set

    override fun filter(exchange: ServerWebExchange): Mono<Void> {
        calls += 1
        this.exchange = exchange
        return Mono.empty()
    }
}
