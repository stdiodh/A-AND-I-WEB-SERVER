package com.example.aandi_post_web_server.common.logging.v2

import com.example.aandi_post_web_server.common.security.v2.V2PathMatcher
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebExchangeDecorator
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import kotlin.math.min

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class V2StructuredLoggingWebFilter(
    private val formatter: V2StructuredLogFormatter,
    private val properties: V2StructuredLoggingProperties,
) : WebFilter {

    private val structuredLogger = LoggerFactory.getLogger(STRUCTURED_LOGGER_NAME)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (!V2PathMatcher.isV2Path(path)) {
            return chain.filter(exchange)
        }

        val startedAt = Instant.now()
        val requestCapture = BodyCapture(properties.maxBodyBytes, exchange.request.headers.contentType)
        val responseCapture = BodyCapture(properties.maxBodyBytes, null)

        val decoratedRequest = object : ServerHttpRequestDecorator(exchange.request) {
            override fun getBody(): Flux<DataBuffer> =
                super.getBody().doOnNext { buffer ->
                    runCatching { requestCapture.append(buffer) }
                }
        }

        val decoratedResponse = object : ServerHttpResponseDecorator(exchange.response) {
            override fun writeWith(body: org.reactivestreams.Publisher<out DataBuffer>): Mono<Void> {
                val captureEnabled = shouldCapture(headers.contentType)
                return super.writeWith(
                    Flux.from(body).doOnNext { buffer ->
                        if (captureEnabled) {
                            runCatching { responseCapture.append(buffer) }
                        } else {
                            responseCapture.disable("unsupported-content-type")
                        }
                    }
                )
            }

            override fun writeAndFlushWith(body: org.reactivestreams.Publisher<out org.reactivestreams.Publisher<out DataBuffer>>): Mono<Void> {
                val captureEnabled = shouldCapture(headers.contentType)
                return super.writeAndFlushWith(
                    Flux.from(body).map { publisher ->
                        Flux.from(publisher).doOnNext { buffer ->
                            if (captureEnabled) {
                                runCatching { responseCapture.append(buffer) }
                            } else {
                                responseCapture.disable("unsupported-content-type")
                            }
                        }
                    }
                )
            }
        }

        val decoratedExchange = object : ServerWebExchangeDecorator(exchange) {
            override fun getRequest() = decoratedRequest
            override fun getResponse() = decoratedResponse
        }

        return chain.filter(decoratedExchange)
            .onErrorResume { throwable ->
                finalizeLog(exchange, startedAt, requestCapture, responseCapture)
                    .onErrorResume { Mono.empty() }
                    .then(Mono.error(throwable))
            }
            .then(finalizeLog(exchange, startedAt, requestCapture, responseCapture))
    }

    private fun finalizeLog(
        exchange: ServerWebExchange,
        startedAt: Instant,
        requestCapture: BodyCapture,
        responseCapture: BodyCapture,
    ): Mono<Void> =
        resolveActor(exchange)
            .timeout(Duration.ofSeconds(1))
            .onErrorReturn(V2StructuredAccessLog.Actor(userId = null, role = null, isAuthenticated = false))
            .doOnNext { actor ->
                runCatching {
                    val completedAt = Instant.now()
                    val context = V2StructuredLogFormatter.LoggingContext(
                        exchange = exchange,
                        actor = actor,
                        requestBodySnapshot = requestCapture.snapshot(),
                        responseBodySnapshot = responseCapture.snapshot(),
                        statusCode = exchange.response.statusCode?.value() ?: 200,
                        latencyMs = Duration.between(startedAt, completedAt).toMillis(),
                        completedAt = completedAt,
                    )
                    val json = formatter.format(context)
                    if (context.statusCode >= 400) {
                        structuredLogger.warn(json)
                    } else {
                        structuredLogger.info(json)
                    }
                }
            }
            .onErrorResume { Mono.empty() }
            .then()

    private fun resolveActor(exchange: ServerWebExchange): Mono<V2StructuredAccessLog.Actor> =
        ReactiveSecurityContextHolder.getContext()
            .mapNotNull { it.authentication }
            .switchIfEmpty(exchange.getPrincipal<Authentication>())
            .map { authentication ->
                V2StructuredAccessLog.Actor(
                    userId = authentication.name?.toLongOrNull() ?: authentication.name,
                    role = resolveRole(authentication),
                    isAuthenticated = authentication.isAuthenticated,
                )
            }
            .switchIfEmpty(Mono.just(V2StructuredAccessLog.Actor(userId = null, role = null, isAuthenticated = false)))

    private fun resolveRole(authentication: Authentication): String? {
        val authorityValues = authentication.authorities.mapNotNull { it.authority }
        return ROLE_PRIORITY.firstOrNull { role -> authorityValues.contains("ROLE_$role") }
    }

    private fun shouldCapture(contentType: MediaType?): Boolean {
        if (contentType == null) {
            return true
        }
        if (contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
            return false
        }
        if (contentType.type.equals("image", ignoreCase = true) ||
            contentType.type.equals("audio", ignoreCase = true) ||
            contentType.type.equals("video", ignoreCase = true)
        ) {
            return false
        }
        return contentType.isCompatibleWith(MediaType.APPLICATION_JSON) ||
            contentType.subtype.endsWith("+json") ||
            contentType.type.equals("text", ignoreCase = true) ||
            contentType.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)
    }

    private class BodyCapture(
        private val maxBodyBytes: Int,
        private val initialContentType: MediaType?,
    ) {
        private val buffer = mutableListOf<Byte>()
        private var totalBytes: Int = 0
        private var truncated: Boolean = false
        private var omittedReason: String? = if (isUnsupported(initialContentType)) "unsupported-content-type" else null

        @Synchronized
        fun append(dataBuffer: DataBuffer) {
            if (omittedReason != null) {
                return
            }

            val byteBuffer = dataBuffer.toReadOnlyByteBuffer()
            val bytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(bytes)

            totalBytes += bytes.size
            if (buffer.size >= maxBodyBytes) {
                truncated = true
                return
            }

            val writable = min(maxBodyBytes - buffer.size, bytes.size)
            repeat(writable) { index -> buffer += bytes[index] }
            if (writable < bytes.size) {
                truncated = true
            }
        }

        @Synchronized
        fun disable(reason: String) {
            if (omittedReason == null) {
                omittedReason = reason
                buffer.clear()
                truncated = false
            }
        }

        @Synchronized
        fun snapshot(): V2StructuredLogFormatter.BodySnapshot {
            val bytes = ByteArray(buffer.size)
            buffer.forEachIndexed { index, value -> bytes[index] = value }
            val text = if (bytes.isEmpty()) null else String(bytes, StandardCharsets.UTF_8)
            return V2StructuredLogFormatter.BodySnapshot(
                text = text,
                totalBytes = totalBytes,
                capturedBytes = bytes.size,
                truncated = truncated,
                omittedReason = omittedReason,
            )
        }

        private fun DataBuffer.toReadOnlyByteBuffer(): ByteBuffer =
            asByteBuffer().asReadOnlyBuffer()

        companion object {
            private fun isUnsupported(contentType: MediaType?): Boolean {
                if (contentType == null) {
                    return false
                }
                if (contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA)) {
                    return true
                }
                return contentType.type.equals("image", ignoreCase = true) ||
                    contentType.type.equals("audio", ignoreCase = true) ||
                    contentType.type.equals("video", ignoreCase = true) ||
                    contentType.isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM)
            }
        }
    }

    companion object {
        const val STRUCTURED_LOGGER_NAME: String = "AANDI_V2_STRUCTURED_STDOUT"
        private val ROLE_PRIORITY = listOf("ADMIN", "ORGANIZER", "USER")
    }
}
