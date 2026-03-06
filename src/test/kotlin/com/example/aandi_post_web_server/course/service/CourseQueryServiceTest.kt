package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import com.example.aandi_post_web_server.assignment.enum.AssignmentDeliveryStatus
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.enum.UserTrack
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.course.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import org.mockito.Mockito
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class CourseQueryServiceTest : StringSpec({
    "관리자 코스 조회는 수강신청 여부와 관계없이 전체 코스를 반환한다" {
        val fixture = QueryFixture()
        val old = Instant.parse("2026-02-01T00:00:00Z")
        val latest = Instant.parse("2026-03-01T00:00:00Z")
        val flCourse = Course(
            id = "course-1",
            title = "FL 기초",
            slug = "fl-basic",
            targetTrack = CourseTrack.FL,
            createdAt = old,
            updatedAt = old,
        )
        val spCourse = Course(
            id = "course-2",
            title = "SP 기초",
            slug = "sp-basic",
            targetTrack = CourseTrack.SP,
            createdAt = latest,
            updatedAt = latest,
        )

        Mockito.`when`(fixture.courseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")))
            .thenReturn(Flux.just(flCourse, spCourse))

        StepVerifier.create(
            fixture.service.getAdminCourses().map { it.slug }.collectList()
        )
            .assertNext { slugs ->
                slugs.shouldContainExactlyInAnyOrder("sp-basic", "fl-basic")
            }
            .verifyComplete()
    }

    "코스 조회는 ENROLLED + track=FL 필터를 함께 적용한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val flCourse = Course(id = "course-1", title = "FL 기초", slug = "fl-basic", targetTrack = CourseTrack.FL)
        val spCourse = Course(id = "course-2", title = "SP 기초", slug = "sp-basic", targetTrack = CourseTrack.SP)
        val enrollments = listOf(
            CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId, status = EnrollmentStatus.ENROLLED),
            CourseEnrollment(id = "enroll-2", courseId = "course-2", userId = userId, status = EnrollmentStatus.ENROLLED),
        )

        Mockito.`when`(
            fixture.courseEnrollmentRepository.findAllByUserIdAndStatus(
                userId,
                EnrollmentStatus.ENROLLED,
            )
        ).thenReturn(Flux.fromIterable(enrollments))
        Mockito.`when`(fixture.courseRepository.findAllById(listOf("course-1", "course-2")))
            .thenReturn(Flux.just(flCourse, spCourse))

        StepVerifier.create(
            fixture.service.getCourses(
                status = null,
                phase = null,
                track = UserTrack.FL,
                userId = userId,
            ).map { it.slug }.collectList()
        )
            .assertNext { slugs ->
                slugs.shouldContainExactly("fl-basic")
            }
            .verifyComplete()
    }

    "코스 조회는 track=NO이면 빈 목록을 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"

        StepVerifier.create(
            fixture.service.getCourses(
                status = null,
                phase = null,
                track = UserTrack.NO,
                userId = userId,
            ).collectList()
        )
            .assertNext { courses ->
                courses.size shouldBe 0
            }
            .verifyComplete()
    }

    "과제 조회는 status 미지정 시 PUBLISHED만 조회한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = Course(id = "course-1", title = "BACK 기초", slug = "back-basic")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENROLLED,
        )
        val published = queryAssignment(id = "assignment-1", courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseIdAndStatus("course-1", AssignmentStatus.PUBLISHED))
            .thenReturn(Flux.just(published))

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = null,
                userId = userId,
            ).map { it.id }.collectList()
        )
            .assertNext { ids ->
                ids.shouldContainExactly("assignment-1")
            }
            .verifyComplete()
    }

    "과제 조회에서 DRAFT 상태 요청은 BAD_REQUEST를 반환한다" {
        val fixture = QueryFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"

        val error = shouldThrow<ResponseStatusException> {
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.DRAFT,
                userId = userId,
            )
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    "배포 조회는 deliveredAt 기준 내림차순 정렬된다" {
        val fixture = QueryFixture()
        val course = Course(id = "course-1", title = "BACK 기초", slug = "back-basic")
        val assignment = queryAssignment(id = "assignment-1", courseId = "course-1")
        val old = AssignmentDelivery(
            id = "d1",
            assignmentId = "assignment-1",
            userId = "user-1",
            status = AssignmentDeliveryStatus.DELIVERED,
            deliveredAt = Instant.parse("2026-03-01T00:00:00Z"),
        )
        val latest = AssignmentDelivery(
            id = "d2",
            assignmentId = "assignment-1",
            userId = "user-2",
            status = AssignmentDeliveryStatus.DELIVERED,
            deliveredAt = Instant.parse("2026-03-02T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentDeliveryRepository.findAllByAssignmentId("assignment-1"))
            .thenReturn(Flux.just(old, latest))

        StepVerifier.create(
            fixture.service.getDeliveries("back-basic", "assignment-1", null).map { it.userId }.collectList()
        )
            .assertNext { orderedUserIds ->
                orderedUserIds.shouldContainExactly("user-2", "user-1")
            }
            .verifyComplete()
    }

    "배포 조회는 status 필터가 있으면 필터 저장소 메서드를 사용한다" {
        val fixture = QueryFixture()
        val course = Course(id = "course-1", title = "BACK 기초", slug = "back-basic")
        val assignment = queryAssignment(id = "assignment-1", courseId = "course-1")
        val failed = AssignmentDelivery(
            id = "d3",
            assignmentId = "assignment-1",
            userId = "user-3",
            status = AssignmentDeliveryStatus.FAILED,
            deliveredAt = Instant.parse("2026-03-02T01:00:00Z"),
            failureReason = "network",
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(
            fixture.assignmentDeliveryRepository.findAllByAssignmentIdAndStatus(
                "assignment-1",
                AssignmentDeliveryStatus.FAILED,
            )
        ).thenReturn(Flux.just(failed))

        StepVerifier.create(
            fixture.service.getDeliveries("back-basic", "assignment-1", AssignmentDeliveryStatus.FAILED)
        )
            .assertNext {
                it.userId shouldBe "user-3"
                it.status shouldBe AssignmentDeliveryStatus.FAILED
            }
            .verifyComplete()
    }
})

private class QueryFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentExampleRepository: AssignmentExampleRepository = Mockito.mock(AssignmentExampleRepository::class.java)
    val assignmentDeliveryRepository: AssignmentDeliveryRepository = Mockito.mock(AssignmentDeliveryRepository::class.java)

    val service = CourseQueryService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentExampleRepository = assignmentExampleRepository,
        assignmentDeliveryRepository = assignmentDeliveryRepository,
    )
}

private fun queryAssignment(
    id: String,
    courseId: String,
): Assignment {
    val now = Instant.parse("2026-02-20T00:00:00Z")
    return Assignment(
        id = id,
        courseId = courseId,
        createdBy = "admin",
        weekNo = 1,
        seqInWeek = 1,
        title = "배포 조회 테스트 과제",
        difficulty = AssignmentDifficulty.MID,
        contentMd = "content",
        timeLimitMinutes = 60,
        openAt = now,
        dueAt = now.plusSeconds(3600),
        status = AssignmentStatus.PUBLISHED,
        createdAt = now,
        updatedAt = now,
        publishedAt = now,
    )
}
