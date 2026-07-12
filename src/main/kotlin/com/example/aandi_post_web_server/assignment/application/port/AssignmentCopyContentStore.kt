package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import reactor.core.publisher.Flux

interface AssignmentCopyContentStore {
    fun findOrderedRequirementsByAssignmentId(assignmentId: String): Flux<AssignmentRequirement>
    fun findOrderedTestCasesByAssignmentId(assignmentId: String): Flux<AssignmentTestCase>
    fun saveRequirements(requirements: List<AssignmentRequirement>): Flux<AssignmentRequirement>
    fun saveTestCases(testCases: List<AssignmentTestCase>): Flux<AssignmentTestCase>
}
