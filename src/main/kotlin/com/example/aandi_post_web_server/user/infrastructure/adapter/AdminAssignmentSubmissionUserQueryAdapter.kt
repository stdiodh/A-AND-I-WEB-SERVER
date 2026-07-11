package com.example.aandi_post_web_server.user.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionUserQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionUserReference
import com.example.aandi_post_web_server.user.infrastructure.repository.ReportUserRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class AdminAssignmentSubmissionUserQueryAdapter(
    private val reportUserRepository: ReportUserRepository,
) : AdminAssignmentSubmissionUserQueryPort {
    override fun findAllByIds(userIds: Collection<String>): Flux<AdminAssignmentSubmissionUserReference> =
        reportUserRepository.findAllByIdInAndDeletedAtIsNull(userIds)
            .map { user ->
                AdminAssignmentSubmissionUserReference(
                    id = user.id,
                    nickname = user.nickname,
                )
            }
}
