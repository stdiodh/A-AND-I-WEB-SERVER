package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.dtos.CreateCourseWeekRequest
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.course.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate

class CourseV1ServiceTest : StringSpec({
    "ENROLLED 사용자는 코스 과제를 조회할 수 있다" {
        val fixture = Fixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENROLLED,
        )
        val visibleAssignment = assignment(id = "a-1", courseId = "course-1", weekNo = 1, status = AssignmentStatus.PUBLISHED)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseIdAndStatus("course-1", AssignmentStatus.PUBLISHED))
            .thenReturn(Flux.just(visibleAssignment))

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            )
        )
            .assertNext {
                it.id shouldBe "a-1"
                it.weekNo shouldBe 1
            }
            .verifyComplete()
    }

    "ENROLLED 사용자는 delivery 정보가 없어도 과제 상세를 조회할 수 있다" {
        val fixture = Fixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENROLLED,
        )
        val assignment = assignment(id = "a-1", courseId = "course-1", weekNo = 1, status = AssignmentStatus.PUBLISHED)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("a-1", "course-1")).thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("a-1"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("a-1"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAssignmentDetail(
                courseSlug = "back-basic",
                assignmentId = "a-1",
                userId = userId,
            )
        )
            .assertNext {
                it.id shouldBe "a-1"
                it.courseSlug shouldBe "back-basic"
            }
            .verifyComplete()
    }

    "코스 주차 생성 API는 신규 주차를 저장한다" {
        val fixture = Fixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", 1)).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation ->
                val week = invocation.arguments[0] as CourseWeek
                Mono.just(week.copy(id = "week-1"))
            }

        StepVerifier.create(
            fixture.service.createWeek(
                courseSlug = "back-basic",
                request = CreateCourseWeekRequest(
                    weekNo = 1,
                    title = "1주차 - 기본 문법",
                    startDate = LocalDate.of(2026, 3, 2),
                    endDate = LocalDate.of(2026, 3, 8),
                ),
            )
        )
            .assertNext {
                it.id shouldBe "week-1"
                it.weekNo shouldBe 1
                it.title shouldBe "1주차 - 기본 문법"
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
    val assignmentDeliveryRepository: AssignmentDeliveryRepository = Mockito.mock(AssignmentDeliveryRepository::class.java)

    private val courseCommandService = CourseCommandService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentExampleRepository = assignmentExampleRepository,
        assignmentDeliveryRepository = assignmentDeliveryRepository,
    )

    private val courseQueryService = CourseQueryService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentExampleRepository = assignmentExampleRepository,
        assignmentDeliveryRepository = assignmentDeliveryRepository,
    )

    val service = CourseV1Service(
        courseCommandService = courseCommandService,
        courseQueryService = courseQueryService,
    )
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
