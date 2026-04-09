package com.example.aandi_post_web_server.report.v2.security

import com.example.aandi_post_web_server.report.v2.error.ReportErrorCode
import com.example.aandi_post_web_server.report.v2.error.ReportValidationException
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class AuthenticateHeaderBridgeFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!ReportPathMatcher.isReportV2Path(path)) {
            return chain.filter(exchange)
        }

        val authenticateHeader = exchange.request.headers.getFirst(ReportHeaderNames.AUTHENTICATE)?.trim().orEmpty()
        if (authenticateHeader.isBlank()) {
            return chain.filter(exchange)
        }

        if (!authenticateHeader.startsWith("Bearer ") || authenticateHeader.removePrefix("Bearer ").isBlank()) {
            return Mono.error(
                ReportValidationException(
                    errorCode = ReportErrorCode.HEADER_INVALID,
                    message = "Authenticate 헤더는 'Bearer {accessToken}' 형식이어야 합니다.",
                )
            )
        }

        if (exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION).isNullOrBlank()) {
            val mutatedRequest = exchange.request.mutate()
                .header(HttpHeaders.AUTHORIZATION, authenticateHeader)
                .build()
            return chain.filter(exchange.mutate().request(mutatedRequest).build())
        }

        return chain.filter(exchange)
    }
}
