package com.example.aandi_post_web_server.submission.repository

import com.example.aandi_post_web_server.submission.entity.AssignmentSubmission
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentSubmissionRepository : ReactiveMongoRepository<AssignmentSubmission, String> {
    fun findByIdAndAssignmentIdAndUserId(id: String, assignmentId: String, userId: String): Mono<AssignmentSubmission>
    fun findAllByAssignmentIdAndUserIdOrderByCreatedAtDesc(assignmentId: String, userId: String): Flux<AssignmentSubmission>
    fun deleteAllByAssignmentIdIn(assignmentIds: Collection<String>): Mono<Long>
}
