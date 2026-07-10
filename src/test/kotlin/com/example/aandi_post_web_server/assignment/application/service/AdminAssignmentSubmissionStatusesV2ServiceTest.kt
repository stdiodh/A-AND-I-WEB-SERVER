package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionCourseQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionEnrollment
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionUserQueryPort
import com.example.aandi_post_web_server.assignment.application.port.AdminAssignmentSubmissionUserReference
import com.example.aandi_post_web_server.assignment.infrastructure.submission.repository.AssignmentSubmissionStatusProjectionRepository
import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class AdminAssignmentSubmissionStatusesV2ServiceTest : StringSpec({
    val courseQueryPort = Mockito.mock(AdminAssignmentSubmissionCourseQueryPort::class.java)
    val projectionRepository = Mockito.mock(AssignmentSubmissionStatusProjectionRepository::class.java)
    val userQueryPort = Mockito.mock(AdminAssignmentSubmissionUserQueryPort::class.java)
    val service = AdminAssignmentSubmissionStatusesV2Service(courseQueryPort, projectionRepository, userQueryPort)

    val courseSlug = "back-basic"
    val assignmentId = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001"

    beforeTest {
        Mockito.reset(courseQueryPort, projectionRepository, userQueryPort)
    }

    "코스 수강생 전체 기준으로 제출/미제출 현황을 조합한다" {
        Mockito.`when`(courseQueryPort.ensureAssignmentBelongsToCourse(courseSlug, assignmentId))
            .thenReturn(Mono.empty())
        Mockito.`when`(courseQueryPort.findEnrollments(courseSlug))
            .thenReturn(
                Flux.just(
                    sampleEnrollment("user-1", "#BE301", "alice"),
                    sampleEnrollment("user-2", "#BE302", "bob"),
                )
            )
        Mockito.`when`(projectionRepository.findAllByAssignmentId(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentSubmissionStatusProjection(
                        id = "projection-1",
                        assignmentId = assignmentId,
                        publicCode = "#BE301",
                        submitted = true,
                        firstCompletedAt = Instant.parse("2026-04-13T08:20:11Z"),
                        lastCompletedAt = Instant.parse("2026-04-13T08:40:11Z"),
                        latestScore = 90,
                        latestPassedCases = 9,
                        latestTotalCases = 10,
                        lastEventTimestamp = Instant.parse("2026-04-13T08:40:11Z"),
                    )
                )
            )
        Mockito.`when`(userQueryPort.findAllByIds(listOf("user-1", "user-2")))
            .thenReturn(
                Flux.just(
                    sampleUser("user-1", "앨리스"),
                    sampleUser("user-2", null),
                )
            )

        StepVerifier.create(service.getSubmissionStatuses(courseSlug, assignmentId))
            .assertNext { response ->
                response.assignmentId shouldBe assignmentId
                response.courseSlug shouldBe courseSlug
                response.totalEnrolled shouldBe 2
                response.submittedCount shouldBe 1
                response.notSubmittedCount shouldBe 1
                response.items[0].userId shouldBe "user-1"
                response.items[0].username shouldBe "앨리스"
                response.items[0].submitted shouldBe true
                response.items[0].score shouldBe 90
                response.items[0].completedAt shouldBe Instant.parse("2026-04-13T08:40:11Z")
                response.items[1].userId shouldBe "user-2"
                response.items[1].username shouldBe "bob"
                response.items[1].submitted shouldBe false
                response.items[1].score shouldBe null
                response.items[1].completedAt shouldBe null
            }
            .verifyComplete()
    }

    "projection 이 전혀 없어도 전체 수강생이 미제출로 반환된다" {
        Mockito.`when`(courseQueryPort.ensureAssignmentBelongsToCourse(courseSlug, assignmentId))
            .thenReturn(Mono.empty())
        Mockito.`when`(courseQueryPort.findEnrollments(courseSlug))
            .thenReturn(Flux.just(sampleEnrollment("user-1", "#BE301", "alice")))
        Mockito.`when`(projectionRepository.findAllByAssignmentId(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(userQueryPort.findAllByIds(listOf("user-1")))
            .thenReturn(Flux.empty())

        StepVerifier.create(service.getSubmissionStatuses(courseSlug, assignmentId))
            .assertNext { response ->
                response.totalEnrolled shouldBe 1
                response.submittedCount shouldBe 0
                response.notSubmittedCount shouldBe 1
                response.items.single().username shouldBe "alice"
                response.items.single().submitted shouldBe false
            }
            .verifyComplete()
    }

    "courseSlug 가 없으면 404를 그대로 전달한다" {
        Mockito.`when`(courseQueryPort.ensureAssignmentBelongsToCourse(courseSlug, assignmentId))
            .thenReturn(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다.")))

        StepVerifier.create(service.getSubmissionStatuses(courseSlug, assignmentId))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
            }
            .verify()

        Mockito.verify(courseQueryPort, Mockito.never()).findEnrollments(Mockito.anyString())
        Mockito.verifyNoInteractions(projectionRepository, userQueryPort)
    }

    "assignment 가 다른 course 소속이면 404를 그대로 전달한다" {
        Mockito.`when`(courseQueryPort.ensureAssignmentBelongsToCourse(courseSlug, assignmentId))
            .thenReturn(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다.")))

        StepVerifier.create(service.getSubmissionStatuses(courseSlug, assignmentId))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
            }
            .verify()

        Mockito.verify(courseQueryPort, Mockito.never()).findEnrollments(Mockito.anyString())
        Mockito.verifyNoInteractions(projectionRepository, userQueryPort)
    }
})

private fun sampleEnrollment(
    userId: String,
    publicCode: String,
    username: String,
): AdminAssignmentSubmissionEnrollment =
    AdminAssignmentSubmissionEnrollment(
        userId = userId,
        publicCode = publicCode,
        username = username,
        status = EnrollmentStatus.ENABLED,
    )

private fun sampleUser(
    userId: String,
    nickname: String?,
): AdminAssignmentSubmissionUserReference =
    AdminAssignmentSubmissionUserReference(
        id = userId,
        nickname = nickname,
    )
