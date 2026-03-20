package com.example.aandi_post_web_server.assignment.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsAsyncClient

@Configuration
@EnableConfigurationProperties(AssignmentReportTestCaseEventProperties::class)
class AssignmentReportTestCaseEventConfig {

    @Bean
    fun assignmentReportTestCaseEventPublisher(
        properties: AssignmentReportTestCaseEventProperties,
        objectMapper: ObjectMapper,
    ): AssignmentReportTestCaseEventPublisher {
        if (!properties.enabled) {
            return NoopAssignmentReportTestCaseEventPublisher()
        }
        require(properties.topicArn.isNotBlank()) {
            "app.events.report-test-case.topic-arn must not be blank when enabled=true " +
                "(APP_EVENTS_REPORT_TEST_CASE_SNS_TOPIC_ARN)"
        }
        val snsAsyncClient = SnsAsyncClient.builder()
            .region(Region.of(properties.region))
            .build()
        return SnsAssignmentReportTestCaseEventPublisher(
            snsAsyncClient = snsAsyncClient,
            objectMapper = objectMapper,
            topicArn = properties.topicArn,
        )
    }
}
