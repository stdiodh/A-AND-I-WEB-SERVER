package com.example.aandi_post_web_server.assignment.application.port

import reactor.core.publisher.Mono

interface AssignmentProblemSyncPort {
    fun publishCreated(assignmentId: String): Mono<Void>

    fun publishUpdated(assignmentId: String): Mono<Void>

    fun publishDeleted(assignmentId: String): Mono<Void>
}
