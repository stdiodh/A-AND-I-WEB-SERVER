package com.example.aandi_post_web_server.user.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.events.user-sync")
data class UserSyncEventProperties(
    val enabled: Boolean = false,
    val queueUrl: String = "",
    val region: String = "ap-northeast-2",
    val waitTimeSeconds: Int = 20,
    val maxNumberOfMessages: Int = 10,
)
