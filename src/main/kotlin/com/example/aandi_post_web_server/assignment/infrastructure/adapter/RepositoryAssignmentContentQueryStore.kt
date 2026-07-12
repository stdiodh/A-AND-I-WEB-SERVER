package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.application.port.AssignmentContentQueryStore
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

@Component
class RepositoryAssignmentContentQueryStore(
    private val assignmentRequirementRepository: AssignmentRequirementRepository,
    private val assignmentTestCaseRepository: AssignmentTestCaseRepository,
) : AssignmentContentQueryStore {
    override fun findOrderedRequirementsByAssignmentId(assignmentId: String): Flux<AssignmentRequirement> =
        assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId)

    override fun findRequirementsByAssignmentIds(assignmentIds: Collection<String>): Flux<AssignmentRequirement> =
        assignmentRequirementRepository.findAllByAssignmentIdIn(assignmentIds)

    override fun findOrderedTestCasesByAssignmentId(assignmentId: String): Flux<AssignmentTestCase> =
        assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId)

    override fun findTestCasesByAssignmentIds(assignmentIds: Collection<String>): Flux<AssignmentTestCase> =
        assignmentTestCaseRepository.findAllByAssignmentIdIn(assignmentIds)
}
