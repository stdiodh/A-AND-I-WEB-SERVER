package com.example.aandi_post_web_server.user.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@Configuration
@EnableConfigurationProperties(UserSyncEventProperties::class)
class UserSyncEventConfig {

    @Bean
    fun sqsAsyncClient(properties: UserSyncEventProperties): SqsAsyncClient =
        SqsAsyncClient.builder()
            .region(Region.of(properties.region))
            .build()
}
