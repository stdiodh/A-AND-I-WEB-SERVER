package com.example.aandi_post_web_server.submission.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentCodeTemplate
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionAccepted
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionCreateRequest
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionOptions
import com.example.aandi_post_web_server.submission.client.JudgeSubmissionResult
import com.example.aandi_post_web_server.submission.client.OnlineJudgeClient
import com.example.aandi_post_web_server.submission.dtos.CreateAssignmentSubmissionRequest
import com.example.aandi_post_web_server.submission.entity.AssignmentSubmission
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionLanguage
import com.example.aandi_post_web_server.submission.enum.AssignmentSubmissionStatus
import com.example.aandi_post_web_server.submission.repository.AssignmentSubmissionRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AssignmentSubmissionServiceTest : StringSpec({
    "과제 제출 생성은 Judge 접수 후 로컬 제출 이력을 저장한다" {
        val fixture = Fixture()
        val request = CreateAssignmentSubmissionRequest(
            language = AssignmentSubmissionLanguage.KOTLIN,
            code = "fun solution(input: String): String = input",
            realtimeFeedback = true,
        )
        val assignment = fixture.visibleAssignment()

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(fixture.course()))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(fixture.enabledEnrollment()))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.just(fixture.example()))
        Mockito.`when`(
            fixture.onlineJudgeClient.createSubmission(
                "Bearer token",
                JudgeSubmissionCreateRequest(
                    problemId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                    language = AssignmentSubmissionLanguage.KOTLIN,
                    code = "fun solution(input: String): String = input",
                    options = JudgeSubmissionOptions(realtimeFeedback = true),
                )
            )
        ).thenReturn(
            Mono.just(
                JudgeSubmissionAccepted(
                    submissionId = "judge-sub-1",
                    streamUrl = "/v1/submissions/judge-sub-1/stream",
                )
            )
        )
        val expectedSavedSubmission = AssignmentSubmission(
            id = "judge-sub-1",
            assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            courseId = "course-1",
            courseSlug = "back-basic",
            userId = "user-1",
            language = AssignmentSubmissionLanguage.KOTLIN,
            status = AssignmentSubmissionStatus.PENDING,
            realtimeFeedback = true,
            createdAt = Instant.parse("2026-03-15T12:00:00Z"),
            updatedAt = Instant.parse("2026-03-15T12:00:00Z"),
        )
        Mockito.`when`(fixture.assignmentSubmissionRepository.save(expectedSavedSubmission))
            .thenReturn(Mono.just(expectedSavedSubmission))

        StepVerifier.create(
            fixture.service.createSubmission(
                courseSlug = "back-basic",
                assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                userId = "user-1",
                authorizationHeader = "Bearer token",
                request = request,
            )
        )
            .assertNext { response ->
                response.submissionId shouldBe "judge-sub-1"
                response.assignmentId shouldBe "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
                response.status shouldBe AssignmentSubmissionStatus.PENDING
            }
            .verifyComplete()
    }

    "과제 제출 결과 조회는 아직 완료되지 않았으면 PENDING을 반환한다" {
        val fixture = Fixture()
        val submission = AssignmentSubmission(
            id = "judge-sub-1",
            assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            courseId = "course-1",
            courseSlug = "back-basic",
            userId = "user-1",
            language = AssignmentSubmissionLanguage.KOTLIN,
            status = AssignmentSubmissionStatus.PENDING,
            createdAt = Instant.parse("2026-03-15T03:00:00Z"),
            updatedAt = Instant.parse("2026-03-15T03:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(fixture.course()))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(fixture.enabledEnrollment()))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", "course-1"))
            .thenReturn(Mono.just(fixture.visibleAssignment()))
        Mockito.`when`(fixture.assignmentSubmissionRepository.findByIdAndAssignmentIdAndUserId("judge-sub-1", "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", "user-1"))
            .thenReturn(Mono.just(submission))
        Mockito.`when`(fixture.onlineJudgeClient.getSubmissionResult("Bearer token", "judge-sub-1"))
            .thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.getSubmissionResult(
                courseSlug = "back-basic",
                assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                submissionId = "judge-sub-1",
                userId = "user-1",
                authorizationHeader = "Bearer token",
            )
        )
            .assertNext { response ->
                response.submissionId shouldBe "judge-sub-1"
                response.status shouldBe AssignmentSubmissionStatus.PENDING
                response.testCases shouldBe emptyList()
            }
            .verifyComplete()
    }

    "과제 제출 결과 조회는 완료된 결과를 저장하고 반환한다" {
        val fixture = Fixture()
        val submission = AssignmentSubmission(
            id = "judge-sub-1",
            assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            courseId = "course-1",
            courseSlug = "back-basic",
            userId = "user-1",
            language = AssignmentSubmissionLanguage.KOTLIN,
            status = AssignmentSubmissionStatus.PENDING,
            createdAt = Instant.parse("2026-03-15T03:00:00Z"),
            updatedAt = Instant.parse("2026-03-15T03:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(fixture.course()))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(fixture.enabledEnrollment()))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", "course-1"))
            .thenReturn(Mono.just(fixture.visibleAssignment()))
        Mockito.`when`(fixture.assignmentSubmissionRepository.findByIdAndAssignmentIdAndUserId("judge-sub-1", "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", "user-1"))
            .thenReturn(Mono.just(submission))
        Mockito.`when`(fixture.onlineJudgeClient.getSubmissionResult("Bearer token", "judge-sub-1"))
            .thenReturn(
                Mono.just(
                    JudgeSubmissionResult(
                        submissionId = "judge-sub-1",
                        status = AssignmentSubmissionStatus.ACCEPTED,
                        testCases = emptyList(),
                    )
                )
            )
        val expectedUpdatedSubmission = submission.copy(
            status = AssignmentSubmissionStatus.ACCEPTED,
            updatedAt = Instant.parse("2026-03-15T12:00:00Z"),
            completedAt = Instant.parse("2026-03-15T12:00:00Z"),
        )
        Mockito.`when`(fixture.assignmentSubmissionRepository.save(expectedUpdatedSubmission))
            .thenReturn(Mono.just(expectedUpdatedSubmission))

        StepVerifier.create(
            fixture.service.getSubmissionResult(
                courseSlug = "back-basic",
                assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                submissionId = "judge-sub-1",
                userId = "user-1",
                authorizationHeader = "Bearer token",
            )
        )
            .assertNext { response ->
                response.status shouldBe AssignmentSubmissionStatus.ACCEPTED
                response.completedAt shouldBe Instant.parse("2026-03-15T12:00:00Z")
            }
            .verifyComplete()
    }
})

private class Fixture {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-03-15T12:00:00Z"), ZoneOffset.UTC)
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentExampleRepository: AssignmentExampleRepository = Mockito.mock(AssignmentExampleRepository::class.java)
    val assignmentSubmissionRepository: AssignmentSubmissionRepository = Mockito.mock(AssignmentSubmissionRepository::class.java)
    val onlineJudgeClient: OnlineJudgeClient = Mockito.mock(OnlineJudgeClient::class.java)

    val service = AssignmentSubmissionService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        assignmentRepository = assignmentRepository,
        assignmentExampleRepository = assignmentExampleRepository,
        assignmentSubmissionRepository = assignmentSubmissionRepository,
        onlineJudgeClient = onlineJudgeClient,
        clock = clock,
    )

    fun course(): Course = Course(
        id = "course-1",
        slug = "back-basic",
        fieldTag = CourseTrack.FL,
        startDate = LocalDate.parse("2026-03-01"),
        endDate = LocalDate.parse("2026-03-30"),
        metadata = CourseMetadata(title = "백엔드 기초"),
    )

    fun enabledEnrollment(): CourseEnrollment = CourseEnrollment(
        id = "enrollment-1",
        courseId = "course-1",
        userId = "user-1",
        publicCode = "FL301",
        username = "tester",
        status = EnrollmentStatus.ENABLED,
        joinedAt = Instant.parse("2026-03-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-01T00:00:00Z"),
    )

    fun visibleAssignment(): Assignment = Assignment(
        id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
        courseId = "course-1",
        courseSlug = "back-basic",
        createdBy = "admin",
        weekNo = 1,
        orderInWeek = 1,
        startAt = Instant.parse("2026-03-14T00:00:00Z"),
        endAt = Instant.parse("2026-03-16T00:00:00Z"),
        metadata = metadata(),
        status = AssignmentStatus.PUBLISHED,
        createdAt = Instant.parse("2026-03-14T00:00:00Z"),
        updatedAt = Instant.parse("2026-03-14T00:00:00Z"),
        publishedAt = Instant.parse("2026-03-14T00:00:00Z"),
    )

    fun closedAssignment(): Assignment = visibleAssignment().copy(
        startAt = Instant.parse("2026-03-10T00:00:00Z"),
        endAt = Instant.parse("2026-03-14T23:59:59Z"),
    )

    fun example(): com.example.aandi_post_web_server.assignment.entity.AssignmentExample =
        com.example.aandi_post_web_server.assignment.entity.AssignmentExample(
            id = "example-1",
            assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            seq = 1,
            inputText = "1 2",
            outputText = "3",
            createdAt = Instant.parse("2026-03-14T00:00:00Z"),
        )

    private fun metadata(): AssignmentMetadata = AssignmentMetadata(
        title = "합 구하기",
        difficulty = AssignmentDifficulty.LOW,
        description = "문제 설명",
        timeLimitMinutes = 60,
        codeTemplates = listOf(
            AssignmentCodeTemplate(
                language = AssignmentTemplateLanguage.KOTLIN,
                commentTemplate = "/* */",
                functionTemplate = "fun solution(input: String): String",
                runnableTemplate = "fun solution(input: String): String = input",
            )
        ),
    )
}
