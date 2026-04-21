package com.example.aandi_post_web_server.common.security.v2

import com.example.aandi_post_web_server.common.api.header.V2HeaderContext
import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode
import com.example.aandi_post_web_server.common.error.v2.V2ValidationException
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime

class V2HeaderValidationFilter(
    private val saltSecret: String,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!V2PathMatcher.isV2Path(path)) {
            return chain.filter(exchange)
        }

        val headers = exchange.request.headers
        val authenticateHeader = headers.getFirst(V2HeaderNames.AUTHENTICATE)?.trim().orEmpty()
        val authorizationHeader = headers.getFirst(HttpHeaders.AUTHORIZATION)?.trim().orEmpty()
        val tokenHeader = authenticateHeader.ifBlank { authorizationHeader }

        if (tokenHeader.isBlank()) {
            return chain.filter(exchange)
        }

        val deviceOS = requiredHeader(headers.getFirst(V2HeaderNames.DEVICE_OS), V2HeaderNames.DEVICE_OS)
        val timestamp = requiredHeader(headers.getFirst(V2HeaderNames.TIMESTAMP), V2HeaderNames.TIMESTAMP)
        val salt = optionalHeader(headers.getFirst(V2HeaderNames.SALT))

        if (!tokenHeader.startsWith("Bearer ") || tokenHeader.removePrefix("Bearer ").isBlank()) {
            return Mono.error(
                V2ValidationException(
                    errorCode = V2ErrorCode.HEADER_INVALID,
                    message = "Authenticate 헤더는 'Bearer {accessToken}' 형식이어야 합니다.",
                )
            )
        }

        if (!isValidTimestamp(timestamp)) {
            return Mono.error(
                V2ValidationException(
                    errorCode = V2ErrorCode.HEADER_INVALID,
                    message = "timestamp 헤더는 epoch milliseconds 또는 ISO-8601 형식이어야 합니다.",
                )
            )
        }

        if (salt != null && !isValidSalt(timestamp, salt)) {
            return Mono.error(
                V2ValidationException(
                    errorCode = V2ErrorCode.HEADER_INVALID,
                    message = "salt 헤더가 올바르지 않습니다.",
                )
            )
        }

        exchange.attributes[V2HeaderContext.ATTRIBUTE_NAME] = V2HeaderContext(
            deviceOS = deviceOS,
            authenticate = tokenHeader,
            timestamp = timestamp,
            salt = salt,
        )
        return chain.filter(exchange)
    }

    private fun requiredHeader(rawValue: String?, headerName: String): String {
        val value = rawValue?.trim().orEmpty()
        if (value.isBlank()) {
            throw V2ValidationException(
                errorCode = V2ErrorCode.HEADER_INVALID,
                message = "$headerName 헤더가 필요합니다.",
            )
        }
        return value
    }

    private fun optionalHeader(rawValue: String?): String? =
        rawValue?.trim()?.takeIf { it.isNotBlank() }

    private fun isValidTimestamp(value: String): Boolean =
        parseTimestamp(value) != null

    private fun parseTimestamp(value: String): Instant? =
        value.toLongOrNull()
            ?.let { runCatching { Instant.ofEpochMilli(it) }.getOrNull() }
            ?: runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

    private fun isValidSalt(timestamp: String, salt: String): Boolean {
        val secret = saltSecret.takeIf { it.isNotBlank() } ?: return true
        return md5Hex(timestamp + secret).equals(salt, ignoreCase = true)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
