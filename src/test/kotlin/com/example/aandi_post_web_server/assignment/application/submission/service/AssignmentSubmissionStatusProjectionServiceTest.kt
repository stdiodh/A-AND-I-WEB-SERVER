package com.example.aandi_post_web_server.assignment.application.submission.service

import com.example.aandi_post_web_server.assignment.application.submission.model.JudgeCompletedEvent
import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.dao.DuplicateKeyException
import org.springframework.dao.OptimisticLockingFailureException
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

    "더 높은 점수 이벤트가 오면 최고 점수 기준 필드가 갱신되고 firstCompletedAt 은 유지된다" {
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

    "더 최신 이벤트라도 점수가 낮으면 최고 점수 기준 필드는 유지되고 마지막 제출 시각만 갱신된다" {
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val service = AssignmentSubmissionStatusProjectionService(
            store = store,
            clock = Clock.fixed(Instant.parse("2026-04-13T09:00:00Z"), ZoneOffset.UTC),
        )
        val bestEvent = JudgeCompletedEvent(
            assignmentId = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001",
            publicCode = "A00123",
            score = 100,
            passedCases = 10,
            totalCases = 10,
            timestamp = Instant.parse("2026-04-13T08:20:11Z"),
        )
        val lowerButNewerEvent = JudgeCompletedEvent(
            assignmentId = bestEvent.assignmentId,
            publicCode = bestEvent.publicCode,
            score = 60,
            passedCases = 6,
            totalCases = 10,
            timestamp = Instant.parse("2026-04-13T08:40:11Z"),
        )

        StepVerifier.create(service.upsert(bestEvent))
            .expectNextCount(1)
            .verifyComplete()
        StepVerifier.create(service.upsert(lowerButNewerEvent))
            .expectNextCount(1)
            .verifyComplete()

        val projection = store.findAll().single()
        projection.firstCompletedAt shouldBe bestEvent.timestamp
        projection.lastCompletedAt shouldBe lowerButNewerEvent.timestamp
        projection.lastEventTimestamp shouldBe bestEvent.timestamp
        projection.latestScore shouldBe 100
        projection.latestPassedCases shouldBe 10
        projection.latestTotalCases shouldBe 10
    }

    "신규 projection 저장의 DuplicateKeyException 은 재조회 후 동시 생성 결과와 병합한다" {
        val incomingEvent = judgeCompletedEvent(
            score = 80,
            passedCases = 8,
            timestamp = Instant.parse("2026-04-13T08:20:11Z"),
        )
        val concurrentProjection = statusProjection(
            id = "concurrent-projection",
            score = 90,
            passedCases = 9,
            firstCompletedAt = Instant.parse("2026-04-13T08:40:11Z"),
            version = 0,
        )
        val conflict = DuplicateKeyException("concurrent projection insert")
        val store = RecordingAssignmentSubmissionStatusProjectionStore(
            findResult = { attempt ->
                if (attempt == 1) Mono.empty() else Mono.just(concurrentProjection)
            },
            saveResult = { attempt, projection ->
                if (attempt == 1) {
                    Mono.error(conflict)
                } else {
                    Mono.just(projection.copy(version = (projection.version ?: -1L) + 1L))
                }
            },
        )
        val service = AssignmentSubmissionStatusProjectionService(
            store = store,
            clock = Clock.fixed(Instant.parse("2026-04-13T09:00:00Z"), ZoneOffset.UTC),
        )

        StepVerifier.create(service.upsert(incomingEvent))
            .assertNext { projection ->
                projection.id shouldBe concurrentProjection.id
                projection.version shouldBe 1L
                projection.firstCompletedAt shouldBe incomingEvent.timestamp
                projection.lastCompletedAt shouldBe concurrentProjection.lastCompletedAt
                projection.lastEventTimestamp shouldBe concurrentProjection.lastEventTimestamp
                projection.latestScore shouldBe 90
                projection.latestPassedCases shouldBe 9
            }
            .verifyComplete()

        store.findCount shouldBe 2
        store.saveCount shouldBe 2
        store.savedProjections shouldHaveSize 2
        store.savedProjections[1].id shouldBe concurrentProjection.id
        store.savedProjections[1].version shouldBe concurrentProjection.version
    }

    "기존 projection 저장의 OptimisticLockingFailureException 은 최신 상태를 재조회해 병합한다" {
        val baseProjection = statusProjection(
            score = 60,
            passedCases = 6,
            firstCompletedAt = Instant.parse("2026-04-13T08:10:11Z"),
            version = 0,
        )
        val concurrentProjection = statusProjection(
            score = 100,
            passedCases = 10,
            firstCompletedAt = baseProjection.firstCompletedAt,
            lastCompletedAt = Instant.parse("2026-04-13T08:20:11Z"),
            lastEventTimestamp = Instant.parse("2026-04-13T08:20:11Z"),
            version = 1,
        )
        val incomingEvent = judgeCompletedEvent(
            score = 90,
            passedCases = 9,
            timestamp = Instant.parse("2026-04-13T08:30:11Z"),
        )
        val conflict = OptimisticLockingFailureException("concurrent projection update")
        val store = RecordingAssignmentSubmissionStatusProjectionStore(
            findResult = { attempt ->
                Mono.just(if (attempt == 1) baseProjection else concurrentProjection)
            },
            saveResult = { attempt, projection ->
                if (attempt == 1) {
                    Mono.error(conflict)
                } else {
                    Mono.just(projection.copy(version = (projection.version ?: -1L) + 1L))
                }
            },
        )
        val service = AssignmentSubmissionStatusProjectionService(
            store = store,
            clock = Clock.fixed(Instant.parse("2026-04-13T09:00:00Z"), ZoneOffset.UTC),
        )

        StepVerifier.create(service.upsert(incomingEvent))
            .assertNext { projection ->
                projection.id shouldBe concurrentProjection.id
                projection.version shouldBe 2L
                projection.firstCompletedAt shouldBe baseProjection.firstCompletedAt
                projection.lastCompletedAt shouldBe incomingEvent.timestamp
                projection.lastEventTimestamp shouldBe concurrentProjection.lastEventTimestamp
                projection.latestScore shouldBe 100
                projection.latestPassedCases shouldBe 10
            }
            .verifyComplete()

        store.findCount shouldBe 2
        store.saveCount shouldBe 2
        store.savedProjections shouldHaveSize 2
        store.savedProjections[1].version shouldBe concurrentProjection.version
    }

    "재시도 가능한 충돌이 계속되면 총 6회 후 마지막 원인을 보존해 실패한다" {
        val existingProjection = statusProjection(
            score = 70,
            passedCases = 7,
            firstCompletedAt = Instant.parse("2026-04-13T08:10:11Z"),
            version = 0,
        )
        val incomingEvent = judgeCompletedEvent(
            score = 80,
            passedCases = 8,
            timestamp = Instant.parse("2026-04-13T08:20:11Z"),
        )
        val conflict = OptimisticLockingFailureException("persistent projection conflict")
        val store = RecordingAssignmentSubmissionStatusProjectionStore(
            findResult = { Mono.just(existingProjection) },
            saveResult = { _, _ -> Mono.error(conflict) },
        )
        val service = AssignmentSubmissionStatusProjectionService(
            store = store,
            clock = Clock.fixed(Instant.parse("2026-04-13T09:00:00Z"), ZoneOffset.UTC),
        )

        StepVerifier.create(service.upsert(incomingEvent))
            .expectErrorSatisfies { error ->
                val storeError = error as AssignmentSubmissionStatusProjectionStoreException
                storeError.message shouldBe
                    "assignment submission status projection upsert failed after 6 attempt(s)"
                storeError.cause shouldBe conflict
            }
            .verify()

        store.findCount shouldBe 6
        store.saveCount shouldBe 6
    }

    "projection 조회 오류는 저장하지 않고 1회 시도 예외로 변환한다" {
        val incomingEvent = judgeCompletedEvent(
            score = 80,
            passedCases = 8,
            timestamp = Instant.parse("2026-04-13T08:20:11Z"),
        )
        val failure = IllegalStateException("projection store unavailable")
        val store = RecordingAssignmentSubmissionStatusProjectionStore(
            findResult = { Mono.error(failure) },
            saveResult = { _, projection -> Mono.just(projection) },
        )
        val service = AssignmentSubmissionStatusProjectionService(
            store = store,
            clock = Clock.fixed(Instant.parse("2026-04-13T09:00:00Z"), ZoneOffset.UTC),
        )

        StepVerifier.create(service.upsert(incomingEvent))
            .expectErrorSatisfies { error ->
                val storeError = error as AssignmentSubmissionStatusProjectionStoreException
                storeError.message shouldBe
                    "assignment submission status projection upsert failed after 1 attempt(s)"
                storeError.cause shouldBe failure
            }
            .verify()

        store.findCount shouldBe 1
        store.saveCount shouldBe 0
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

private class RecordingAssignmentSubmissionStatusProjectionStore(
    private val findResult: (attempt: Int) -> Mono<AssignmentSubmissionStatusProjection>,
    private val saveResult: (
        attempt: Int,
        projection: AssignmentSubmissionStatusProjection,
    ) -> Mono<AssignmentSubmissionStatusProjection>,
) : AssignmentSubmissionStatusProjectionStore {
    var findCount: Int = 0
        private set
    var saveCount: Int = 0
        private set
    val savedProjections = mutableListOf<AssignmentSubmissionStatusProjection>()

    override fun findByAssignmentIdAndPublicCode(
        assignmentId: String,
        publicCode: String,
    ): Mono<AssignmentSubmissionStatusProjection> =
        findResult(++findCount)

    override fun save(projection: AssignmentSubmissionStatusProjection): Mono<AssignmentSubmissionStatusProjection> {
        savedProjections += projection
        return saveResult(++saveCount, projection)
    }
}

private fun judgeCompletedEvent(
    score: Int,
    passedCases: Int,
    timestamp: Instant,
): JudgeCompletedEvent =
    JudgeCompletedEvent(
        assignmentId = TEST_ASSIGNMENT_ID,
        publicCode = TEST_PUBLIC_CODE,
        score = score,
        passedCases = passedCases,
        totalCases = 10,
        timestamp = timestamp,
    )

private fun statusProjection(
    id: String = "projection-1",
    score: Int,
    passedCases: Int,
    firstCompletedAt: Instant,
    lastCompletedAt: Instant = firstCompletedAt,
    lastEventTimestamp: Instant = lastCompletedAt,
    version: Long,
): AssignmentSubmissionStatusProjection =
    AssignmentSubmissionStatusProjection(
        id = id,
        assignmentId = TEST_ASSIGNMENT_ID,
        publicCode = TEST_PUBLIC_CODE,
        firstCompletedAt = firstCompletedAt,
        lastCompletedAt = lastCompletedAt,
        latestScore = score,
        latestPassedCases = passedCases,
        latestTotalCases = 10,
        lastEventTimestamp = lastEventTimestamp,
        createdAt = Instant.parse("2026-04-13T08:00:00Z"),
        updatedAt = Instant.parse("2026-04-13T08:00:00Z"),
        version = version,
    )

private const val TEST_ASSIGNMENT_ID = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001"
private const val TEST_PUBLIC_CODE = "A00123"
