package com.example.aandi_post_web_server.assignment.infrastructure.adapter

import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher

class RepositoryAssignmentDocumentCleanupAdapterTest : StringSpec({
    "single cleanup uses deleteById after child documents" {
        val fixture = DocumentCleanupFixture()
        fixture.stubSuccessfulDeletes(listOf("assignment-1"))

        StepVerifier.create(fixture.adapter.deleteByAssignmentId("assignment-1"))
            .verifyComplete()

        Mockito.verify(fixture.requirementRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.testCaseRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.deliveryRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.assignmentRepository).deleteById("assignment-1")
        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .deleteAllById(listOf("assignment-1"))
    }

    "bulk cleanup uses deleteAllById with exact ids" {
        val fixture = DocumentCleanupFixture()
        val assignmentIds = listOf("assignment-1", "assignment-2")
        fixture.stubSuccessfulDeletes(assignmentIds, bulk = true)

        StepVerifier.create(fixture.adapter.deleteAllByAssignmentIds(assignmentIds))
            .verifyComplete()

        Mockito.verify(fixture.requirementRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.testCaseRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.deliveryRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentRepository).deleteAllById(assignmentIds)
        Mockito.verify(fixture.assignmentRepository, Mockito.never()).deleteById(Mockito.anyString())
    }

    "cleanup subscribes sequentially and combines failures" {
        val fixture = DocumentCleanupFixture()
        val assignmentIds = listOf("assignment-1")
        val requirementDelete = TestPublisher.create<Long>()
        val testCaseDelete = TestPublisher.create<Long>()
        val deliveryDelete = TestPublisher.create<Long>()
        val assignmentDelete = TestPublisher.create<Void>()
        val requirementFailure = IllegalStateException("requirement delete failed")
        val deliveryFailure = IllegalArgumentException("delivery delete failed")
        Mockito.`when`(fixture.requirementRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(requirementDelete.mono())
        Mockito.`when`(fixture.testCaseRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(testCaseDelete.mono())
        Mockito.`when`(fixture.deliveryRepository.deleteAllByAssignmentIdIn(assignmentIds))
            .thenReturn(deliveryDelete.mono())
        Mockito.`when`(fixture.assignmentRepository.deleteAllById(assignmentIds))
            .thenReturn(assignmentDelete.mono())

        StepVerifier.create(fixture.adapter.deleteAllByAssignmentIds(assignmentIds))
            .then {
                requirementDelete.assertSubscribers(1)
                testCaseDelete.assertNoSubscribers()
                deliveryDelete.assertNoSubscribers()
                assignmentDelete.assertNoSubscribers()
            }
            .then { requirementDelete.error(requirementFailure) }
            .then {
                testCaseDelete.assertSubscribers(1)
                deliveryDelete.assertNoSubscribers()
                assignmentDelete.assertNoSubscribers()
            }
            .then { testCaseDelete.emit(1L) }
            .then {
                deliveryDelete.assertSubscribers(1)
                assignmentDelete.assertNoSubscribers()
            }
            .then { deliveryDelete.error(deliveryFailure) }
            .then { assignmentDelete.assertSubscribers(1) }
            .then { assignmentDelete.complete() }
            .expectErrorSatisfies { error ->
                val failures = Exceptions.unwrapMultipleExcludingTracebacks(error)
                failures shouldHaveSize 2
                failures.contains(requirementFailure) shouldBe true
                failures.contains(deliveryFailure) shouldBe true
            }
            .verify()
    }
})

private class DocumentCleanupFixture {
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val requirementRepository: AssignmentRequirementRepository =
        Mockito.mock(AssignmentRequirementRepository::class.java)
    val testCaseRepository: AssignmentTestCaseRepository =
        Mockito.mock(AssignmentTestCaseRepository::class.java)
    val deliveryRepository: AssignmentDeliveryRepository =
        Mockito.mock(AssignmentDeliveryRepository::class.java)
    val adapter = RepositoryAssignmentDocumentCleanupAdapter(
        assignmentRepository,
        requirementRepository,
        testCaseRepository,
        deliveryRepository,
    )

    fun stubSuccessfulDeletes(
        assignmentIds: Collection<String>,
        bulk: Boolean = false,
    ) {
        Mockito.`when`(requirementRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(1L))
        Mockito.`when`(testCaseRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(1L))
        Mockito.`when`(deliveryRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(1L))
        if (bulk) {
            Mockito.`when`(assignmentRepository.deleteAllById(assignmentIds)).thenReturn(Mono.empty())
        } else {
            Mockito.`when`(assignmentRepository.deleteById(assignmentIds.single())).thenReturn(Mono.empty())
        }
    }
}
