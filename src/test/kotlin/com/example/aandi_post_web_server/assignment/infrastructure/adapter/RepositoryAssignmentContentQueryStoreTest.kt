package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class RepositoryAssignmentContentQueryStoreTest : StringSpec({
    "ordered requirements query delegates to sorted repository method" {
        val fixture = ContentQueryStoreFixture()
        val requirement = Mockito.mock(AssignmentRequirement::class.java)
        Mockito.`when`(fixture.requirementRepository.findAllByAssignmentIdOrderBySortOrder("assignment-1"))
            .thenReturn(Flux.just(requirement))

        StepVerifier.create(fixture.store.findOrderedRequirementsByAssignmentId("assignment-1"))
            .expectNext(requirement)
            .verifyComplete()

        Mockito.verify(fixture.requirementRepository)
            .findAllByAssignmentIdOrderBySortOrder("assignment-1")
    }

    "requirement batch query delegates with exact ids" {
        val fixture = ContentQueryStoreFixture()
        val assignmentIds = listOf("assignment-1", "assignment-2")
        val requirement = Mockito.mock(AssignmentRequirement::class.java)
        Mockito.`when`(fixture.requirementRepository.findAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Flux.just(requirement))

        StepVerifier.create(fixture.store.findRequirementsByAssignmentIds(assignmentIds))
            .expectNext(requirement)
            .verifyComplete()

        Mockito.verify(fixture.requirementRepository).findAllByAssignmentIdIn(assignmentIds)
    }

    "ordered test cases query delegates to sorted repository method" {
        val fixture = ContentQueryStoreFixture()
        val testCase = Mockito.mock(AssignmentTestCase::class.java)
        Mockito.`when`(fixture.testCaseRepository.findAllByAssignmentIdOrderBySeq("assignment-1"))
            .thenReturn(Flux.just(testCase))

        StepVerifier.create(fixture.store.findOrderedTestCasesByAssignmentId("assignment-1"))
            .expectNext(testCase)
            .verifyComplete()

        Mockito.verify(fixture.testCaseRepository).findAllByAssignmentIdOrderBySeq("assignment-1")
    }

    "test case batch query delegates with exact ids" {
        val fixture = ContentQueryStoreFixture()
        val assignmentIds = listOf("assignment-1", "assignment-2")
        val testCase = Mockito.mock(AssignmentTestCase::class.java)
        Mockito.`when`(fixture.testCaseRepository.findAllByAssignmentIdIn(assignmentIds))
            .thenReturn(Flux.just(testCase))

        StepVerifier.create(fixture.store.findTestCasesByAssignmentIds(assignmentIds))
            .expectNext(testCase)
            .verifyComplete()

        Mockito.verify(fixture.testCaseRepository).findAllByAssignmentIdIn(assignmentIds)
    }

    "repository error is propagated unchanged" {
        val fixture = ContentQueryStoreFixture()
        val failure = IllegalStateException("content query failed")
        Mockito.`when`(fixture.requirementRepository.findAllByAssignmentIdOrderBySortOrder("assignment-1"))
            .thenReturn(Flux.error(failure))

        StepVerifier.create(fixture.store.findOrderedRequirementsByAssignmentId("assignment-1"))
            .expectErrorMatches { error -> error === failure }
            .verify()
    }
})

private class ContentQueryStoreFixture {
    val requirementRepository: AssignmentRequirementRepository =
        Mockito.mock(AssignmentRequirementRepository::class.java)
    val testCaseRepository: AssignmentTestCaseRepository =
        Mockito.mock(AssignmentTestCaseRepository::class.java)
    val store = RepositoryAssignmentContentQueryStore(requirementRepository, testCaseRepository)
}
