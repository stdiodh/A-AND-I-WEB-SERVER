package com.example.aandi_post_web_server.report.v2.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.report.v2")
data class ReportV2SecurityProperties(
    val saltSecret: String = "",
)
