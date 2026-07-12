package com.example.aandi_post_web_server.assignment.infrastructure.submission.adapter

import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionProjectionReference
import com.example.aandi_post_web_server.assignment.infrastructure.submission.repository.AssignmentSubmissionStatusProjectionRepository
import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import java.time.Instant

class AdminAssignmentSubmissionProjectionQueryAdapterTest : StringSpec({
    val repository = Mockito.mock(AssignmentSubmissionStatusProjectionRepository::class.java)
    val adapter = AdminAssignmentSubmissionProjectionQueryAdapter(repository)

    beforeTest {
        Mockito.reset(repository)
    }

    "projection 조회 결과를 순서와 필드 의미를 유지해 reference로 변환한다" {
        val first = sampleProjection(
            publicCode = "#BE301",
            submitted = false,
            score = 70,
            passedCases = 7,
            totalCases = 10,
            lastCompletedAt = Instant.parse("2026-04-13T08:45:11Z"),
            lastEventTimestamp = Instant.parse("2026-04-13T08:40:11Z"),
        )
        val second = sampleProjection(
            publicCode = "#BE302",
            submitted = true,
            score = 90,
            passedCases = 9,
            totalCases = 12,
            lastCompletedAt = Instant.parse("2026-04-13T09:25:11Z"),
            lastEventTimestamp = Instant.parse("2026-04-13T09:20:11Z"),
        )
        Mockito.`when`(repository.findAllByAssignmentId("assignment-1"))
            .thenReturn(Flux.just(first, second))

        StepVerifier.create(adapter.findAllByAssignmentId("assignment-1"))
            .expectNext(
                AdminAssignmentSubmissionProjectionReference(
                    publicCode = "#BE301",
                    submitted = false,
                    score = 70,
                    passedCases = 7,
                    totalCases = 10,
                    completedAt = Instant.parse("2026-04-13T08:40:11Z"),
                ),
                AdminAssignmentSubmissionProjectionReference(
                    publicCode = "#BE302",
                    submitted = true,
                    score = 90,
                    passedCases = 9,
                    totalCases = 12,
                    completedAt = Instant.parse("2026-04-13T09:20:11Z"),
                ),
            )
            .verifyComplete()

        Mockito.verify(repository).findAllByAssignmentId("assignment-1")
    }

    "repository 오류를 그대로 전파한다" {
        val repositoryError = IllegalStateException("projection query failed")
        Mockito.`when`(repository.findAllByAssignmentId("assignment-1"))
            .thenReturn(Flux.error(repositoryError))

        StepVerifier.create(adapter.findAllByAssignmentId("assignment-1"))
            .expectErrorSatisfies { error -> error shouldBe repositoryError }
            .verify()
    }
})

private fun sampleProjection(
    publicCode: String,
    submitted: Boolean,
    score: Int,
    passedCases: Int,
    totalCases: Int,
    lastCompletedAt: Instant,
    lastEventTimestamp: Instant,
): AssignmentSubmissionStatusProjection =
    AssignmentSubmissionStatusProjection(
        assignmentId = "assignment-1",
        publicCode = publicCode,
        submitted = submitted,
        firstCompletedAt = Instant.parse("2026-04-13T08:00:11Z"),
        lastCompletedAt = lastCompletedAt,
        latestScore = score,
        latestPassedCases = passedCases,
        latestTotalCases = totalCases,
        lastEventTimestamp = lastEventTimestamp,
    )
