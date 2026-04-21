package com.example.aandi_post_web_server.assignment.infrastructure.repository

import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentRequirementRepository : ReactiveMongoRepository<AssignmentRequirement, String> {
    fun findAllByAssignmentIdOrderBySortOrder(assignmentId: String): Flux<AssignmentRequirement>
    fun deleteAllByAssignmentIdIn(assignmentIds: Collection<String>): Mono<Long>
}
