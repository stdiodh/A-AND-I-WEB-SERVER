package com.example.aandi_post_web_server.assignment.submission.event

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.events.judge-submission")
data class JudgeSubmissionEventProperties(
    val enabled: Boolean = false,
    val queueUrl: String = "",
    val region: String = "ap-northeast-2",
    val waitTimeSeconds: Int = 20,
    val maxNumberOfMessages: Int = 10,
    val visibilityTimeoutSeconds: Int = 60,
    val pollDelay: Duration = Duration.ofSeconds(1),
)
