package com.example.aandi_post_web_server.assignment.application.port

import reactor.core.publisher.Mono

interface AssignmentDocumentCleanupPort {
    fun deleteByAssignmentId(assignmentId: String): Mono<Void>
    fun deleteAllByAssignmentIds(assignmentIds: Collection<String>): Mono<Void>
}
