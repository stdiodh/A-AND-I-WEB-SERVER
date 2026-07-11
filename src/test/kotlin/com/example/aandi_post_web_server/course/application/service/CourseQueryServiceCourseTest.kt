package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.course.application.service.CourseQueryServiceTestData.fixedNow
import com.example.aandi_post_web_server.course.application.service.CourseQueryServiceTestData.queryAssignment
import com.example.aandi_post_web_server.course.application.service.CourseQueryServiceTestData.queryAssignmentId
import com.example.aandi_post_web_server.course.application.service.CourseQueryServiceTestData.queryCourse
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseWeek
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate

class CourseQueryServiceCourseTest : StringSpec({
    "관리자 코스 조회는 수강신청 여부와 관계없이 전체 코스를 반환한다" {
        val fixture = CourseQueryServiceTestFixture()
        val old = Instant.parse("2026-02-01T00:00:00Z")
        val latest = Instant.parse("2026-03-01T00:00:00Z")
        val flCourse = queryCourse(id = "course-1", slug = "fl-basic", title = "FL 기초", fieldTag = CourseTrack.FL, createdAt = old, updatedAt = old)
        val spCourse = queryCourse(id = "course-2", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP, createdAt = latest, updatedAt = latest)

        Mockito.`when`(fixture.courseRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")))
            .thenReturn(Flux.just(spCourse, flCourse))

        StepVerifier.create(
            fixture.service.getAdminCourses().map { it.slug }.collectList()
        )
            .assertNext { slugs ->
                slugs.shouldContainExactly("sp-basic", "fl-basic")
            }
            .verifyComplete()
    }

    "코스 조회는 ENABLED 상태로 수강 중인 코스만 반환한다" {
        val fixture = CourseQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val flCourse = queryCourse(id = "course-1", slug = "fl-basic", title = "FL 기초", fieldTag = CourseTrack.FL)
        val spCourse = queryCourse(id = "course-2", slug = "sp-basic", title = "SP 기초", fieldTag = CourseTrack.SP)
        val enrollments = listOf(
            CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId, status = EnrollmentStatus.ENABLED),
            CourseEnrollment(id = "enroll-2", courseId = "course-2", userId = userId, status = EnrollmentStatus.ENABLED),
        )

        Mockito.`when`(
            fixture.courseEnrollmentRepository.findAllByUserIdAndStatus(
                userId,
                EnrollmentStatus.ENABLED,
            )
        ).thenReturn(Flux.fromIterable(enrollments))
        Mockito.`when`(fixture.courseRepository.findAllById(listOf("course-1", "course-2")))
            .thenReturn(Flux.just(flCourse, spCourse))

        StepVerifier.create(
            fixture.service.getCourses(userId).map { it.slug }.collectList()
        )
            .assertNext { slugs ->
                slugs.shouldContainExactly("fl-basic", "sp-basic")
            }
            .verifyComplete()
    }

    "코스 목차는 공개 과제만 주차와 순서로 정렬하고 종료 시각을 지난 과제만 완료 처리한다" {
        val fixture = CourseQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val now = fixedNow
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.ENABLED,
        )
        val week1Order1 = queryAssignment(queryAssignmentId(1), "course-1").copy(
            weekNo = 1,
            orderInWeek = 1,
            endAt = now.plusSeconds(1),
            metadata = queryAssignment(queryAssignmentId(1), "course-1").metadata.copy(title = "Hello"),
        )
        val week1Order2 = queryAssignment(queryAssignmentId(2), "course-1").copy(
            weekNo = 1,
            orderInWeek = 2,
            endAt = now.minusNanos(1),
            metadata = queryAssignment(queryAssignmentId(2), "course-1").metadata.copy(title = "OOP Calculator"),
        )
        val week2Order1 = queryAssignment(queryAssignmentId(3), "course-1").copy(
            weekNo = 2,
            orderInWeek = 1,
            endAt = now,
        )
        val draft = queryAssignment(queryAssignmentId(4), "course-1").copy(
            status = AssignmentStatus.DRAFT,
            publishedAt = null,
        )
        val futurePublished = queryAssignment(queryAssignmentId(5), "course-1").copy(
            startAt = now.plusSeconds(1),
            endAt = now.plusSeconds(3600),
            publishedAt = now.plusSeconds(1),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(week2Order1, futurePublished, draft, week1Order2, week1Order1))

        StepVerifier.create(fixture.service.getCourseOutline("back-basic", userId))
            .assertNext { outline ->
                outline.totalAssignments shouldBe 3
                outline.assignments.map { it.assignmentId }
                    .shouldContainExactly(week1Order1.id, week1Order2.id, week2Order1.id)
                outline.assignments.map { it.weekNo to it.orderInWeek }
                    .shouldContainExactly(1 to 1, 1 to 2, 2 to 1)
                outline.assignments.map { it.checked }.shouldContainExactly(false, true, false)
            }
            .verifyComplete()
    }

    "코스 상세 조회는 ENABLED 수강생에게 코스 응답을 반환한다" {
        val fixture = CourseQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(fixture.service.getCourse("back-basic", userId))
            .assertNext { response ->
                response.id shouldBe "course-1"
                response.slug shouldBe "back-basic"
                response.metadata.title shouldBe "BACK 기초"
            }
            .verifyComplete()
    }

    "코스 상세 조회는 비활성 수강 상태를 조회 불가로 처리한다" {
        val fixture = CourseQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = userId,
            status = EnrollmentStatus.BANNED,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(fixture.service.getCourse("back-basic", userId))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "조회 가능한 코스를 찾을 수 없습니다."
            }
            .verify()
    }

    "수강 신청 목록은 courseSlug를 포함하고 updatedAt 내림차순으로 반환한다" {
        val fixture = CourseQueryServiceTestFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val older = CourseEnrollment(
            id = "enroll-1",
            courseId = "course-1",
            userId = "user-1",
            publicCode = "#FL301",
            username = "older",
            updatedAt = Instant.parse("2026-03-02T00:00:00Z"),
        )
        val newer = CourseEnrollment(
            id = "enroll-2",
            courseId = "course-1",
            userId = "user-2",
            publicCode = "#FL302",
            username = "newer",
            bannedAt = Instant.parse("2026-03-03T00:00:00Z"),
            banReason = "policy",
            updatedAt = Instant.parse("2026-03-04T00:00:00Z"),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(older, newer))

        StepVerifier.create(fixture.service.getEnrollments("back-basic").collectList())
            .assertNext { enrollments ->
                enrollments.map { it.userId }.shouldContainExactly("user-2", "user-1")
                enrollments[0].courseSlug shouldBe "back-basic"
                enrollments[0].publicCode shouldBe "#FL302"
                enrollments[0].banReason shouldBe "policy"
            }
            .verifyComplete()
    }

    "주차 목록 조회는 수강 확인 후 weekNo 오름차순으로 반환한다" {
        val fixture = CourseQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)
        val week2 = CourseWeek(
            id = "week-2",
            courseId = "course-1",
            weekNo = 2,
            title = "2주차",
            startDate = LocalDate.of(2026, 3, 8),
            endDate = LocalDate.of(2026, 3, 14),
        )
        val week1 = CourseWeek(
            id = "week-1",
            courseId = "course-1",
            weekNo = 1,
            title = "1주차",
            startDate = LocalDate.of(2026, 3, 1),
            endDate = LocalDate.of(2026, 3, 7),
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))
        Mockito.`when`(fixture.courseWeekRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(week2, week1))

        StepVerifier.create(fixture.service.getWeeks("back-basic", userId).collectList())
            .assertNext { weeks ->
                weeks.map { it.weekNo }.shouldContainExactly(1, 2)
                weeks[0].id shouldBe "week-1"
                weeks[0].title shouldBe "1주차"
                weeks[0].startDate shouldBe LocalDate.of(2026, 3, 1)
            }
            .verifyComplete()
    }

    "과제 ID로 코스 조회는 과제 공개 여부와 수강 상태를 확인한다" {
        val fixture = CourseQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val assignment = queryAssignment(id = assignmentId, courseId = "course-1")
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollment = CourseEnrollment(id = "enroll-1", courseId = "course-1", userId = userId)

        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.courseRepository.findById("course-1")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
            .thenReturn(Mono.just(enrollment))

        StepVerifier.create(fixture.service.getAssignmentCourse(assignmentId, userId))
            .assertNext { response ->
                response.slug shouldBe "back-basic"
            }
            .verifyComplete()
    }

    "과제 ID로 코스 조회는 과제의 코스가 없으면 NOT_FOUND를 반환한다" {
        val fixture = CourseQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val assignment = queryAssignment(id = assignmentId, courseId = "missing-course")

        Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenReturn(Mono.just(assignment))
        Mockito.`when`(fixture.courseRepository.findById("missing-course")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.getAssignmentCourse(assignmentId, userId))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "코스를 찾을 수 없습니다: missing-course"
            }
            .verify()
    }

    "과제 ID로 코스 조회는 DRAFT와 미래 공개 과제를 정확한 과제 NOT_FOUND로 처리한다" {
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val hiddenAssignments = listOf(
            queryAssignment(queryAssignmentId(6), "course-1").copy(
                status = AssignmentStatus.DRAFT,
                publishedAt = null,
            ),
            queryAssignment(queryAssignmentId(7), "course-1").copy(
                startAt = fixedNow.plusSeconds(1),
                endAt = fixedNow.plusSeconds(3600),
                publishedAt = fixedNow.plusSeconds(1),
            ),
        )

        hiddenAssignments.forEach { assignment ->
            val fixture = CourseQueryServiceTestFixture()
            val assignmentId = requireNotNull(assignment.id)
            Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenReturn(Mono.just(assignment))

            StepVerifier.create(fixture.service.getAssignmentCourse(assignmentId, userId))
                .expectErrorSatisfies { error ->
                    (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                    error.reason shouldBe "과제를 찾을 수 없습니다: $assignmentId"
                }
                .verify()

            Mockito.verifyNoInteractions(fixture.courseRepository, fixture.courseEnrollmentRepository)
        }
    }

    "과제 ID로 코스 조회는 BANNED 또는 미수강 사용자에게 기존 조회 불가 NOT_FOUND를 반환한다" {
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = queryAssignmentId(8)
        val assignment = queryAssignment(assignmentId, "course-1")
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val enrollmentCases = listOf(
            CourseEnrollment(
                id = "enroll-banned",
                courseId = "course-1",
                userId = userId,
                status = EnrollmentStatus.BANNED,
            ),
            null,
        )

        enrollmentCases.forEach { enrollment ->
            val fixture = CourseQueryServiceTestFixture()
            Mockito.`when`(fixture.assignmentRepository.findById(assignmentId)).thenReturn(Mono.just(assignment))
            Mockito.`when`(fixture.courseRepository.findById("course-1")).thenReturn(Mono.just(course))
            Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId("course-1", userId))
                .thenReturn(if (enrollment == null) Mono.empty() else Mono.just(enrollment))

            StepVerifier.create(fixture.service.getAssignmentCourse(assignmentId, userId))
                .expectErrorSatisfies { error ->
                    (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                    error.reason shouldBe "조회 가능한 코스를 찾을 수 없습니다."
                }
                .verify()
        }
    }

    "수강 코스 조회는 수강 내역이 없으면 빈 목록을 반환한다" {
        val fixture = CourseQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"

        Mockito.`when`(fixture.courseEnrollmentRepository.findAllByUserIdAndStatus(userId, EnrollmentStatus.ENABLED))
            .thenReturn(Flux.empty())

        StepVerifier.create(fixture.service.getCourses(userId).collectList())
            .assertNext { courses -> courses shouldBe emptyList() }
            .verifyComplete()
    }
})
