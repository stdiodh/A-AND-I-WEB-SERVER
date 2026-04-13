package com.example.aandi_post_web_server.assignment.v2.service

import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailMetadataResponse
import com.example.aandi_post_web_server.assignment.dtos.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.submission.repository.AssignmentSubmissionStatusProjectionRepository
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.course.dtos.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.service.CourseV1Service
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
    val courseV1Service = Mockito.mock(CourseV1Service::class.java)
    val projectionRepository = Mockito.mock(AssignmentSubmissionStatusProjectionRepository::class.java)
    val service = AdminAssignmentSubmissionStatusesV2Service(courseV1Service, projectionRepository)

    val courseSlug = "back-basic"
    val assignmentId = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001"

    beforeTest {
        Mockito.reset(courseV1Service, projectionRepository)
    }

    "코스 수강생 전체 기준으로 제출/미제출 현황을 조합한다" {
        Mockito.`when`(courseV1Service.getAdminAssignmentDetail(courseSlug, assignmentId))
            .thenReturn(Mono.just(sampleAssignmentDetailResponse(courseSlug, assignmentId)))
        Mockito.`when`(courseV1Service.getEnrollments(courseSlug))
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

        StepVerifier.create(service.getSubmissionStatuses(courseSlug, assignmentId))
            .assertNext { response ->
                response.assignmentId shouldBe assignmentId
                response.courseSlug shouldBe courseSlug
                response.totalEnrolled shouldBe 2
                response.submittedCount shouldBe 1
                response.notSubmittedCount shouldBe 1
                response.items[0].userId shouldBe "user-1"
                response.items[0].submitted shouldBe true
                response.items[0].score shouldBe 90
                response.items[0].completedAt shouldBe Instant.parse("2026-04-13T08:40:11Z")
                response.items[1].userId shouldBe "user-2"
                response.items[1].submitted shouldBe false
                response.items[1].score shouldBe null
                response.items[1].completedAt shouldBe null
            }
            .verifyComplete()
    }

    "projection 이 전혀 없어도 전체 수강생이 미제출로 반환된다" {
        Mockito.`when`(courseV1Service.getAdminAssignmentDetail(courseSlug, assignmentId))
            .thenReturn(Mono.just(sampleAssignmentDetailResponse(courseSlug, assignmentId)))
        Mockito.`when`(courseV1Service.getEnrollments(courseSlug))
            .thenReturn(Flux.just(sampleEnrollment("user-1", "#BE301", "alice")))
        Mockito.`when`(projectionRepository.findAllByAssignmentId(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(service.getSubmissionStatuses(courseSlug, assignmentId))
            .assertNext { response ->
                response.totalEnrolled shouldBe 1
                response.submittedCount shouldBe 0
                response.notSubmittedCount shouldBe 1
                response.items.single().submitted shouldBe false
            }
            .verifyComplete()
    }

    "courseSlug 가 없으면 404를 그대로 전달한다" {
        Mockito.`when`(courseV1Service.getAdminAssignmentDetail(courseSlug, assignmentId))
            .thenReturn(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "코스를 찾을 수 없습니다.")))

        StepVerifier.create(service.getSubmissionStatuses(courseSlug, assignmentId))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
            }
            .verify()
    }

    "assignment 가 다른 course 소속이면 404를 그대로 전달한다" {
        Mockito.`when`(courseV1Service.getAdminAssignmentDetail(courseSlug, assignmentId))
            .thenReturn(Mono.error(ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다.")))

        StepVerifier.create(service.getSubmissionStatuses(courseSlug, assignmentId))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
            }
            .verify()
    }
})

private fun sampleAssignmentDetailResponse(
    courseSlug: String,
    assignmentId: String,
): AssignmentDetailResponse =
    AssignmentDetailResponse(
        id = assignmentId,
        courseSlug = courseSlug,
        weekNo = 1,
        orderInWeek = 1,
        startAt = Instant.parse("2026-03-03T00:00:00Z"),
        endAt = Instant.parse("2026-03-11T00:00:00Z"),
        status = AssignmentStatus.PUBLISHED,
        publishedAt = Instant.parse("2026-03-03T00:00:00Z"),
        metadata = AssignmentDetailMetadataResponse(
            title = "터미널 계산기",
            difficulty = AssignmentDifficulty.MID,
            description = "# 문제 설명",
        ),
    )

private fun sampleEnrollment(
    userId: String,
    publicCode: String,
    username: String,
): CourseEnrollmentResponse =
    CourseEnrollmentResponse(
        courseId = "course-1",
        courseSlug = "back-basic",
        userId = userId,
        publicCode = publicCode,
        username = username,
        status = EnrollmentStatus.ENABLED,
        joinedAt = Instant.parse("2026-03-05T09:20:18Z"),
        bannedAt = null,
        banReason = null,
        updatedAt = Instant.parse("2026-03-05T09:20:18Z"),
    )
