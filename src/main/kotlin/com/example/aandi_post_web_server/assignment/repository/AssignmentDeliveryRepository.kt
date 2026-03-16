package com.example.aandi_post_web_server.assignment.repository

import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono

interface AssignmentDeliveryRepository : ReactiveMongoRepository<AssignmentDelivery, String> {
    fun deleteAllByAssignmentIdIn(assignmentIds: Collection<String>): Mono<Long>
}
