package com.example.aandi_post_web_server.assignment.submission.event

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@Configuration
@EnableConfigurationProperties(JudgeSubmissionEventProperties::class)
class JudgeSubmissionEventConfig {

    @Bean("judgeSubmissionSqsAsyncClient")
    fun judgeSubmissionSqsAsyncClient(properties: JudgeSubmissionEventProperties): SqsAsyncClient =
        SqsAsyncClient.builder()
            .region(Region.of(properties.region))
            .build()
}
