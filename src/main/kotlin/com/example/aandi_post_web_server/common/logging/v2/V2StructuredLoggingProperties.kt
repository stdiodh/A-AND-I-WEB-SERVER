package com.example.aandi_post_web_server.common.logging.v2

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.logging.v2")
data class V2StructuredLoggingProperties(
    val env: String = "local",
    val maxBodyBytes: Int = 8_192,
    val includePathPrefixes: List<String> = listOf("/v2", "/api/v2"),
    val excludePathPrefixes: List<String> = listOf(
        "/actuator",
        "/swagger-ui",
        "/v3/api-docs",
        "/favicon.ico",
        "/static",
        "/assets",
        "/webjars",
    ),
    val service: Service = Service(),
) {
    data class Service(
        val name: String = "report-service",
        val domainCode: Int = 4,
        val version: String = "0.0.1-SNAPSHOT",
        val instanceId: String = "report-service-local",
    )
}
