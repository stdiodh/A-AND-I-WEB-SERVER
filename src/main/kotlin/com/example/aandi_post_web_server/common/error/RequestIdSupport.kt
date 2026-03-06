package com.example.aandi_post_web_server.common.error

import org.springframework.web.server.ServerWebExchange
import java.util.UUID

object RequestIdSupport {
    const val HEADER_NAME: String = "X-Request-Id"
    const val ATTRIBUTE_NAME: String = "requestId"

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
}
