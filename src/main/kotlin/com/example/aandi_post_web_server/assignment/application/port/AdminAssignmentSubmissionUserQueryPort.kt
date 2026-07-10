package com.example.aandi_post_web_server.assignment.application.port

import reactor.core.publisher.Flux

data class AdminAssignmentSubmissionUserReference(
    val id: String,
    val nickname: String?,
)

interface AdminAssignmentSubmissionUserQueryPort {
    fun findAllByIds(userIds: Collection<String>): Flux<AdminAssignmentSubmissionUserReference>
}
