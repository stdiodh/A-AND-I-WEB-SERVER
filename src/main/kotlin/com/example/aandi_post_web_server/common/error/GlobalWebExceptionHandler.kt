package com.example.aandi_post_web_server.common.error

import com.example.aandi_post_web_server.common.openapi.ApiEnvelope
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class GlobalWebExceptionHandler(
    private val errorResponseFactory: ErrorResponseFactory,
    private val objectMapper: ObjectMapper,
) : ErrorWebExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalWebExceptionHandler::class.java)

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        if (exchange.response.isCommitted) {
            return Mono.error(ex)
        }

        val result = errorResponseFactory.fromThrowable(exchange, ex)
        val response = exchange.response
        response.statusCode = HttpStatusCode.valueOf(result.status.value())
        response.headers.contentType = MediaType.APPLICATION_JSON
        response.headers.set(RequestIdSupport.HEADER_NAME, RequestIdSupport.resolveRequestId(exchange))

        val payload = serializeBody(result.body)
        logByStatus(exchange, result, ex)
        return response.writeWith(Mono.just(response.bufferFactory().wrap(payload)))
    }

    private fun serializeBody(body: ApiEnvelope<Nothing?>): ByteArray {
        return runCatching { objectMapper.writeValueAsBytes(body) }
            .getOrElse {
                """
                {"success":false,"data":null,"error":{"code":"INTERNAL_ERROR","message":"서버 내부 오류가 발생했습니다."},"timestamp":"${body.timestamp}"}
                """.trimIndent().toByteArray()
            }
    }

    private fun logByStatus(exchange: ServerWebExchange, result: ApiErrorResult, ex: Throwable) {
        val requestId = RequestIdSupport.resolveRequestId(exchange)
        val errorCode = result.body.error?.code ?: ErrorCode.INTERNAL_ERROR.name
        if (result.status.value() >= 500) {
            log.error(
                "[requestId={}] {} {} -> {} {} ({})",
                requestId,
                exchange.request.method,
                exchange.request.path.pathWithinApplication().value(),
                result.status.value(),
                errorCode,
                ex.javaClass.simpleName,
                ex,
            )
            return
        }

        log.warn(
            "[requestId={}] {} {} -> {} {} ({})",
            requestId,
            exchange.request.method,
            exchange.request.path.pathWithinApplication().value(),
            result.status.value(),
            errorCode,
            ex.javaClass.simpleName,
        )
    }
}
