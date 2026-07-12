package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentCopyContentStore
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class RepositoryAssignmentCopyContentStore(
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
) : AssignmentCopyContentStore {
    override fun findOrderedRequirementsByAssignmentId(assignmentId: String): Flux<AssignmentRequirement> =
        assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId)

    override fun findOrderedTestCasesByAssignmentId(assignmentId: String): Flux<AssignmentTestCase> =
        assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId)

    override fun saveRequirements(requirements: List<AssignmentRequirement>): Flux<AssignmentRequirement> =
        assignmentRequirementRepository.saveAll(requirements)

    override fun saveTestCases(testCases: List<AssignmentTestCase>): Flux<AssignmentTestCase> =
        assignmentTestCaseRepository.saveAll(testCases)
}
