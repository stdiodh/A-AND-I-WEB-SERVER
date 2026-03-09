package com.example.aandi_post_web_server.assignment.repository

import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import com.example.aandi_post_web_server.assignment.enum.AssignmentDeliveryStatus
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentDeliveryRepository : ReactiveMongoRepository<AssignmentDelivery, String> {
    fun findByAssignmentIdAndUserId(assignmentId: String, userId: String): Mono<AssignmentDelivery>
    fun findAllByAssignmentId(assignmentId: String): Flux<AssignmentDelivery>
    fun findAllByAssignmentIdAndStatus(assignmentId: String, status: AssignmentDeliveryStatus): Flux<AssignmentDelivery>
    fun findAllByUserId(userId: String): Flux<AssignmentDelivery>
    fun findAllByUserIdAndStatus(userId: String, status: AssignmentDeliveryStatus): Flux<AssignmentDelivery>
    fun deleteAllByAssignmentIdIn(assignmentIds: Collection<String>): Mono<Long>
}
