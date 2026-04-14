package com.example.aandi_post_web_server.common.v2.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.report.v2")
data class V2SecurityProperties(
    val saltSecret: String = "",
)
