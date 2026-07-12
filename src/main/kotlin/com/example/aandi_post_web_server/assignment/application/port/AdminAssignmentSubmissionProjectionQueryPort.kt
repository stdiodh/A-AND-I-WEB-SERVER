package com.example.aandi_post_web_server.assignment.application.port

import reactor.core.publisher.Flux
import java.time.Instant

data class AdminAssignmentSubmissionProjectionReference(
    val publicCode: String,
    val submitted: Boolean,
    val score: Int,
    val passedCases: Int,
    val totalCases: Int,
    val completedAt: Instant,
)

interface AdminAssignmentSubmissionProjectionQueryPort {
    fun findAllByAssignmentId(
        assignmentId: String,
    ): Flux<AdminAssignmentSubmissionProjectionReference>
}
