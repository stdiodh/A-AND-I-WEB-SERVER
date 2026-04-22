package com.example.aandi_post_web_server.assignment.application.submission.service

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.infrastructure.submission.event.JudgeCompletedEvent
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant

interface AssignmentSubmissionStatusProjectionStore {
    fun findByAssignmentIdAndPublicCode(assignmentId: String, publicCode: String): Mono<AssignmentSubmissionStatusProjection>
    fun save(projection: AssignmentSubmissionStatusProjection): Mono<AssignmentSubmissionStatusProjection>
}

class AssignmentSubmissionStatusProjectionStoreException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

class AssignmentSubmissionStatusProjectionService(
    private val store: AssignmentSubmissionStatusProjectionStore,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun upsert(event: JudgeCompletedEvent): Mono<AssignmentSubmissionStatusProjection> =
        upsert(event, attempt = 0)

    internal fun merge(
        existing: AssignmentSubmissionStatusProjection?,
        event: JudgeCompletedEvent,
        now: Instant = Instant.now(clock),
    ): AssignmentSubmissionStatusProjection {
        if (existing == null) {
            return AssignmentSubmissionStatusProjection(
                assignmentId = event.assignmentId,
                publicCode = event.publicCode,
                submitted = true,
                firstCompletedAt = event.timestamp,
                lastCompletedAt = event.timestamp,
                latestScore = event.score,
                latestPassedCases = event.passedCases,
                latestTotalCases = event.totalCases,
                lastEventTimestamp = event.timestamp,
                createdAt = now,
                updatedAt = now,
            )
        }

        val shouldRefreshLatest =
            event.score > existing.latestScore ||
                (event.score == existing.latestScore && !event.timestamp.isBefore(existing.lastEventTimestamp))
        return existing.copy(
            submitted = true,
            firstCompletedAt = minOf(existing.firstCompletedAt, event.timestamp),
            lastCompletedAt = maxOf(existing.lastCompletedAt, event.timestamp),
            latestScore = if (shouldRefreshLatest) event.score else existing.latestScore,
            latestPassedCases = if (shouldRefreshLatest) event.passedCases else existing.latestPassedCases,
            latestTotalCases = if (shouldRefreshLatest) event.totalCases else existing.latestTotalCases,
            lastEventTimestamp = if (shouldRefreshLatest) event.timestamp else existing.lastEventTimestamp,
            updatedAt = now,
        )
    }

    private fun upsert(
        event: JudgeCompletedEvent,
        attempt: Int,
    ): Mono<AssignmentSubmissionStatusProjection> {
        val now = Instant.now(clock)
        return store.findByAssignmentIdAndPublicCode(event.assignmentId, event.publicCode)
            .switchIfEmpty(Mono.just(MISSING_PROJECTION))
            .flatMap { existing ->
                val projection = merge(existing.takeIf { it !== MISSING_PROJECTION }, event, now)
                store.save(projection)
            }
            .onErrorResume { ex ->
                if (attempt >= MAX_RETRY_COUNT || !isRetryable(ex)) {
                    return@onErrorResume Mono.error(
                        AssignmentSubmissionStatusProjectionStoreException(
                            "assignment submission status projection upsert failed after ${attempt + 1} attempt(s)",
                            ex,
                        )
                    )
                }
                upsert(event, attempt + 1)
            }
    }

    private fun isRetryable(ex: Throwable): Boolean {
        val simpleName = ex.javaClass.simpleName
        return simpleName == "DuplicateKeyException" || simpleName == "OptimisticLockingFailureException"
    }

    companion object {
        private const val MAX_RETRY_COUNT = 5

        private val MISSING_PROJECTION = AssignmentSubmissionStatusProjection(
            id = "__missing__",
            assignmentId = "__missing__",
            publicCode = "__missing__",
            firstCompletedAt = Instant.EPOCH,
            lastCompletedAt = Instant.EPOCH,
            latestScore = 0,
            latestPassedCases = 0,
            latestTotalCases = 0,
            lastEventTimestamp = Instant.EPOCH,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
