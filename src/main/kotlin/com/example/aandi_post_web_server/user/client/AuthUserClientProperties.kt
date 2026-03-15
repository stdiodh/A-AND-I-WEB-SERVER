package com.example.aandi_post_web_server.user.client

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth.user")
data class AuthUserClientProperties(
    val baseUrl: String = "http://localhost:9000",
)
