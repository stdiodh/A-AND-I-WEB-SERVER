package com.example.aandi_post_web_server.assignment.submission.repository

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentSubmissionStatusProjectionRepository :
    ReactiveMongoRepository<AssignmentSubmissionStatusProjection, String> {
    fun findByAssignmentIdAndPublicCode(assignmentId: String, publicCode: String): Mono<AssignmentSubmissionStatusProjection>
    fun findAllByAssignmentId(assignmentId: String): Flux<AssignmentSubmissionStatusProjection>
}
