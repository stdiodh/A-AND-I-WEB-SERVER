package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class AdminAssignmentSubmissionEnrollment(
    val userId: String,
    val publicCode: String,
    val username: String,
    val status: EnrollmentStatus,
)

interface AdminAssignmentSubmissionCourseQueryPort {
    fun ensureAssignmentBelongsToCourse(
        courseSlug: String,
        assignmentId: String,
    ): Mono<Void>

    fun findEnrollments(courseSlug: String): Flux<AdminAssignmentSubmissionEnrollment>
}
