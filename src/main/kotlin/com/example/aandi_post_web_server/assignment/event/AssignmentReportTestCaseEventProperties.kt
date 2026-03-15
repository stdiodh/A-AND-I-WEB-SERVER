package com.example.aandi_post_web_server.assignment.event

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.events.report-test-case")
data class AssignmentReportTestCaseEventProperties(
    val enabled: Boolean = false,
    val topicArn: String = "",
    val region: String = "ap-northeast-2",
)
