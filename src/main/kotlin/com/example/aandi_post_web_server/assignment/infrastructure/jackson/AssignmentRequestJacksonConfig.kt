package com.example.aandi_post_web_server.assignment.infrastructure.jackson

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AssignmentRequestJacksonConfig {

    @Bean
    fun assignmentRequestJacksonCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            builder.modulesToInstall(assignmentMetadataPayloadModule())
        }
}
