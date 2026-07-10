package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentTestCaseRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class DirectAssignmentProblemSyncAdapterTest : StringSpec({
    "created 는 repository 의 최신 snapshot 을 mapper payload 로 publish 한다" {
        val fixture = DirectAssignmentProblemSyncFixture()
        fixture.stubSnapshot(
            assignment = assignment(),
            testCases = listOf(
                testCase(seq = 3, visibility = AssignmentTestCaseVisibility.EXCLUDED),
                testCase(seq = 2, inputValues = listOf("3", "4"), outputText = "7", visibility = AssignmentTestCaseVisibility.HIDDEN),
                testCase(seq = 1, inputValues = listOf("1", "2"), outputText = "3"),
            ),
        )
        fixture.stubPublish(Mono.empty())

        StepVerifier.create(fixture.adapter.publishCreated(ASSIGNMENT_ID))
            .verifyComplete()

        fixture.publishedEvent() shouldBe AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.PROBLEM_CREATED,
            problemId = ASSIGNMENT_ID,
            testCases = listOf(
                AssignmentReportTestCase(caseId = 1, input = listOf("1", "2"), output = "3"),
                AssignmentReportTestCase(caseId = 2, input = listOf("3", "4"), output = "7"),
            ),
        )
        Mockito.verify(fixture.assignmentRepository).findById(ASSIGNMENT_ID)
        Mockito.verify(fixture.assignmentTestCaseRepository).findAllByAssignmentIdOrderBySeq(ASSIGNMENT_ID)
    }

    "updated 는 PROBLEM_UPDATED 를 publish 하고 publisher 오류 인스턴스를 그대로 전파한다" {
        val fixture = DirectAssignmentProblemSyncFixture()
        val publisherError = IllegalStateException("sns unavailable")
        fixture.stubSnapshot(assignment = assignment(), testCases = emptyList())
        fixture.stubPublish(Mono.error(publisherError))

        StepVerifier.create(fixture.adapter.publishUpdated(ASSIGNMENT_ID))
            .expectErrorMatches { error -> error === publisherError }
            .verify()

        fixture.publishedEvent().eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_UPDATED
    }

    "snapshot 이 없으면 정확한 오류를 반환하고 publish 하지 않는다" {
        val fixture = DirectAssignmentProblemSyncFixture()
        Mockito.`when`(fixture.assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Mono.empty())
        Mockito.`when`(
            fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(ASSIGNMENT_ID)
        ).thenReturn(Flux.empty())

        StepVerifier.create(fixture.adapter.publishCreated(ASSIGNMENT_ID))
            .expectErrorSatisfies { error ->
                error::class shouldBe IllegalStateException::class
                error.message shouldBe "problem sync 대상 assignment snapshot을 찾을 수 없습니다: $ASSIGNMENT_ID"
            }
            .verify()

        fixture.eventPublisher.events shouldBe emptyList()
    }

    "deleted 는 repository 를 조회하지 않고 빈 payload 를 publish 한다" {
        val fixture = DirectAssignmentProblemSyncFixture()
        fixture.stubPublish(Mono.empty())

        val publish = fixture.adapter.publishDeleted(ASSIGNMENT_ID)
        fixture.eventPublisher.events shouldBe emptyList()

        StepVerifier.create(publish)
            .verifyComplete()

        fixture.publishedEvent() shouldBe AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.PROBLEM_DELETED,
            problemId = ASSIGNMENT_ID,
            testCases = emptyList(),
        )
        Mockito.verifyNoInteractions(
            fixture.assignmentRepository,
            fixture.assignmentTestCaseRepository,
        )
    }
})

private class DirectAssignmentProblemSyncFixture {
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentTestCaseRepository: AssignmentTestCaseRepository = Mockito.mock(AssignmentTestCaseRepository::class.java)
    val eventPublisher = RecordingProblemSyncEventPublisher()
    val adapter = DirectAssignmentProblemSyncAdapter(
        assignmentRepository = assignmentRepository,
        assignmentTestCaseRepository = assignmentTestCaseRepository,
        eventMapper = AssignmentReportTestCaseEventMapper(),
        eventPublisher = eventPublisher,
    )
    fun stubSnapshot(
        assignment: Assignment,
        testCases: List<AssignmentTestCase>,
    ) {
        Mockito.`when`(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Mono.just(assignment))
        Mockito.`when`(assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(ASSIGNMENT_ID))
            .thenReturn(Flux.fromIterable(testCases))
    }

    fun stubPublish(result: Mono<Void>) {
        eventPublisher.result = result
    }

    fun publishedEvent(): AssignmentReportTestCaseEvent = eventPublisher.events.single()
}

private class RecordingProblemSyncEventPublisher : AssignmentReportTestCaseEventPublisher {
    val events = mutableListOf<AssignmentReportTestCaseEvent>()
    var result: Mono<Void> = Mono.empty()

    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        events += event
        return result
    }
}

private fun assignment(): Assignment = Assignment(
    id = ASSIGNMENT_ID,
    courseId = "course-1",
    courseSlug = "back-basic",
    createdBy = "admin-1",
    weekNo = 1,
    orderInWeek = 1,
    startAt = Instant.parse("2026-03-18T00:00:00Z"),
    endAt = Instant.parse("2026-03-25T00:00:00Z"),
    metadata = AssignmentMetadata(
        title = "latest title",
        difficulty = AssignmentDifficulty.MID,
        description = "latest description",
        timeLimitMinutes = 60,
    ),
    status = AssignmentStatus.PUBLISHED,
)

private fun testCase(
    seq: Int,
    inputValues: List<String> = listOf("excluded"),
    outputText: String = "excluded",
    visibility: AssignmentTestCaseVisibility = AssignmentTestCaseVisibility.PUBLIC,
): AssignmentTestCase = AssignmentTestCase(
    assignmentId = ASSIGNMENT_ID,
    seq = seq,
    inputValues = inputValues,
    outputText = outputText,
    visibility = visibility,
)

private const val ASSIGNMENT_ID = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
