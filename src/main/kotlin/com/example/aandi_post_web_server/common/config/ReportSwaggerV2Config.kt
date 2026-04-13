package com.example.aandi_post_web_server.common.config

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportSwaggerV2Config {
    @Bean
    fun reportV2GroupedOpenApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("report-v2")
            .pathsToMatch("/v2/**")
            .addOpenApiCustomizer(ReportSwaggerSupport.reportV2InfoCustomizer())
            .addOperationCustomizer(ReportSwaggerSupport.reportV2OperationCustomizer())
            .build()
    }
}
