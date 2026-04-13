package com.example.aandi_post_web_server.assignment.submission.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AssignmentSubmissionStatusProjectionServiceConfig {

    @Bean
    fun assignmentSubmissionStatusProjectionService(
        store: AssignmentSubmissionStatusProjectionStore,
    ): AssignmentSubmissionStatusProjectionService =
        AssignmentSubmissionStatusProjectionService(store, Clock.systemUTC())
}
