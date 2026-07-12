package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import reactor.core.publisher.Flux

interface AssignmentContentQueryStore {
    fun findOrderedRequirementsByAssignmentId(assignmentId: String): Flux<AssignmentRequirement>
    fun findRequirementsByAssignmentIds(assignmentIds: Collection<String>): Flux<AssignmentRequirement>
    fun findOrderedTestCasesByAssignmentId(assignmentId: String): Flux<AssignmentTestCase>
    fun findTestCasesByAssignmentIds(assignmentIds: Collection<String>): Flux<AssignmentTestCase>
}
