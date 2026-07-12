package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentCommandContentStore
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class RepositoryAssignmentCommandContentStore(
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
) : AssignmentCommandContentStore {
    override fun saveRequirements(requirements: List<AssignmentRequirement>): Flux<AssignmentRequirement> =
        assignmentRequirementRepository.saveAll(requirements)

    override fun findOrderedRequirementsByAssignmentId(assignmentId: String): Flux<AssignmentRequirement> =
        assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId)

    override fun deleteRequirementsByAssignmentId(assignmentId: String): Mono<Void> =
        assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then()

    override fun saveTestCases(testCases: List<AssignmentTestCase>): Flux<AssignmentTestCase> =
        assignmentTestCaseRepository.saveAll(testCases)

    override fun findOrderedTestCasesByAssignmentId(assignmentId: String): Flux<AssignmentTestCase> =
        assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId)

    override fun deleteTestCasesByAssignmentId(assignmentId: String): Mono<Void> =
        assignmentTestCaseRepository.deleteAllByAssignmentIdIn(listOf(assignmentId)).then()
}
