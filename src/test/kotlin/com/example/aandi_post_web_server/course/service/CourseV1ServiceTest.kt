package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseValidator
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEvent
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventMapper
import com.example.aandi_post_web_server.assignment.infrastructure.event.AssignmentReportTestCaseEventPublisher
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.infrastructure.jackson.AssignmentMetadataPayloadTestCasePresenceTracker
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import com.example.aandi_post_web_server.user.infrastructure.repository.ReportUserRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate

class CourseV1ServiceTest : StringSpec({
    "ENABLED 사용자는 코스 과제를 조회할 수 있다" {
        val fixture = Fixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENABLED,
        )
        val visibleAssignment = assignment(
            id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            courseId = "course-1",
            weekNo = 1,
            status = AssignmentStatus.PUBLISHED,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(visibleAssignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            )
        )
            .assertNext {
                it.id shouldBe "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
                it.weekNo shouldBe 1
            }
            .verifyComplete()
    }

    "ENABLED 사용자는 delivery 정보가 없어도 과제 상세를 조회할 수 있다" {
        val fixture = Fixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENABLED,
        )
        val assignment = assignment(
            id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            courseId = "course-1",
            weekNo = 1,
            status = AssignmentStatus.PUBLISHED,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", "course-1")).thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAssignmentDetail(
                courseSlug = "back-basic",
                assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                userId = userId,
            )
        )
            .assertNext {
                it.id shouldBe "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
                it.courseSlug shouldBe "back-basic"
            }
            .verifyComplete()
    }

})

private class Fixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentExampleRepository: AssignmentExampleRepository = Mockito.mock(AssignmentExampleRepository::class.java)
    val assignmentReportTestCaseEventMapper = AssignmentReportTestCaseEventMapper()
    val assignmentReportTestCaseEventPublisher = NoopAssignmentReportTestCaseEventPublisherForTest()
    val reportUserRepository: ReportUserRepository = Mockito.mock(ReportUserRepository::class.java)
    val assignmentTestCaseValidator = AssignmentTestCaseValidator()
    val assignmentMetadataPayloadTestCasePresenceTracker = AssignmentMetadataPayloadTestCasePresenceTracker()
    private val courseEnrollmentCommandService = CourseEnrollmentCommandService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        reportUserRepository = reportUserRepository,
    )

    private val courseCommandService = CourseCommandService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentExampleRepository,
        assignmentDeliveryRepository = Mockito.mock(com.example.aandi_post_web_server.assignment.infrastructure.repository.AssignmentDeliveryRepository::class.java),
        assignmentReportTestCaseEventMapper = assignmentReportTestCaseEventMapper,
        assignmentReportTestCaseEventPublisher = assignmentReportTestCaseEventPublisher,
        courseEnrollmentCommandService = courseEnrollmentCommandService,
        assignmentTestCaseValidator = assignmentTestCaseValidator,
        assignmentMetadataPayloadTestCasePresenceTracker = assignmentMetadataPayloadTestCasePresenceTracker,
    )

    private val courseQueryService = CourseQueryService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentTestCaseRepository = assignmentExampleRepository,
    )

    val service = CourseV1Service(
        courseCommandService = courseCommandService,
        courseQueryService = courseQueryService,
    )

}

private class NoopAssignmentReportTestCaseEventPublisherForTest : AssignmentReportTestCaseEventPublisher {
    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> = Mono.empty()
}

private fun assignment(
    id: String,
    courseId: String,
    weekNo: Int,
    status: AssignmentStatus,
): Assignment {
    val now = Instant.parse("2026-02-20T00:00:00Z")
    return Assignment(
        id = id,
        courseId = courseId,
        createdBy = "admin",
        weekNo = weekNo,
        orderInWeek = 1,
        startAt = now,
        endAt = now.plusSeconds(3600),
        metadata = com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata(
            title = "테스트 과제",
            difficulty = AssignmentDifficulty.LOW,
            description = "content",
            timeLimitMinutes = 60,
        ),
        status = status,
        createdAt = now,
        updatedAt = now,
        publishedAt = if (status == AssignmentStatus.PUBLISHED) now else null,
    )
}

private fun queryCourse(id: String, slug: String, title: String): Course = Course(
    id = id,
    slug = slug,
    fieldTag = CourseTrack.FL,
    startDate = LocalDate.of(2026, 3, 1),
    endDate = LocalDate.of(2026, 3, 30),
    metadata = CourseMetadata(
        title = title,
        description = null,
        phase = null,
        attributes = emptyMap(),
    ),
)
