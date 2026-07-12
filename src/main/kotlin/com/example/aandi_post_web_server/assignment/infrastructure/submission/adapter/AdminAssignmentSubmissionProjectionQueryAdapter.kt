package com.example.aandi_post_web_server.assignment.infrastructure.submission.adapter

import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionProjectionQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionProjectionReference
import com.example.aandi_post_web_server.assignment.infrastructure.submission.repository.AssignmentSubmissionStatusProjectionRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class AdminAssignmentSubmissionProjectionQueryAdapter(
    private val repository: AssignmentSubmissionStatusProjectionRepository,
) : AdminAssignmentSubmissionProjectionQueryPort {
    override fun findAllByAssignmentId(
        assignmentId: String,
    ): Flux<AdminAssignmentSubmissionProjectionReference> =
        repository.findAllByAssignmentId(assignmentId)
            .map { projection ->
                AdminAssignmentSubmissionProjectionReference(
                    publicCode = projection.publicCode,
                    submitted = projection.submitted,
                    score = projection.latestScore,
                    passedCases = projection.latestPassedCases,
                    totalCases = projection.latestTotalCases,
                    completedAt = projection.lastEventTimestamp,
                )
            }
}
