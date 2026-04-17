package com.example.aandi_post_web_server.assignment.submission.event

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@Configuration
@ConditionalOnProperty(prefix = "app.events.judge-submission", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(JudgeSubmissionEventProperties::class)
class JudgeSubmissionEventConfig {

    @Bean("judgeSubmissionSqsAsyncClient")
    fun judgeSubmissionSqsAsyncClient(properties: JudgeSubmissionEventProperties): SqsAsyncClient {
        val builder = SqsAsyncClient.builder()
        if (properties.region.isNotBlank()) {
            builder.region(Region.of(properties.region))
        }
        return builder.build()
    }
}
