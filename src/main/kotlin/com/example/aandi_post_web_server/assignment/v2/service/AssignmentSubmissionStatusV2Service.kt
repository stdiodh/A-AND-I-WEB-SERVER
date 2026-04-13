package com.example.aandi_post_web_server.assignment.v2.service

import com.example.aandi_post_web_server.assignment.submission.repository.AssignmentSubmissionStatusProjectionRepository
import com.example.aandi_post_web_server.assignment.v2.dto.AssignmentSubmissionStatusResponse
import com.example.aandi_post_web_server.course.service.CourseQueryService
import com.example.aandi_post_web_server.user.repository.ReportUserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@Service
class AssignmentSubmissionStatusV2Service(
    private val courseQueryService: CourseQueryService,
    private val reportUserRepository: ReportUserRepository,
    private val projectionRepository: AssignmentSubmissionStatusProjectionRepository,
) {

    fun getMySubmissionStatus(
        assignmentId: String,
        userId: String,
    ): Mono<AssignmentSubmissionStatusResponse> =
        courseQueryService.getAssignmentCourse(assignmentId, userId)
            .then(resolvePublicCode(userId))
            .flatMap { publicCode ->
                projectionRepository.findByAssignmentIdAndPublicCode(assignmentId, publicCode)
                    .map { projection ->
                        AssignmentSubmissionStatusResponse(
                            assignmentId = projection.assignmentId,
                            submitted = projection.submitted,
                            firstCompletedAt = projection.firstCompletedAt,
                            lastCompletedAt = projection.lastCompletedAt,
                            latestScore = projection.latestScore,
                            passedCases = projection.latestPassedCases,
                            totalCases = projection.latestTotalCases,
                        )
                    }
                    .switchIfEmpty(
                        Mono.just(
                            AssignmentSubmissionStatusResponse(
                                assignmentId = assignmentId,
                                submitted = false,
                            )
                        )
                    )
            }

    private fun resolvePublicCode(userId: String): Mono<String> =
        reportUserRepository.findById(userId)
            .map { it.publicCode }
            .switchIfEmpty(
                Mono.error(
                    ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "현재 사용자 publicCode projection 을 찾을 수 없습니다: $userId",
                    )
                )
            )
}
