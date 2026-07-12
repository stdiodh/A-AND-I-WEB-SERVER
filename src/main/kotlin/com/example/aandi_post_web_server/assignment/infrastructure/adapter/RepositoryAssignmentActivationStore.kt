package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentActivationStore
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentActivation
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentActivationRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class RepositoryAssignmentActivationStore(
    private val repository: AssignmentActivationRepository,
) : AssignmentActivationStore {
    override fun findById(id: String): Mono<AssignmentActivation> =
        repository.findById(id)

    override fun save(activation: AssignmentActivation): Mono<AssignmentActivation> =
        repository.save(activation)
}
