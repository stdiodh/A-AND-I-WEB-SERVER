package com.example.aandi_post_web_server.report.v2.security

import com.example.aandi_post_web_server.report.v2.api.ReportHeaderContext
import com.example.aandi_post_web_server.report.v2.error.ReportErrorCode
import com.example.aandi_post_web_server.report.v2.error.ReportValidationException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime

class ReportHeaderValidationFilter(
    private val saltSecret: String,
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!ReportPathMatcher.isReportV2Path(path)) {
            return chain.filter(exchange)
        }

        val headers = exchange.request.headers
        val authenticateHeader = headers.getFirst(ReportHeaderNames.AUTHENTICATE)?.trim().orEmpty()
        val authorizationHeader = headers.getFirst("Authorization")?.trim().orEmpty()
        if (authenticateHeader.isBlank() && authorizationHeader.isBlank()) {
            return chain.filter(exchange)
        }

        val deviceOS = requiredHeader(headers.getFirst(ReportHeaderNames.DEVICE_OS), ReportHeaderNames.DEVICE_OS)
        val authenticate = requiredHeader(headers.getFirst(ReportHeaderNames.AUTHENTICATE), ReportHeaderNames.AUTHENTICATE)
        val timestamp = requiredHeader(headers.getFirst(ReportHeaderNames.TIMESTAMP), ReportHeaderNames.TIMESTAMP)
        val salt = optionalHeader(headers.getFirst(ReportHeaderNames.SALT))

        if (!authenticate.startsWith("Bearer ") || authenticate.removePrefix("Bearer ").isBlank()) {
            return Mono.error(
                ReportValidationException(
                    errorCode = ReportErrorCode.HEADER_INVALID,
                    message = "Authenticate 헤더는 'Bearer {accessToken}' 형식이어야 합니다.",
                )
            )
        }

        if (!isValidTimestamp(timestamp)) {
            return Mono.error(
                ReportValidationException(
                    errorCode = ReportErrorCode.HEADER_INVALID,
                    message = "timestamp 헤더는 ISO-8601 형식이어야 합니다.",
                )
            )
        }

        if (salt != null && !isValidSalt(timestamp, salt)) {
            return Mono.error(
                ReportValidationException(
                    errorCode = ReportErrorCode.HEADER_INVALID,
                    message = "salt 헤더가 올바르지 않습니다.",
                )
            )
        }

        exchange.attributes[ReportHeaderContext.ATTRIBUTE_NAME] = ReportHeaderContext(
            deviceOS = deviceOS,
            authenticate = authenticate,
            timestamp = timestamp,
            salt = salt,
        )
        return chain.filter(exchange)
    }

    private fun requiredHeader(rawValue: String?, headerName: String): String {
        val value = rawValue?.trim().orEmpty()
        if (value.isBlank()) {
            throw ReportValidationException(
                errorCode = ReportErrorCode.HEADER_INVALID,
                message = "$headerName 헤더가 필요합니다.",
            )
        }
        return value
    }

    private fun optionalHeader(rawValue: String?): String? =
        rawValue?.trim()?.takeIf { it.isNotBlank() }

    private fun isValidTimestamp(value: String): Boolean =
        runCatching { Instant.parse(value) }.isSuccess || runCatching { OffsetDateTime.parse(value) }.isSuccess

    private fun isValidSalt(timestamp: String, salt: String): Boolean {
        val secret = saltSecret.takeIf { it.isNotBlank() } ?: return true
        return md5Hex(timestamp + secret).equals(salt, ignoreCase = true)
    }

    private fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
