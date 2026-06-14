package com.example.aandi_post_web_server.assignment.application.activation

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.server.WebFilter

/**
 * Provides a permissive activation gate filter for WebFlux slice tests.
 *
 * Without it, SecurityConfig fails to wire because it requires a bean
 * named `assignmentActivationGateFilter`. Tests that want to assert
 * deactivation behaviour can override this bean or import the real config.
 */
@TestConfiguration
class TestAssignmentActivationConfig {

    @Bean(name = ["assignmentActivationGateFilter"])
    fun assignmentActivationGateFilter(): WebFilter =
        WebFilter { exchange, chain -> chain.filter(exchange) }
}
