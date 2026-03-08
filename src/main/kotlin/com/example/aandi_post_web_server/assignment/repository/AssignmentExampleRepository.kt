package com.example.aandi_post_web_server.assignment.repository

import com.example.aandi_post_web_server.assignment.entity.AssignmentExample
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentExampleRepository : ReactiveMongoRepository<AssignmentExample, String> {
    fun findAllByAssignmentIdOrderBySeq(assignmentId: String): Flux<AssignmentExample>
    fun deleteAllByAssignmentIdIn(assignmentIds: Collection<String>): Mono<Long>
}
