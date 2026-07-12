package com.example.aandi_post_web_server.assignment.application.port

import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface AssignmentCommandContentStore {
    fun saveRequirements(requirements: List<AssignmentRequirement>): Flux<AssignmentRequirement>
    fun findOrderedRequirementsByAssignmentId(assignmentId: String): Flux<AssignmentRequirement>
    fun deleteRequirementsByAssignmentId(assignmentId: String): Mono<Void>
    fun saveTestCases(testCases: List<AssignmentTestCase>): Flux<AssignmentTestCase>
    fun findOrderedTestCasesByAssignmentId(assignmentId: String): Flux<AssignmentTestCase>
    fun deleteTestCasesByAssignmentId(assignmentId: String): Mono<Void>
}
