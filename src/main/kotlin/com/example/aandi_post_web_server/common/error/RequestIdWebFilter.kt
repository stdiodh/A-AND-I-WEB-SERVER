package com.example.aandi_post_web_server.common.error

import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

@Component
class RequestIdWebFilter : WebFilter, Ordered {

    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val incomingRequestId = exchange.request.headers.getFirst(RequestIdSupport.HEADER_NAME)?.trim().orEmpty()
        val requestId = if (incomingRequestId.isNotBlank()) incomingRequestId else UUID.randomUUID().toString()

        exchange.attributes[RequestIdSupport.ATTRIBUTE_NAME] = requestId
        exchange.response.headers.set(RequestIdSupport.HEADER_NAME, requestId)

        val mutatedRequest = exchange.request.mutate()
            .header(RequestIdSupport.HEADER_NAME, requestId)
            .build()

        val mutatedExchange = exchange.mutate().request(mutatedRequest).build()

        return chain.filter(mutatedExchange)
            .contextWrite { context -> context.put(RequestIdSupport.ATTRIBUTE_NAME, requestId) }
    }
}
