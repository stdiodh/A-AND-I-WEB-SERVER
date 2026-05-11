package com.example.aandi_post_web_server.common.error

import org.springframework.web.server.ServerWebExchange
import java.util.UUID

object RequestIdSupport {
    const val HEADER_NAME: String = "X-Request-Id"
    const val TRACE_HEADER_NAME: String = "X-Trace-Id"
    const val ATTRIBUTE_NAME: String = "requestId"
    const val TRACE_ATTRIBUTE_NAME: String = "traceId"

    fun resolveRequestId(exchange: ServerWebExchange): String {
        val attr = exchange.attributes[ATTRIBUTE_NAME] as? String
        if (!attr.isNullOrBlank()) {
            return attr
        }
        val header = exchange.request.headers.getFirst(HEADER_NAME)?.trim().orEmpty()
        if (header.isNotBlank()) {
            return header
        }
        return UUID.randomUUID().toString()
    }

    fun resolveTraceId(exchange: ServerWebExchange): String {
        val attr = exchange.attributes[TRACE_ATTRIBUTE_NAME] as? String
        if (!attr.isNullOrBlank()) {
            return attr
        }

        val headers = exchange.request.headers
        val traceId = listOf(
            headers.getFirst(TRACE_HEADER_NAME),
            headers.getFirst("traceId"),
            extractTraceIdFromTraceParent(headers.getFirst("traceparent")),
            extractTraceIdFromAmznTraceId(headers.getFirst("X-Amzn-Trace-Id")),
        ).firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            ?: generateTraceId()

        exchange.attributes[TRACE_ATTRIBUTE_NAME] = traceId
        return traceId
    }

    fun generateTraceId(): String =
        UUID.randomUUID().toString().replace("-", "")

    private fun extractTraceIdFromTraceParent(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }

        val parts = value.trim().split('-')
        return parts.getOrNull(1)
            ?.takeIf { it.matches(TRACE_ID_REGEX) }
    }

    private fun extractTraceIdFromAmznTraceId(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }

        val root = value.split(';')
            .firstNotNullOfOrNull { part ->
                part.trim()
                    .removePrefix("Root=")
                    .takeIf { it != part.trim() }
            }
            ?: return null
        val parts = root.split('-')
        if (parts.size < 3) {
            return null
        }
        return (parts[1] + parts[2]).takeIf { it.matches(TRACE_ID_REGEX) }
    }

    private val TRACE_ID_REGEX = Regex("^[0-9a-fA-F]{32}$")
}
