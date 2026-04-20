package com.example.aandi_post_web_server.common.logging.v2

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.logging.v2")
data class V2StructuredLoggingProperties(
    val env: String = "local",
    val maxBodyBytes: Int = 16_384,
    val service: Service = Service(),
) {
    data class Service(
        val name: String = "aandi_post_web_server",
        val domainCode: Int = 0,
        val version: String = "0.0.1-SNAPSHOT",
        val instanceId: String = "local-instance",
    )
}
