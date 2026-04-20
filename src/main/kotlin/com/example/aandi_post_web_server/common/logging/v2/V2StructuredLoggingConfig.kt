package com.example.aandi_post_web_server.common.logging.v2

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

@Configuration
@EnableConfigurationProperties(V2StructuredLoggingProperties::class)
class V2StructuredLoggingConfig {

    @Bean
    fun v2StructuredLogSanitizer(objectMapper: ObjectMapper): V2StructuredLogSanitizer =
        V2StructuredLogSanitizer(objectMapper)

    @Bean
    fun v2StructuredLogFormatter(
        objectMapper: ObjectMapper,
        sanitizer: V2StructuredLogSanitizer,
        properties: V2StructuredLoggingProperties,
        environment: Environment,
    ): V2StructuredLogFormatter =
        V2StructuredLogFormatter(
            objectMapper = objectMapper,
            sanitizer = sanitizer,
            properties = properties,
            environment = environment,
        )

    @Bean
    fun v2StructuredLoggingWebFilter(
        formatter: V2StructuredLogFormatter,
        properties: V2StructuredLoggingProperties,
    ): V2StructuredLoggingWebFilter =
        V2StructuredLoggingWebFilter(
            formatter = formatter,
            properties = properties,
        )
}
