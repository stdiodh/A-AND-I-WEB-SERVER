package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import io.kotest.core.spec.style.StringSpec
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class RepositoryAssignmentCopyContentStoreTest : StringSpec({
    "ordered requirement query delegates to sorted repository method" {
        val fixture = CopyContentStoreFixture()
        val requirement = Mockito.mock(AssignmentRequirement::class.java)
        Mockito.`when`(fixture.requirementRepository.findAllByAssignmentIdOrderBySortOrder("assignment-1"))
            .thenReturn(Flux.just(requirement))

        StepVerifier.create(fixture.store.findOrderedRequirementsByAssignmentId("assignment-1"))
            .expectNext(requirement)
            .verifyComplete()

        Mockito.verify(fixture.requirementRepository)
            .findAllByAssignmentIdOrderBySortOrder("assignment-1")
    }

    "ordered test case query delegates to sorted repository method" {
        val fixture = CopyContentStoreFixture()
        val testCase = Mockito.mock(AssignmentTestCase::class.java)
        Mockito.`when`(fixture.testCaseRepository.findAllByAssignmentIdOrderBySeq("assignment-1"))
            .thenReturn(Flux.just(testCase))

        StepVerifier.create(fixture.store.findOrderedTestCasesByAssignmentId("assignment-1"))
            .expectNext(testCase)
            .verifyComplete()

        Mockito.verify(fixture.testCaseRepository).findAllByAssignmentIdOrderBySeq("assignment-1")
    }

    "requirement save delegates with exact list" {
        val fixture = CopyContentStoreFixture()
        val requirement = Mockito.mock(AssignmentRequirement::class.java)
        val requirements = listOf(requirement)
        Mockito.`when`(fixture.requirementRepository.saveAll(requirements)).thenReturn(Flux.just(requirement))

        StepVerifier.create(fixture.store.saveRequirements(requirements))
            .expectNext(requirement)
            .verifyComplete()

        Mockito.verify(fixture.requirementRepository).saveAll(requirements)
    }

    "test case save delegates with exact list" {
        val fixture = CopyContentStoreFixture()
        val testCase = Mockito.mock(AssignmentTestCase::class.java)
        val testCases = listOf(testCase)
        Mockito.`when`(fixture.testCaseRepository.saveAll(testCases)).thenReturn(Flux.just(testCase))

        StepVerifier.create(fixture.store.saveTestCases(testCases))
            .expectNext(testCase)
            .verifyComplete()

        Mockito.verify(fixture.testCaseRepository).saveAll(testCases)
    }

    "duplicate key save error is propagated unchanged" {
        val fixture = CopyContentStoreFixture()
        val requirement = Mockito.mock(AssignmentRequirement::class.java)
        val requirements = listOf(requirement)
        val failure = DuplicateKeyException("duplicate child")
        Mockito.`when`(fixture.requirementRepository.saveAll(requirements)).thenReturn(Flux.error(failure))

        StepVerifier.create(fixture.store.saveRequirements(requirements))
            .expectErrorMatches { error -> error === failure }
            .verify()
    }
})

private class CopyContentStoreFixture {
    val requirementRepository: AssignmentRequirementRepository =
        Mockito.mock(AssignmentRequirementRepository::class.java)
    val testCaseRepository: AssignmentTestCaseRepository =
        Mockito.mock(AssignmentTestCaseRepository::class.java)
    val store = RepositoryAssignmentCopyContentStore(requirementRepository, testCaseRepository)
}
