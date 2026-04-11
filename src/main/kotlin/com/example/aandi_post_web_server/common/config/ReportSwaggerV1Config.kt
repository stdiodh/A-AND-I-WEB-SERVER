package com.example.aandi_post_web_server.common.config

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportSwaggerV1Config {
    @Bean
    fun reportV1GroupedOpenApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("report-v1")
            .pathsToMatch("/v1/report/**", "/v1/courses/**")
            .addOpenApiCustomizer(ReportSwaggerSupport.reportV1InfoCustomizer())
            .addOperationCustomizer(ReportSwaggerSupport.reportV1OperationCustomizer())
            .build()
    }
}
