package com.example.aandi_post_web_server.common.security.v2

import com.example.aandi_post_web_server.common.api.header.V2HeaderContext
import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode
import com.example.aandi_post_web_server.common.error.v2.V2ValidationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpHeaders
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.security.MessageDigest

class V2HeaderValidationFilterTest : StringSpec({
    "non-v2 requests bypass v2 header validation" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/v1/courses",
            authorization = "Bearer v1-token",
        )

        StepVerifier.create(V2HeaderValidationFilter("secret").filter(exchange, chain))
            .verifyComplete()

        chain.calls shouldBe 1
        chain.exchange?.getAttribute<V2HeaderContext>(V2HeaderContext.ATTRIBUTE_NAME).shouldBeNull()
    }

    "v2 requests without a token bypass common header validation" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(path = "/v2/courses")

        StepVerifier.create(V2HeaderValidationFilter("secret").filter(exchange, chain))
            .verifyComplete()

        chain.calls shouldBe 1
        chain.exchange?.getAttribute<V2HeaderContext>(V2HeaderContext.ATTRIBUTE_NAME).shouldBeNull()
    }

    "Authorization bearer token with epoch timestamp stores trimmed header context" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/v2/courses",
            authorization = "  Bearer access-token  ",
            deviceOS = "  IOS  ",
            timestamp = "1770000000000",
            salt = "   ",
        )

        StepVerifier.create(V2HeaderValidationFilter("secret").filter(exchange, chain))
            .verifyComplete()

        val context = chain.exchange?.getAttribute<V2HeaderContext>(V2HeaderContext.ATTRIBUTE_NAME)
        context?.deviceOS shouldBe "IOS"
        context?.authenticate shouldBe "Bearer access-token"
        context?.timestamp shouldBe "1770000000000"
        context?.salt.shouldBeNull()
    }

    "Authenticate bearer token with matching salt stores header context" {
        val timestamp = "2026-04-13T09:00:00Z"
        val secret = "hash-secret"
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/api/v2/courses",
            authenticate = "Bearer authenticate-token",
            deviceOS = "ANDROID",
            timestamp = timestamp,
            salt = md5Hex(timestamp + secret).uppercase(),
        )

        StepVerifier.create(V2HeaderValidationFilter(secret).filter(exchange, chain))
            .verifyComplete()

        val context = chain.exchange?.getAttribute<V2HeaderContext>(V2HeaderContext.ATTRIBUTE_NAME)
        context?.deviceOS shouldBe "ANDROID"
        context?.authenticate shouldBe "Bearer authenticate-token"
        context?.timestamp shouldBe timestamp
        context?.salt shouldBe md5Hex(timestamp + secret).uppercase()
    }

    "blank salt secret does not reject a provided salt" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/v2/courses",
            authorization = "Bearer access-token",
            deviceOS = "IOS",
            timestamp = "2026-04-13T18:00:00+09:00",
            salt = "client-provided-salt",
        )

        StepVerifier.create(V2HeaderValidationFilter("").filter(exchange, chain))
            .verifyComplete()

        chain.calls shouldBe 1
        chain.exchange
            ?.getAttribute<V2HeaderContext>(V2HeaderContext.ATTRIBUTE_NAME)
            ?.salt shouldBe "client-provided-salt"
    }

    "missing deviceOS fails when a token is present" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/v2/courses",
            authorization = "Bearer access-token",
            timestamp = "1770000000000",
        )

        val exception = shouldThrow<V2ValidationException> {
            V2HeaderValidationFilter("secret").filter(exchange, chain)
        }

        exception.errorCode shouldBe V2ErrorCode.HEADER_INVALID
        chain.calls shouldBe 0
    }

    "missing timestamp fails when a token is present" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/v2/courses",
            authorization = "Bearer access-token",
            deviceOS = "IOS",
        )

        val exception = shouldThrow<V2ValidationException> {
            V2HeaderValidationFilter("secret").filter(exchange, chain)
        }

        exception.errorCode shouldBe V2ErrorCode.HEADER_INVALID
        chain.calls shouldBe 0
    }

    "invalid bearer format fails before reaching the chain" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/v2/courses",
            authorization = "Token access-token",
            deviceOS = "IOS",
            timestamp = "1770000000000",
        )

        StepVerifier.create(V2HeaderValidationFilter("secret").filter(exchange, chain))
            .expectErrorSatisfies { error ->
                val exception = error as V2ValidationException
                exception.errorCode shouldBe V2ErrorCode.HEADER_INVALID
            }
            .verify()

        chain.calls shouldBe 0
    }

    "invalid timestamp fails before reaching the chain" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/v2/courses",
            authorization = "Bearer access-token",
            deviceOS = "IOS",
            timestamp = "not-a-timestamp",
        )

        StepVerifier.create(V2HeaderValidationFilter("secret").filter(exchange, chain))
            .expectErrorSatisfies { error ->
                val exception = error as V2ValidationException
                exception.errorCode shouldBe V2ErrorCode.HEADER_INVALID
            }
            .verify()

        chain.calls shouldBe 0
    }

    "salt mismatch fails before reaching the chain" {
        val chain = HeaderValidationRecordingWebFilterChain()
        val exchange = headerExchange(
            path = "/v2/courses",
            authorization = "Bearer access-token",
            deviceOS = "IOS",
            timestamp = "1770000000000",
            salt = "wrong-salt",
        )

        StepVerifier.create(V2HeaderValidationFilter("secret").filter(exchange, chain))
            .expectErrorSatisfies { error ->
                val exception = error as V2ValidationException
                exception.errorCode shouldBe V2ErrorCode.HEADER_INVALID
            }
            .verify()

        chain.calls shouldBe 0
    }
})

private fun headerExchange(
    path: String,
    authenticate: String? = null,
    authorization: String? = null,
    deviceOS: String? = null,
    timestamp: String? = null,
    salt: String? = null,
): MockServerWebExchange {
    val request = MockServerHttpRequest.get(path)
    authenticate?.let { request.header(V2HeaderNames.AUTHENTICATE, it) }
    authorization?.let { request.header(HttpHeaders.AUTHORIZATION, it) }
    deviceOS?.let { request.header(V2HeaderNames.DEVICE_OS, it) }
    timestamp?.let { request.header(V2HeaderNames.TIMESTAMP, it) }
    salt?.let { request.header(V2HeaderNames.SALT, it) }
    return MockServerWebExchange.from(request)
}

private fun md5Hex(input: String): String =
    MessageDigest.getInstance("MD5")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }

private class HeaderValidationRecordingWebFilterChain : WebFilterChain {
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
