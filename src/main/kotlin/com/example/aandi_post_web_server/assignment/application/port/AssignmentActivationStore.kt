package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentActivation
import reactor.core.publisher.Mono

interface AssignmentActivationStore {
    fun findById(id: String): Mono<AssignmentActivation>

    fun save(activation: AssignmentActivation): Mono<AssignmentActivation>
}
