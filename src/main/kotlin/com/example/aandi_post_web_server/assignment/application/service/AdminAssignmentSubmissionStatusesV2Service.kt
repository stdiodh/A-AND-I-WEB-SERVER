package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.api.v2.dto.AdminAssignmentSubmissionStatusItemResponse
import com.example.aandi_post_web_server.assignment.api.v2.dto.AdminAssignmentSubmissionStatusesResponse
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionCourseQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionEnrollment
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionUserQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionUserReference
import com.example.aandi_post_web_server.assignment.infrastructure.submission.repository.AssignmentSubmissionStatusProjectionRepository
import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class AdminAssignmentSubmissionStatusesV2Service(
    private val courseQueryPort: AdminAssignmentSubmissionCourseQueryPort,
    private val projectionRepository: AssignmentSubmissionStatusProjectionRepository,
    private val userQueryPort: AdminAssignmentSubmissionUserQueryPort,
) {

    fun getSubmissionStatuses(
        courseSlug: String,
        assignmentId: String,
    ): Mono<AdminAssignmentSubmissionStatusesResponse> =
        courseQueryPort.ensureAssignmentBelongsToCourse(courseSlug, assignmentId)
            .then(
                Mono.defer {
                    courseQueryPort.findEnrollments(courseSlug).collectList()
                        .flatMap { enrollments ->
                            val userIds = enrollments.map(AdminAssignmentSubmissionEnrollment::userId).distinct()
                            Mono.zip(
                                Mono.just(enrollments),
                                projectionRepository.findAllByAssignmentId(assignmentId).collectList(),
                                userQueryPort.findAllByIds(userIds)
                                    .collectMap(AdminAssignmentSubmissionUserReference::id),
                            )
                        }
                }
            )
            .map { tuple ->
                val enrollments = tuple.t1
                val projectionsByPublicCode = tuple.t2.associateBy(AssignmentSubmissionStatusProjection::publicCode)
                val reportUsersById = tuple.t3
                val items = enrollments.map { enrollment ->
                    val projection = projectionsByPublicCode[enrollment.publicCode]
                    val reportUser = reportUsersById[enrollment.userId]
                    toItemResponse(enrollment, projection, reportUser)
                }
                val submittedCount = items.count { it.submitted }
                AdminAssignmentSubmissionStatusesResponse(
                    assignmentId = assignmentId,
                    courseSlug = courseSlug,
                    totalEnrolled = items.size,
                    submittedCount = submittedCount,
                    notSubmittedCount = items.size - submittedCount,
                    items = items,
                )
            }

    private fun toItemResponse(
        enrollment: AdminAssignmentSubmissionEnrollment,
        projection: AssignmentSubmissionStatusProjection?,
        reportUser: AdminAssignmentSubmissionUserReference?,
    ): AdminAssignmentSubmissionStatusItemResponse =
        AdminAssignmentSubmissionStatusItemResponse(
            userId = enrollment.userId,
            publicCode = enrollment.publicCode,
            username = reportUser?.nickname?.takeIf { it.isNotBlank() } ?: enrollment.username,
            enrollmentStatus = enrollment.status,
            submitted = projection?.submitted == true,
            score = projection?.latestScore,
            passedCases = projection?.latestPassedCases,
            totalCases = projection?.latestTotalCases,
            completedAt = projection?.lastEventTimestamp,
        )
}
