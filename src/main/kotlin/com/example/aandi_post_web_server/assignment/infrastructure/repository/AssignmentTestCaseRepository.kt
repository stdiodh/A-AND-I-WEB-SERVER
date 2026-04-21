package com.example.aandi_post_web_server.assignment.infrastructure.repository

import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentTestCaseRepository : ReactiveMongoRepository<AssignmentTestCase, String> {
    fun findAllByAssignmentIdOrderBySeq(assignmentId: String): Flux<AssignmentTestCase>
    fun deleteAllByAssignmentIdIn(assignmentIds: Collection<String>): Mono<Long>
}
