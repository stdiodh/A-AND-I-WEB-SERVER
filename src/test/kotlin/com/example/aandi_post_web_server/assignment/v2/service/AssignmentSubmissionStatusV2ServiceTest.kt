package com.example.aandi_post_web_server.assignment.v2.service

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.submission.repository.AssignmentSubmissionStatusProjectionRepository
import com.example.aandi_post_web_server.course.dtos.CourseResponse
import com.example.aandi_post_web_server.course.dtos.CourseMetadataResponse
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseStatus
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.service.CourseQueryService
import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.repository.ReportUserRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate

class AssignmentSubmissionStatusV2ServiceTest : StringSpec({
    val courseQueryService = Mockito.mock(CourseQueryService::class.java)
    val reportUserRepository = Mockito.mock(ReportUserRepository::class.java)
    val projectionRepository = Mockito.mock(AssignmentSubmissionStatusProjectionRepository::class.java)
    val service = AssignmentSubmissionStatusV2Service(
        courseQueryService = courseQueryService,
        reportUserRepository = reportUserRepository,
        projectionRepository = projectionRepository,
    )
    val assignmentId = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001"
    val userId = "user-1"
    val publicCode = "A00123"

    beforeTest {
        Mockito.reset(courseQueryService, reportUserRepository, projectionRepository)
        Mockito.`when`(courseQueryService.getAssignmentCourse(assignmentId, userId))
            .thenReturn(Mono.just(sampleCourseResponse()))
        Mockito.`when`(reportUserRepository.findById(userId))
            .thenReturn(Mono.just(sampleReportUser(userId, publicCode)))
    }

    "현재 사용자 projection 이 있으면 submitted=true 응답을 반환한다" {
        Mockito.`when`(projectionRepository.findByAssignmentIdAndPublicCode(assignmentId, publicCode))
            .thenReturn(
                Mono.just(
                    AssignmentSubmissionStatusProjection(
                        id = "projection-1",
                        assignmentId = assignmentId,
                        publicCode = publicCode,
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

        StepVerifier.create(service.getMySubmissionStatus(assignmentId, userId))
            .assertNext { response ->
                response.assignmentId shouldBe assignmentId
                response.submitted shouldBe true
                response.latestScore shouldBe 90
                response.passedCases shouldBe 9
                response.totalCases shouldBe 10
            }
            .verifyComplete()
    }

    "projection 이 없으면 submitted=false 응답을 반환한다" {
        Mockito.`when`(projectionRepository.findByAssignmentIdAndPublicCode(assignmentId, publicCode))
            .thenReturn(Mono.empty())

        StepVerifier.create(service.getMySubmissionStatus(assignmentId, userId))
            .assertNext { response ->
                response.assignmentId shouldBe assignmentId
                response.submitted shouldBe false
                response.firstCompletedAt shouldBe null
                response.latestScore shouldBe null
            }
            .verifyComplete()
    }
})

private fun sampleCourseResponse(): CourseResponse =
    CourseResponse(
        id = "course-1",
        slug = "back-basic",
        fieldTag = CourseTrack.FL,
        startDate = LocalDate.parse("2026-04-01"),
        endDate = LocalDate.parse("2026-04-30"),
        metadata = CourseMetadataResponse(
            title = "BACK 기초",
            description = "desc",
            phase = CoursePhase.BASIC,
            attributes = emptyMap(),
        ),
        status = CourseStatus.PUBLISHED,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-04-01T00:00:00Z"),
    )

private fun sampleReportUser(
    userId: String,
    publicCode: String,
): ReportUser =
    ReportUser(
        id = userId,
        publicCode = publicCode,
        username = "tester",
        role = "USER",
    )
