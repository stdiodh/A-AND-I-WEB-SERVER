package com.example.aandi_post_web_server.course.service

import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentDelivery
import com.example.aandi_post_web_server.assignment.dtos.AssignmentMetadataPayload
import com.example.aandi_post_web_server.assignment.dtos.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.dtos.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import com.example.aandi_post_web_server.assignment.repository.AssignmentDeliveryRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentExampleRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRepository
import com.example.aandi_post_web_server.assignment.repository.AssignmentRequirementRepository
import com.example.aandi_post_web_server.course.dtos.CreateCourseRequest
import com.example.aandi_post_web_server.course.dtos.CreateCourseWeekRequest
import com.example.aandi_post_web_server.course.dtos.CourseMetadataPayload
import com.example.aandi_post_web_server.course.dtos.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.enum.CoursePhase
import com.example.aandi_post_web_server.course.enum.CourseTrack
import com.example.aandi_post_web_server.course.enum.EnrollmentStatus
import com.example.aandi_post_web_server.course.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.repository.CourseRepository
import com.example.aandi_post_web_server.course.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate

class CourseCommandServiceTest : StringSpec({
    "중복 slug 코스 생성은 CONFLICT를 반환한다" {
        val fixture = CommandFixture()
        Mockito.`when`(fixture.courseRepository.existsBySlug("back-basic")).thenReturn(Mono.just(true))

        StepVerifier.create(
            fixture.service.createCourse(
                CreateCourseRequest(
                    slug = "back-basic",
                    fieldTag = CourseTrack.FL,
                    startDate = LocalDate.parse("2026-03-01"),
                    endDate = LocalDate.parse("2026-03-28"),
                    metadata = CourseMetadataPayload(
                        title = "BACK 기초",
                        description = "desc",
                        phase = CoursePhase.BASIC,
                    ),
                )
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.CONFLICT
            }
            .verify()
    }

    "코스 삭제는 코스 연관 데이터를 하드 삭제한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignments = listOf(
            commandAssignment(id = "assignment-1", courseId = "course-1", status = AssignmentStatus.PUBLISHED),
            commandAssignment(id = "assignment-2", courseId = "course-1", status = AssignmentStatus.DRAFT),
        )
        val assignmentIds = assignments.mapNotNull { it.id }

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1")).thenReturn(Flux.fromIterable(assignments))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(2))
        Mockito.`when`(fixture.assignmentExampleRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(2))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(assignmentIds)).thenReturn(Mono.just(4))
        Mockito.`when`(fixture.assignmentRepository.deleteAllById(assignmentIds)).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.deleteAllByCourseId("course-1")).thenReturn(Mono.just(3))
        Mockito.`when`(fixture.courseEnrollmentRepository.deleteAllByCourseId("course-1")).thenReturn(Mono.just(5))
        Mockito.`when`(fixture.courseRepository.deleteById("course-1")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteCourse("back-basic"))
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentExampleRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentDeliveryRepository).deleteAllByAssignmentIdIn(assignmentIds)
        Mockito.verify(fixture.assignmentRepository).deleteAllById(assignmentIds)
        Mockito.verify(fixture.courseWeekRepository).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseEnrollmentRepository).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseRepository).deleteById("course-1")
    }

    "과제 삭제는 과제 연관 데이터를 하드 삭제한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignment = commandAssignment(id = "assignment-1", courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.assignmentRequirementRepository.deleteAllByAssignmentIdIn(listOf("assignment-1"))).thenReturn(Mono.just(1))
        Mockito.`when`(fixture.assignmentExampleRepository.deleteAllByAssignmentIdIn(listOf("assignment-1"))).thenReturn(Mono.just(1))
        Mockito.`when`(fixture.assignmentDeliveryRepository.deleteAllByAssignmentIdIn(listOf("assignment-1"))).thenReturn(Mono.just(0))
        Mockito.`when`(fixture.assignmentRepository.deleteById("assignment-1")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteAssignment("back-basic", "assignment-1"))
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.assignmentExampleRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.assignmentDeliveryRepository).deleteAllByAssignmentIdIn(listOf("assignment-1"))
        Mockito.verify(fixture.assignmentRepository).deleteById("assignment-1")
    }

    "과제 수정은 전달된 필드를 반영해 저장한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val target = commandAssignment(id = "assignment-1", courseId = "course-1", status = AssignmentStatus.DRAFT)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(target))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        )
            .thenReturn(
                Mono.just(
                    CourseWeek(
                        id = "week-2",
                        courseId = "course-1",
                        weekNo = 2,
                        title = "2주차",
                    )
                )
            )
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        )
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.assignmentRepository.save(ArgumentMatchers.any(Assignment::class.java)))
            .thenAnswer { invocation ->
                val assignment = invocation.arguments[0] as Assignment
                Mono.just(assignment)
            }
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder("assignment-1"))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentExampleRepository.findAllByAssignmentIdOrderBySeq("assignment-1"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.updateAssignment(
                courseSlug = "back-basic",
                assignmentId = "assignment-1",
                request = UpdateAssignmentRequest(
                    weekNo = 2,
                    orderInWeek = 2,
                ),
            )
        )
            .assertNext { updated ->
                updated.id shouldBe "assignment-1"
                updated.weekNo shouldBe 2
                updated.orderInWeek shouldBe 2
            }
            .verifyComplete()
    }

    "BANNED 상태 변경은 banReason이 필수다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = "user-1",
            status = EnrollmentStatus.ENROLLED,
            joinedAt = Instant.parse("2026-02-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", "user-1"))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(
            fixture.service.updateEnrollmentStatus(
                courseSlug = "back-basic",
                userId = "user-1",
                request = UpdateEnrollmentRequest(
                    status = EnrollmentStatus.BANNED,
                    banReason = null,
                ),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
            }
            .verify()
    }

    "이미 PUBLISHED 과제를 게시하면 기존 publishedAt을 유지한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val alreadyPublishedAt = Instant.parse("2026-03-01T00:00:00Z")
        val published = commandAssignment(
            id = "assignment-1",
            courseId = "course-1",
            status = AssignmentStatus.PUBLISHED,
        ).copy(publishedAt = alreadyPublishedAt)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(published))

        StepVerifier.create(
            fixture.service.publishAssignment("back-basic", "assignment-1")
        )
            .assertNext {
                it.assignmentId shouldBe "assignment-1"
                it.status shouldBe AssignmentStatus.PUBLISHED
                it.publishedAt shouldBe alreadyPublishedAt
            }
            .verifyComplete()
    }

    "주차 생성은 weekNo가 1 미만이면 BAD_REQUEST를 반환한다" {
        val fixture = CommandFixture()

        val error = shouldThrow<ResponseStatusException> {
            fixture.service.createWeek(
                courseSlug = "back-basic",
                request = CreateCourseWeekRequest(
                    weekNo = 0,
                    title = "0주차",
                ),
            )
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    "주차 생성은 endDate가 startDate보다 빠르면 BAD_REQUEST를 반환한다" {
        val fixture = CommandFixture()

        StepVerifier.create(
            fixture.service.createWeek(
                courseSlug = "back-basic",
                request = CreateCourseWeekRequest(
                    weekNo = 1,
                    title = "1주차",
                    startDate = LocalDate.of(2026, 3, 10),
                    endDate = LocalDate.of(2026, 3, 9),
                ),
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.BAD_REQUEST
            }
            .verify()
    }

    "주차 업서트는 기존 주차를 수정한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val existing = CourseWeek(
            id = "week-1",
            courseId = "course-1",
            weekNo = 1,
            title = "기존 1주차",
            startDate = LocalDate.of(2026, 3, 1),
            endDate = LocalDate.of(2026, 3, 7),
            createdAt = Instant.parse("2026-02-20T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-20T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo("course-1", 1)).thenReturn(Mono.just(existing))
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation ->
                val week = invocation.arguments[0] as CourseWeek
                Mono.just(week)
            }

        StepVerifier.create(
            fixture.service.createWeek(
                courseSlug = "back-basic",
                request = CreateCourseWeekRequest(
                    weekNo = 1,
                    title = "수정된 1주차",
                    startDate = LocalDate.of(2026, 3, 2),
                    endDate = LocalDate.of(2026, 3, 8),
                ),
            )
        )
            .assertNext {
                it.id shouldBe "week-1"
                it.title shouldBe "수정된 1주차"
                it.startDate shouldBe LocalDate.of(2026, 3, 2)
                it.endDate shouldBe LocalDate.of(2026, 3, 8)
            }
            .verifyComplete()
    }

    "주차가 없으면 과제 생성은 NOT_FOUND를 반환한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val request = CreateAssignmentRequest(
            weekNo = 2,
            orderInWeek = 1,
            startAt = Instant.parse("2026-03-10T00:00:00Z"),
            endAt = Instant.parse("2026-03-11T00:00:00Z"),
            metadata = AssignmentMetadataPayload(
                title = "터미널 계산기",
                difficulty = AssignmentDifficulty.MID,
                description = "문제 설명",
                timeLimitMinutes = 60,
            ),
            requirements = emptyList(),
            examples = emptyList(),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(
            fixture.courseWeekRepository.findByCourseIdAndWeekNo(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(
            fixture.assignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(Mono.empty())

        StepVerifier.create(
            fixture.service.createAssignment(
                courseSlug = "back-basic",
                request = request,
                createdBy = "admin",
            )
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .findByCourseIdAndWeekNoAndOrderInWeek(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt(),
            )
    }

    "DRAFT 과제는 배포 트리거할 수 없다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val draft = commandAssignment(
            id = "assignment-draft",
            courseId = "course-1",
            status = AssignmentStatus.DRAFT,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-draft", "course-1"))
            .thenReturn(Mono.just(draft))

        StepVerifier.create(
            fixture.service.triggerDeliveries("back-basic", "assignment-draft")
        )
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.UNPROCESSABLE_ENTITY
            }
            .verify()
    }

    "배포 트리거는 ENROLLED 대상만 DELIVERED 처리한다" {
        val fixture = CommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val published = commandAssignment(
            id = "assignment-1",
            courseId = "course-1",
            status = AssignmentStatus.PUBLISHED,
        )
        val enrollments = listOf(
            CourseEnrollment(courseId = "course-1", userId = "user-1", status = EnrollmentStatus.ENROLLED),
            CourseEnrollment(courseId = "course-1", userId = "user-2", status = EnrollmentStatus.ENROLLED),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId("assignment-1", "course-1"))
            .thenReturn(Mono.just(published))
        Mockito.`when`(fixture.courseEnrollmentRepository.findAllByCourseIdAndStatus("course-1", EnrollmentStatus.ENROLLED))
            .thenReturn(Flux.fromIterable(enrollments))
        Mockito.`when`(
            fixture.assignmentDeliveryRepository.findByAssignmentIdAndUserId(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.anyString(),
            )
        ).thenReturn(Mono.empty())
        Mockito.`when`(fixture.assignmentDeliveryRepository.save(ArgumentMatchers.any(AssignmentDelivery::class.java)))
            .thenAnswer { invocation ->
                val delivery = invocation.arguments[0] as AssignmentDelivery
                Mono.just(delivery.copy(id = "${delivery.userId}-delivery"))
            }

        StepVerifier.create(
            fixture.service.triggerDeliveries("back-basic", "assignment-1")
        )
            .assertNext {
                it.targetCount shouldBe 2
                it.deliveredCount shouldBe 2
                it.failedCount shouldBe 0
            }
            .verifyComplete()
    }
})

private class CommandFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val assignmentRepository: AssignmentRepository = Mockito.mock(AssignmentRepository::class.java)
    val assignmentRequirementRepository: AssignmentRequirementRepository = Mockito.mock(AssignmentRequirementRepository::class.java)
    val assignmentExampleRepository: AssignmentExampleRepository = Mockito.mock(AssignmentExampleRepository::class.java)
    val assignmentDeliveryRepository: AssignmentDeliveryRepository = Mockito.mock(AssignmentDeliveryRepository::class.java)

    val service = CourseCommandService(
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
        assignmentRepository = assignmentRepository,
        assignmentRequirementRepository = assignmentRequirementRepository,
        assignmentExampleRepository = assignmentExampleRepository,
        assignmentDeliveryRepository = assignmentDeliveryRepository,
    )
}

private fun commandAssignment(
    id: String,
    courseId: String,
    status: AssignmentStatus,
): Assignment {
    val now = Instant.parse("2026-02-20T00:00:00Z")
    return Assignment(
        id = id,
        courseId = courseId,
        createdBy = "admin",
        weekNo = 1,
        orderInWeek = 1,
        startAt = now,
        endAt = now.plusSeconds(3600),
        metadata = com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata(
            title = "테스트 과제",
            difficulty = AssignmentDifficulty.MID,
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
        phase = CoursePhase.BASIC,
        attributes = emptyMap(),
    ),
)
