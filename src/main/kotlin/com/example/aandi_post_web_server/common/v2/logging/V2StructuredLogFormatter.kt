package com.example.aandi_post_web_server.common.v2.logging

import com.example.aandi_post_web_server.common.error.RequestIdSupport
import com.example.aandi_post_web_server.common.v2.api.V2HeaderContext
import com.example.aandi_post_web_server.common.v2.security.V2HeaderNames
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebExchange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class V2StructuredLogFormatter(
    private val objectMapper: ObjectMapper,
    private val sanitizer: V2StructuredLogSanitizer,
    private val properties: V2StructuredLoggingProperties,
    private val environment: Environment,
) {

    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun format(context: LoggingContext): String {
        val payload = buildPayload(context)
        return objectMapper.writeValueAsString(payload)
    }

    private fun buildPayload(context: LoggingContext): V2StructuredAccessLog {
        val exchange = context.exchange
        val completedAt = formatTimestamp(context.completedAt)
        val path = exchange.request.path.pathWithinApplication().value()
        val route = resolveRoute(exchange, path)
        val requestEnvelope = decodeRequestBody(context.requestBodySnapshot)
        val responseEnvelope = decodeResponseBody(
            snapshot = context.responseBodySnapshot,
            statusCode = context.statusCode,
            completedAt = completedAt,
        )
        val isFailure = context.statusCode >= 400 || !responseEnvelope.success
        val traceId = resolveTraceId(exchange)
        val requestId = RequestIdSupport.resolveRequestId(exchange)

        return V2StructuredAccessLog(
            timestamp = completedAt,
            level = if (isFailure) "WARN" else "INFO",
            logType = if (isFailure) "API_ERROR" else "API",
            message = buildMessage(isFailure, responseEnvelope.error),
            env = resolveEnvironmentName(),
            service = V2StructuredAccessLog.Service(
                name = properties.service.name,
                domainCode = properties.service.domainCode,
                version = properties.service.version,
                instanceId = properties.service.instanceId,
            ),
            trace = V2StructuredAccessLog.Trace(
                traceId = traceId,
                requestId = requestId,
            ),
            http = V2StructuredAccessLog.Http(
                method = exchange.request.method.name(),
                path = path,
                route = route,
                statusCode = context.statusCode,
                latencyMs = context.latencyMs,
            ),
            headers = buildHeaders(exchange),
            client = buildClient(exchange),
            actor = context.actor,
            request = V2StructuredAccessLog.Request(
                query = resolveQuery(exchange),
                pathVariables = resolvePathVariables(exchange),
                body = requestEnvelope,
            ),
            response = responseEnvelope,
            tags = buildTags(route, isFailure),
        )
    }

    private fun buildHeaders(exchange: ServerWebExchange): V2StructuredAccessLog.Headers {
        val headerContext = exchange.getAttribute<V2HeaderContext>(V2HeaderContext.ATTRIBUTE_NAME)
        val headers = exchange.request.headers
        return V2StructuredAccessLog.Headers(
            deviceOS = headerContext?.deviceOS ?: headers.getFirst(V2HeaderNames.DEVICE_OS),
            Authenticate = null,
            timestamp = headerContext?.timestamp ?: headers.getFirst(V2HeaderNames.TIMESTAMP),
            salt = null,
        )
    }

    private fun buildClient(exchange: ServerWebExchange): V2StructuredAccessLog.Client {
        val headers = exchange.request.headers
        val userAgent = headers.getFirst(HttpHeaders.USER_AGENT)
        return V2StructuredAccessLog.Client(
            ip = resolveClientIp(headers, exchange),
            userAgent = userAgent,
            appVersion = resolveAppVersion(headers, userAgent),
        )
    }

    private fun resolveClientIp(headers: HttpHeaders, exchange: ServerWebExchange): String? {
        val forwarded = headers.getFirst("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (forwarded != null) {
            return forwarded
        }

        val realIp = headers.getFirst("X-Real-IP")?.trim()
        if (!realIp.isNullOrBlank()) {
            return realIp
        }

        return exchange.request.remoteAddress?.address?.hostAddress
    }

    private fun resolveAppVersion(headers: HttpHeaders, userAgent: String?): String? {
        val headerCandidates = listOf("appVersion", "App-Version", "X-App-Version")
        headerCandidates.forEach { headerName ->
            val value = headers.getFirst(headerName)?.trim()
            if (!value.isNullOrBlank()) {
                return value
            }
        }

        val ua = userAgent ?: return null
        val match = USER_AGENT_VERSION_REGEX.find(ua) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun resolveTraceId(exchange: ServerWebExchange): String {
        val cached = exchange.getAttribute<String>(TRACE_ID_ATTRIBUTE)?.takeIf { it.isNotBlank() }
        if (cached != null) {
            return cached
        }

        val headers = exchange.request.headers
        val candidates = listOf(
            headers.getFirst("X-Trace-Id"),
            headers.getFirst("traceId"),
            headers.getFirst("X-Amzn-Trace-Id"),
            extractTraceIdFromTraceParent(headers.getFirst("traceparent")),
        )
        val traceId = candidates.firstOrNull { !it.isNullOrBlank() }?.trim()
            ?: UUID.randomUUID().toString().replace("-", "")

        exchange.attributes[TRACE_ID_ATTRIBUTE] = traceId
        return traceId
    }

    private fun extractTraceIdFromTraceParent(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }

        val parts = value.split('-')
        return parts.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    private fun resolveRoute(exchange: ServerWebExchange, fallbackPath: String): String {
        val bestMatchingPattern = exchange.getAttribute<Any>(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
            ?.toString()
            ?.trim()
        return bestMatchingPattern?.takeIf { it.isNotBlank() } ?: fallbackPath
    }

    private fun resolveQuery(exchange: ServerWebExchange): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        exchange.request.queryParams.forEach { (key, values) ->
            result[key] = when {
                values.isEmpty() -> null
                values.size == 1 -> values.first()
                else -> values.toList()
            }
        }
        return result
    }

    private fun resolvePathVariables(exchange: ServerWebExchange): Map<String, Any?> {
        val variables = exchange.getAttribute<Map<String, String>>(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE).orEmpty()
        return linkedMapOf<String, Any?>().apply {
            variables.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun decodeRequestBody(snapshot: BodySnapshot): Any? {
        if (snapshot.omittedReason != null) {
            return bodySummary(snapshot)
        }
        if (snapshot.text.isNullOrBlank()) {
            return null
        }
        if (snapshot.truncated) {
            return bodySummary(snapshot.copy(omittedReason = snapshot.omittedReason ?: "size-limit"))
        }

        return parseJson(snapshot.text)?.let(sanitizer::sanitize)
            ?: bodySummary(snapshot.copy(omittedReason = "unparseable"))
    }

    private fun decodeResponseBody(
        snapshot: BodySnapshot,
        statusCode: Int,
        completedAt: String,
    ): V2StructuredAccessLog.Response {
        val parsedRoot = if (!snapshot.text.isNullOrBlank() && !snapshot.truncated && snapshot.omittedReason == null) {
            parseJson(snapshot.text)
        } else {
            null
        }

        val success = parsedRoot?.path("success")?.asBoolean(statusCode < 400) ?: (statusCode < 400)
        val data = if (success) {
            parsedRoot?.get("data")?.let(sanitizer::sanitize)
                ?: if (snapshot.text != null && (snapshot.truncated || snapshot.omittedReason != null)) bodySummary(snapshot) else null
        } else {
            null
        }
        val error = if (success) {
            null
        } else {
            parsedRoot?.get("error")?.let(::toError)
                ?: V2StructuredAccessLog.Error(
                    code = null,
                    message = snapshot.omittedReason ?: "response body unavailable",
                    value = null,
                    alert = null,
                )
        }
        val timestamp = parsedRoot?.path("timestamp")?.takeIf { !it.isMissingNode && !it.isNull }?.asText() ?: completedAt

        return V2StructuredAccessLog.Response(
            success = success,
            data = data,
            error = error,
            timestamp = timestamp,
        )
    }

    private fun toError(node: JsonNode): V2StructuredAccessLog.Error =
        V2StructuredAccessLog.Error(
            code = node.path("code").takeIf { !it.isMissingNode && !it.isNull }?.asInt(),
            message = node.path("message").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            value = node.path("value").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            alert = node.path("alert").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
        )

    private fun parseJson(raw: String): JsonNode? =
        runCatching { objectMapper.readTree(raw) }.getOrNull()

    private fun bodySummary(snapshot: BodySnapshot): Map<String, Any?> =
        linkedMapOf(
            "omitted" to true,
            "reason" to snapshot.omittedReason,
            "capturedBytes" to snapshot.capturedBytes,
            "totalBytes" to snapshot.totalBytes,
            "truncated" to snapshot.truncated,
        )

    private fun buildMessage(isFailure: Boolean, error: V2StructuredAccessLog.Error?): String =
        if (!isFailure) {
            "HTTP request completed"
        } else {
            error?.message?.takeIf { it.isNotBlank() }?.let { "HTTP request failed: $it" } ?: "HTTP request failed"
        }

    private fun buildTags(route: String, isFailure: Boolean): List<String> {
        val segments = route.split('/')
            .filter { it.isNotBlank() }
            .filterNot { it == "v2" || it.startsWith("{") && it.endsWith("}") }
            .map { it.lowercase() }
            .take(4)
            .toMutableList()
        segments += if (isFailure) "fail" else "success"
        return segments.distinct()
    }

    private fun formatTimestamp(instant: Instant): String =
        instant.atZone(seoulZone).format(timestampFormatter)

    private fun resolveEnvironmentName(): String =
        environment.activeProfiles.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: properties.env

    data class LoggingContext(
        val exchange: ServerWebExchange,
        val actor: V2StructuredAccessLog.Actor,
        val requestBodySnapshot: BodySnapshot,
        val responseBodySnapshot: BodySnapshot,
        val statusCode: Int,
        val latencyMs: Long,
        val completedAt: Instant,
    )

    data class BodySnapshot(
        val text: String?,
        val totalBytes: Int,
        val capturedBytes: Int,
        val truncated: Boolean,
        val omittedReason: String?,
    )

    companion object {
        private const val TRACE_ID_ATTRIBUTE: String = "aandi.v2.logging.traceId"
        private val USER_AGENT_VERSION_REGEX = Regex("""/(\d+(?:\.\d+)+)""")
    }
}
