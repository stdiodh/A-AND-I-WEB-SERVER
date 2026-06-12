package com.example.aandi_post_web_server.common.config

import com.example.aandi_post_web_server.assignment.application.activation.AssignmentActivationGateFilter
import com.example.aandi_post_web_server.assignment.application.activation.AssignmentActivationService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.server.WebFilter

@Configuration
class AssignmentActivationGateFilterConfig {

    @Bean(name = ["assignmentActivationGateFilter"])
    fun assignmentActivationGateFilter(
        activationService: AssignmentActivationService,
    ): WebFilter = AssignmentActivationGateFilter(activationService)
}
