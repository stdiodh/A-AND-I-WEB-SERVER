package com.example.aandi_post_web_server.assignment.submission.service

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.submission.repository.AssignmentSubmissionStatusProjectionRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class RepositoryAssignmentSubmissionStatusProjectionStore(
    private val repository: AssignmentSubmissionStatusProjectionRepository,
) : AssignmentSubmissionStatusProjectionStore {
    override fun findByAssignmentIdAndPublicCode(
        assignmentId: String,
        publicCode: String,
    ): Mono<AssignmentSubmissionStatusProjection> =
        repository.findByAssignmentIdAndPublicCode(assignmentId, publicCode)

    override fun save(projection: AssignmentSubmissionStatusProjection): Mono<AssignmentSubmissionStatusProjection> =
        repository.save(projection)
}
