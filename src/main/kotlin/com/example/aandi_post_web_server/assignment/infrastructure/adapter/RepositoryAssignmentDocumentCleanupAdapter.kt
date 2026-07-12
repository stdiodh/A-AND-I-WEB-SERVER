package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentDocumentCleanupPort
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class RepositoryAssignmentDocumentCleanupAdapter(
    private val assignmentRepository: AssignmentRepository,
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
    private val assignmentDeliveryRepository: AssignmentDeliveryRepository,
) : AssignmentDocumentCleanupPort {
    override fun deleteByAssignmentId(assignmentId: String): Mono<Void> =
        Flux.concatDelayError(
            assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then(),
            assignmentRepository.deleteById(assignmentId).then(),
        ).then()

    override fun deleteAllByAssignmentIds(assignmentIds: Collection<String>): Mono<Void> =
        Flux.concatDelayError(
            assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
            assignmentTestCaseRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
            assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds).then(),
            assignmentRepository.deleteAllById(assignmentIds).then(),
        ).then()
}
