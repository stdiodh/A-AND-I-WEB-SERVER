package com.example.aandi_post_web_server.common.logging.v2

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder(
    value = [
        "@timestamp",
        "level",
        "logType",
        "message",
        "env",
        "service",
        "trace",
        "http",
        "headers",
        "client",
        "actor",
        "request",
        "response",
        "tags",
    ],
)
data class V2StructuredAccessLog(
    @JsonProperty("@timestamp")
    val timestamp: String,
    val level: String,
    val logType: String,
    val message: String,
    val env: String,
    val service: Service,
    val trace: Trace,
    val http: Http,
    val headers: Headers,
    val client: Client,
    val actor: Actor,
    val request: Request,
    val response: Response,
    val tags: List<String>,
) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Service(
        val name: String,
        val domainCode: Int,
        val version: String,
        val instanceId: String,
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Trace(
        val traceId: String,
        val requestId: String,
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Http(
        val method: String,
        val path: String,
        val route: String,
        val statusCode: Int,
        val latencyMs: Long,
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Headers(
        val deviceOS: String?,
        @field:JsonProperty("Authenticate")
        @get:JsonProperty("Authenticate")
        val Authenticate: String?,
        val timestamp: String?,
        val salt: String?,
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Client(
        val ip: String?,
        val userAgent: String?,
        val appVersion: String?,
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Actor(
        val userId: Any?,
        val role: String?,
        val isAuthenticated: Boolean,
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Request(
        val query: Map<String, Any?>,
        val pathVariables: Map<String, Any?>,
        val body: Any?,
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Response(
        val success: Boolean,
        val data: Any?,
        val error: Error?,
        val timestamp: String?,
    )

    @JsonInclude(JsonInclude.Include.ALWAYS)
    data class Error(
        val code: Int?,
        val message: String?,
        val value: String?,
        val alert: String?,
    )
}
