package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.infrastructure.submission.repository.AssignmentSubmissionStatusProjectionRepository
import com.example.aandi_post_web_server.assignment.api.v2.dto.AdminAssignmentSubmissionStatusItemResponse
import com.example.aandi_post_web_server.assignment.api.v2.dto.AdminAssignmentSubmissionStatusesResponse
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.application.service.CourseV1Service
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class AdminAssignmentSubmissionStatusesV2Service(
    private val courseV1Service: CourseV1Service,
    private val projectionRepository: AssignmentSubmissionStatusProjectionRepository,
) {

    fun getSubmissionStatuses(
        courseSlug: String,
        assignmentId: String,
    ): Mono<AdminAssignmentSubmissionStatusesResponse> =
        courseV1Service.getAdminAssignmentDetail(courseSlug, assignmentId)
            .flatMap {
                Mono.zip(
                    courseV1Service.getEnrollments(courseSlug).collectList(),
                    projectionRepository.findAllByAssignmentId(assignmentId).collectList(),
                )
            }
            .map { tuple ->
                val enrollments = tuple.t1
                val projectionsByPublicCode = tuple.t2.associateBy(AssignmentSubmissionStatusProjection::publicCode)
                val items = enrollments.map { enrollment ->
                    val projection = projectionsByPublicCode[enrollment.publicCode]
                    toItemResponse(enrollment, projection)
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
        enrollment: CourseEnrollmentResponse,
        projection: AssignmentSubmissionStatusProjection?,
    ): AdminAssignmentSubmissionStatusItemResponse =
        AdminAssignmentSubmissionStatusItemResponse(
            userId = enrollment.userId,
            publicCode = enrollment.publicCode,
            username = enrollment.username,
            enrollmentStatus = enrollment.status,
            submitted = projection?.submitted == true,
            score = projection?.latestScore,
            passedCases = projection?.latestPassedCases,
            totalCases = projection?.latestTotalCases,
            completedAt = projection?.lastEventTimestamp,
        )
}
