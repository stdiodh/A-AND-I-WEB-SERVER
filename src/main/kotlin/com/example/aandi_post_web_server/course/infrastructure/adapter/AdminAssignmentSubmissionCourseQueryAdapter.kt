package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionCourseQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionEnrollment
import com.example.aandi_post_web_server.course.application.service.CourseQueryService
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class AdminAssignmentSubmissionCourseQueryAdapter(
    private val courseQueryService: CourseQueryService,
) : AdminAssignmentSubmissionCourseQueryPort {
    override fun ensureAssignmentBelongsToCourse(
        courseSlug: String,
        assignmentId: String,
    ): Mono<Void> =
        courseQueryService.getAdminAssignmentDetail(courseSlug, assignmentId).then()

    override fun findEnrollments(courseSlug: String): Flux<AdminAssignmentSubmissionEnrollment> =
        courseQueryService.getEnrollments(courseSlug)
            .map { enrollment ->
                AdminAssignmentSubmissionEnrollment(
                    userId = enrollment.userId,
                    publicCode = enrollment.publicCode,
                    username = enrollment.username,
                    status = enrollment.status,
                )
            }
}
