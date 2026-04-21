package com.example.aandi_post_web_server.assignment.application.submission.service

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.infrastructure.submission.event.JudgeCompletedEvent
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AssignmentSubmissionStatusProjectionServiceTest : StringSpec({
    "동일 이벤트를 두 번 처리해도 projection 은 하나만 유지된다" {
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val service = AssignmentSubmissionStatusProjectionService(
            store = store,
            clock = Clock.fixed(Instant.parse("2026-04-13T08:30:00Z"), ZoneOffset.UTC),
        )
        val event = JudgeCompletedEvent(
            assignmentId = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001",
            publicCode = "A00123",
            score = 80,
            passedCases = 8,
            totalCases = 10,
            timestamp = Instant.parse("2026-04-13T08:20:11Z"),
        )

        StepVerifier.create(service.upsert(event))
            .expectNextCount(1)
            .verifyComplete()
        StepVerifier.create(service.upsert(event))
            .expectNextCount(1)
            .verifyComplete()

        store.findAll() shouldHaveSize 1
        val projection = store.findAll().single()
        projection.assignmentId shouldBe event.assignmentId
        projection.publicCode shouldBe event.publicCode
        projection.firstCompletedAt shouldBe event.timestamp
        projection.lastCompletedAt shouldBe event.timestamp
        projection.latestScore shouldBe 80
        projection.latestPassedCases shouldBe 8
        projection.latestTotalCases shouldBe 10
    }

    "더 최신 timestamp 이벤트가 오면 latest 필드가 갱신되고 firstCompletedAt 은 유지된다" {
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val service = AssignmentSubmissionStatusProjectionService(
            store = store,
            clock = Clock.fixed(Instant.parse("2026-04-13T09:00:00Z"), ZoneOffset.UTC),
        )
        val olderEvent = JudgeCompletedEvent(
            assignmentId = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001",
            publicCode = "A00123",
            score = 60,
            passedCases = 6,
            totalCases = 10,
            timestamp = Instant.parse("2026-04-13T08:20:11Z"),
        )
        val newerEvent = JudgeCompletedEvent(
            assignmentId = olderEvent.assignmentId,
            publicCode = olderEvent.publicCode,
            score = 90,
            passedCases = 9,
            totalCases = 10,
            timestamp = Instant.parse("2026-04-13T08:40:11Z"),
        )

        StepVerifier.create(service.upsert(olderEvent))
            .expectNextCount(1)
            .verifyComplete()
        StepVerifier.create(service.upsert(newerEvent))
            .expectNextCount(1)
            .verifyComplete()

        val projection = store.findAll().single()
        projection.firstCompletedAt shouldBe olderEvent.timestamp
        projection.lastCompletedAt shouldBe newerEvent.timestamp
        projection.lastEventTimestamp shouldBe newerEvent.timestamp
        projection.latestScore shouldBe 90
        projection.latestPassedCases shouldBe 9
        projection.latestTotalCases shouldBe 10
    }
})

private class InMemoryAssignmentSubmissionStatusProjectionStore : AssignmentSubmissionStatusProjectionStore {
    private val projections = linkedMapOf<String, AssignmentSubmissionStatusProjection>()

    override fun findByAssignmentIdAndPublicCode(
        assignmentId: String,
        publicCode: String,
    ): Mono<AssignmentSubmissionStatusProjection> =
        Mono.justOrEmpty(projections["$assignmentId::$publicCode"])

    override fun save(projection: AssignmentSubmissionStatusProjection): Mono<AssignmentSubmissionStatusProjection> {
        val key = "${projection.assignmentId}::${projection.publicCode}"
        val currentVersion = projections[key]?.version ?: -1L
        val persisted = projection.copy(
            id = projection.id ?: key,
            version = currentVersion + 1,
        )
        projections[key] = persisted
        return Mono.just(persisted)
    }

    fun findAll(): List<AssignmentSubmissionStatusProjection> = projections.values.toList()
}
