package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionCourseQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionEnrollment
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionProjectionQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionProjectionReference
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionUserQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionUserReference
import com.example.aandi_post_web_server.assignment.application.submission.model.AdminAssignmentSubmissionStatusItemResult
import com.example.aandi_post_web_server.assignment.application.submission.model.AdminAssignmentSubmissionStatusesResult
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class AdminAssignmentSubmissionStatusesV2Service(
    private val courseQueryPort: AdminAssignmentSubmissionCourseQueryPort,
    private val projectionQueryPort: AdminAssignmentSubmissionProjectionQueryPort,
    private val userQueryPort: AdminAssignmentSubmissionUserQueryPort,
) {

    fun getSubmissionStatuses(
        courseSlug: String,
        assignmentId: String,
    ): Mono<AdminAssignmentSubmissionStatusesResult> =
        courseQueryPort.ensureAssignmentBelongsToCourse(courseSlug, assignmentId)
            .then(
                Mono.defer {
                    courseQueryPort.findEnrollments(courseSlug).collectList()
                        .flatMap { enrollments ->
                            val userIds = enrollments.map(AdminAssignmentSubmissionEnrollment::userId).distinct()
                            Mono.zip(
                                Mono.just(enrollments),
                                projectionQueryPort.findAllByAssignmentId(assignmentId).collectList(),
                                userQueryPort.findAllByIds(userIds)
                                    .collectMap(AdminAssignmentSubmissionUserReference::id),
                            )
                        }
                }
            )
            .map { tuple ->
                val enrollments = tuple.t1
                val projectionsByPublicCode = tuple.t2.associateBy(AdminAssignmentSubmissionProjectionReference::publicCode)
                val reportUsersById = tuple.t3
                val items = enrollments.map { enrollment ->
                    val projection = projectionsByPublicCode[enrollment.publicCode]
                    val reportUser = reportUsersById[enrollment.userId]
                    toItemResult(enrollment, projection, reportUser)
                }
                val submittedCount = items.count { it.submitted }
                AdminAssignmentSubmissionStatusesResult(
                    assignmentId = assignmentId,
                    courseSlug = courseSlug,
                    totalEnrolled = items.size,
                    submittedCount = submittedCount,
                    notSubmittedCount = items.size - submittedCount,
                    items = items,
                )
            }

    private fun toItemResult(
        enrollment: AdminAssignmentSubmissionEnrollment,
        projection: AdminAssignmentSubmissionProjectionReference?,
        reportUser: AdminAssignmentSubmissionUserReference?,
    ): AdminAssignmentSubmissionStatusItemResult =
        AdminAssignmentSubmissionStatusItemResult(
            userId = enrollment.userId,
            publicCode = enrollment.publicCode,
            username = reportUser?.nickname?.takeIf { it.isNotBlank() } ?: enrollment.username,
            enrollmentStatus = enrollment.status,
            submitted = projection?.submitted == true,
            score = projection?.score,
            passedCases = projection?.passedCases,
            totalCases = projection?.totalCases,
            completedAt = projection?.completedAt,
        )
}
