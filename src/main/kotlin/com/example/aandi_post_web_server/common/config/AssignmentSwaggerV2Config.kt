package com.example.aandi_post_web_server.common.config

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AssignmentSwaggerV2Config {
    @Bean
    fun assignmentV2GroupedOpenApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("assignment-v2")
            .pathsToMatch("/v2/assignments/**")
            .addOpenApiCustomizer(ReportSwaggerSupport.assignmentV2InfoCustomizer())
            .addOperationCustomizer(ReportSwaggerSupport.reportV1OperationCustomizer())
            .build()
    }
}
