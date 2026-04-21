package com.example.aandi_post_web_server.common.security.v2

import com.example.aandi_post_web_server.common.error.v2.V2ErrorCode
import com.example.aandi_post_web_server.common.error.v2.V2ValidationException
import org.springframework.http.HttpHeaders
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

class V2AuthenticateHeaderBridgeFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!V2PathMatcher.isV2Path(path)) {
            return chain.filter(exchange)
        }

        val authenticateHeader = exchange.request.headers.getFirst(V2HeaderNames.AUTHENTICATE)?.trim().orEmpty()
        if (authenticateHeader.isBlank()) {
            return chain.filter(exchange)
        }

        if (!authenticateHeader.startsWith("Bearer ") || authenticateHeader.removePrefix("Bearer ").isBlank()) {
            return Mono.error(
                V2ValidationException(
                    errorCode = V2ErrorCode.HEADER_INVALID,
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
