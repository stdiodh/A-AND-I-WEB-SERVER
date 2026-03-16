package com.example.aandi_post_web_server.submission.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionAccepted
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionCreateRequest
import com.example.aandi_post_web_server.submission.client.OnlineJudgeClient
import com.example.aandi_post_web_server.submission.dtos.CreateAssignmentSubmissionRequest
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionLanguage
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AssignmentSubmissionServiceTest : StringSpec({
    "제출 생성은 OJ /v1/submissions 호출로 위임하고 submissionId와 streamUrl을 반환한다" {
        val now = Instant.parse("2026-03-16T00:00:00Z")
        val fixture = SubmissionFixture(now)
        val assignmentId = "11111111-1111-1111-1111-111111111111"

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(submissionCourse()))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(submissionEnrollment()))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(submissionAssignment(assignmentId, now.minusSeconds(60))))
        val judgeRequest = JudgeSubmissionCreateRequest(
            publicCode = "A00123",
            problemId = assignmentId,
            language = AssignmentSubmissionLanguage.KOTLIN,
            code = "fun solution(input: String): String = input",
        )
        Mockito.`when`(
            fixture.onlineJudgeClient.createSubmission(
                "Bearer token",
                judgeRequest,
            )
        ).thenReturn(
            Mono.just(
                JudgeSubmissionAccepted(
                    submissionId = "judge-sub-1",
                    streamUrl = "/v1/submissions/judge-sub-1/stream",
                )
            )
        )

        StepVerifier.create(
            fixture.service.createSubmission(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                userId = "user-1",
                publicCode = "A00123",
                authorizationHeader = "Bearer token",
                request = CreateAssignmentSubmissionRequest(
                    language = AssignmentSubmissionLanguage.KOTLIN,
                    code = "fun solution(input: String): String = input",
                    realtimeFeedback = true,
                ),
            )
        )
            .assertNext { response ->
                response.submissionId shouldBe "judge-sub-1"
                response.streamUrl shouldBe "/v1/submissions/judge-sub-1/stream"
            }
            .verifyComplete()

        val request = createdJudgeRequests(fixture).single()
        request.publicCode shouldBe "A00123"
        request.problemId shouldBe assignmentId
        request.language shouldBe AssignmentSubmissionLanguage.KOTLIN
        request.options.realtimeFeedback shouldBe true
    }

    "공개 시작 전 과제는 제출 생성 시 OJ 호출 없이 NOT_FOUND를 반환한다" {
        val now = Instant.parse("2026-03-16T00:00:00Z")
        val fixture = SubmissionFixture(now)
        val assignmentId = "11111111-1111-1111-1111-111111111111"

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(submissionCourse()))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(submissionEnrollment()))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(submissionAssignment(assignmentId, now.plusSeconds(60))))

        StepVerifier.create(
            fixture.service.createSubmission(
                courseSlug = "back-basic",
                assignmentId = assignmentId,
                userId = "user-1",
                publicCode = "A00123",
                authorizationHeader = "Bearer token",
                request = CreateAssignmentSubmissionRequest(
                    language = AssignmentSubmissionLanguage.KOTLIN,
                    code = "fun solution(input: String): String = input",
                ),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
            }
            .verify()

        Mockito.verifyNoInteractions(fixture.onlineJudgeClient)
    }
})

private class SubmissionFixture(now: Instant) {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val onlineJudgeClient: OnlineJudgeClient = Mockito.mock(OnlineJudgeClient::class.java)
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    val service = AssignmentSubmissionService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        assignmentRepository = assignmentRepository,
        onlineJudgeClient = onlineJudgeClient,
        clock = clock,
    )
}

private fun submissionCourse(): Course = Course(
    id = "course-1",
    slug = "back-basic",
    fieldTag = CourseTrack.FL,
    startDate = LocalDate.of(2026, 3, 1),
    endDate = LocalDate.of(2026, 3, 30),
    metadata = CourseMetadata(
        title = "BACK 기초",
        description = null,
        phase = CoursePhase.BASIC,
        attributes = emptyMap(),
    ),
)

private fun submissionEnrollment(): CourseEnrollment = CourseEnrollment(
    id = "enroll-1",
    courseId = "course-1",
    userId = "user-1",
    status = EnrollmentStatus.ENABLED,
    joinedAt = Instant.parse("2026-03-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
)

private fun submissionAssignment(assignmentId: String, startAt: Instant): Assignment = Assignment(
    id = assignmentId,
    courseId = "course-1",
    courseSlug = "back-basic",
    createdBy = "admin",
    weekNo = 1,
    orderInWeek = 1,
    startAt = startAt,
    endAt = startAt.plusSeconds(3600),
    metadata = AssignmentMetadata(
        title = "터미널 계산기",
        difficulty = AssignmentDifficulty.MID,
        description = "문제 설명",
        timeLimitMinutes = 60,
    ),
    status = AssignmentStatus.PUBLISHED,
    createdAt = Instant.parse("2026-03-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
    publishedAt = Instant.parse("2026-03-01T00:00:00Z"),
)

@Suppress("UNCHECKED_CAST")
private fun createdJudgeRequests(fixture: SubmissionFixture): List<JudgeSubmissionCreateRequest> =
    Mockito.mockingDetails(fixture.onlineJudgeClient).invocations
        .filter { it.method.name == "createSubmission" }
        .map { it.arguments[1] as JudgeSubmissionCreateRequest }
