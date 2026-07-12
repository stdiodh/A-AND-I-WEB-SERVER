package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentDetailResponse
import com.example.aandi_post_web_server.assignment.api.dto.CopyAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.CreateAssignmentRequest
import com.example.aandi_post_web_server.assignment.api.dto.UpdateAssignmentRequest
import com.example.aandi_post_web_server.assignment.application.service.AssignmentCommandService
import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataPayload
import com.example.aandi_post_web_server.course.api.dto.EnrollCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateEnrollmentRequest
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseEnrollmentStore
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseStore
import com.example.aandi_post_web_server.course.infrastructure.adapter.RepositoryCourseWeekStore
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import reactor.test.publisher.TestPublisher
import java.time.LocalDate

class CourseCommandServiceTest : StringSpec({
    "중복 slug 코스 생성은 CONFLICT를 반환한다" {
        val fixture = CourseCommandFixture()
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

    "코스 생성 중 slug unique 충돌이 발생하면 CONFLICT를 반환한다" {
        val fixture = CourseCommandFixture()
        val duplicate = DuplicateKeyException("course slug race")
        val request = CreateCourseRequest(
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
        Mockito.`when`(fixture.courseRepository.existsBySlug("back-basic")).thenReturn(Mono.just(false))
        Mockito.`when`(fixture.courseRepository.save(ArgumentMatchers.any(Course::class.java)))
            .thenReturn(Mono.error(duplicate))

        StepVerifier.create(fixture.service.createCourse(request))
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.CONFLICT
                exception.reason shouldBe "이미 존재하는 코스 slug입니다: back-basic"
                exception.cause shouldBe duplicate
            }
            .verify()
    }

    "코스 삭제는 코스 연관 데이터를 하드 삭제한다" {
        val fixture = CourseCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentCommandService.deleteAllByCourseId("course-1")).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.deleteAllByCourseId("course-1")).thenReturn(Mono.just(3))
        Mockito.`when`(fixture.courseEnrollmentRepository.deleteAllByCourseId("course-1")).thenReturn(Mono.just(5))
        Mockito.`when`(fixture.courseRepository.deleteById("course-1")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteCourse("back-basic"))
            .verifyComplete()

        Mockito.verify(fixture.assignmentCommandService).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseWeekRepository).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseEnrollmentRepository).deleteAllByCourseId("course-1")
        Mockito.verify(fixture.courseRepository).deleteById("course-1")
    }

    "코스 삭제는 과제 삭제 완료 후 코스 관계를 삭제한다" {
        val fixture = CourseCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentDeletePublisher = TestPublisher.create<Void>()
        val weekDeletePublisher = TestPublisher.create<Long>()
        val enrollmentDeletePublisher = TestPublisher.create<Long>()
        val courseDeletePublisher = TestPublisher.create<Void>()

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentCommandService.deleteAllByCourseId("course-1"))
            .thenReturn(assignmentDeletePublisher.mono())
        Mockito.`when`(fixture.courseWeekRepository.deleteAllByCourseId("course-1"))
            .thenReturn(weekDeletePublisher.mono())
        Mockito.`when`(fixture.courseEnrollmentRepository.deleteAllByCourseId("course-1"))
            .thenReturn(enrollmentDeletePublisher.mono())
        Mockito.`when`(fixture.courseRepository.deleteById("course-1"))
            .thenReturn(courseDeletePublisher.mono())

        StepVerifier.create(fixture.service.deleteCourse("back-basic"))
            .then {
                assignmentDeletePublisher.assertSubscribers(1)
                weekDeletePublisher.assertNoSubscribers()
                enrollmentDeletePublisher.assertNoSubscribers()
                courseDeletePublisher.assertNoSubscribers()
            }
            .then { assignmentDeletePublisher.complete() }
            .then {
                weekDeletePublisher.assertSubscribers(1)
                enrollmentDeletePublisher.assertNoSubscribers()
                courseDeletePublisher.assertNoSubscribers()
            }
            .then { weekDeletePublisher.emit(3L) }
            .then {
                enrollmentDeletePublisher.assertSubscribers(1)
                courseDeletePublisher.assertNoSubscribers()
            }
            .then { enrollmentDeletePublisher.emit(5L) }
            .then { courseDeletePublisher.assertSubscribers(1) }
            .then { courseDeletePublisher.complete() }
            .verifyComplete()
    }

    "코스 관계 삭제는 주차와 수강 삭제가 모두 실패해도 순차 시도 후 오류를 모은다" {
        val fixture = CourseCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentDeletePublisher = TestPublisher.create<Void>()
        val weekDeletePublisher = TestPublisher.create<Long>()
        val enrollmentDeletePublisher = TestPublisher.create<Long>()
        val courseDeletePublisher = TestPublisher.create<Void>()
        val weekDeleteFailure = IllegalStateException("week deletion failed")
        val enrollmentDeleteFailure = IllegalArgumentException("enrollment deletion failed")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentCommandService.deleteAllByCourseId("course-1"))
            .thenReturn(assignmentDeletePublisher.mono())
        Mockito.`when`(fixture.courseWeekRepository.deleteAllByCourseId("course-1"))
            .thenReturn(weekDeletePublisher.mono())
        Mockito.`when`(fixture.courseEnrollmentRepository.deleteAllByCourseId("course-1"))
            .thenReturn(enrollmentDeletePublisher.mono())
        Mockito.`when`(fixture.courseRepository.deleteById("course-1"))
            .thenReturn(courseDeletePublisher.mono())

        StepVerifier.create(fixture.service.deleteCourse("back-basic"))
            .then {
                assignmentDeletePublisher.assertSubscribers(1)
                weekDeletePublisher.assertNoSubscribers()
                enrollmentDeletePublisher.assertNoSubscribers()
                courseDeletePublisher.assertNoSubscribers()
            }
            .then { assignmentDeletePublisher.complete() }
            .then {
                weekDeletePublisher.assertSubscribers(1)
                enrollmentDeletePublisher.assertNoSubscribers()
                courseDeletePublisher.assertNoSubscribers()
            }
            .then { weekDeletePublisher.error(weekDeleteFailure) }
            .then {
                enrollmentDeletePublisher.assertSubscribers(1)
                courseDeletePublisher.assertNoSubscribers()
            }
            .then { enrollmentDeletePublisher.error(enrollmentDeleteFailure) }
            .expectErrorSatisfies { error ->
                val failures = Exceptions.unwrapMultipleExcludingTracebacks(error)
                failures shouldHaveSize 2
                failures.contains(weekDeleteFailure) shouldBe true
                failures.contains(enrollmentDeleteFailure) shouldBe true
            }
            .verify()

        courseDeletePublisher.assertNoSubscribers()
    }

    "과제 삭제가 실패하면 코스 관계와 코스 삭제를 구독하지 않는다" {
        var courseRelationDeleteSubscriptions = 0
        var courseDeleteSubscriptions = 0
        val fixture = CourseCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentCommandService.deleteAllByCourseId("course-1"))
            .thenReturn(Mono.error(IllegalStateException("assignment deletion failed")))
        Mockito.`when`(fixture.courseWeekRepository.deleteAllByCourseId("course-1"))
            .thenReturn(Mono.defer { courseRelationDeleteSubscriptions++; Mono.just(1L) })
        Mockito.`when`(fixture.courseEnrollmentRepository.deleteAllByCourseId("course-1"))
            .thenReturn(Mono.defer { courseRelationDeleteSubscriptions++; Mono.just(1L) })
        Mockito.`when`(fixture.courseRepository.deleteById("course-1"))
            .thenReturn(Mono.defer { courseDeleteSubscriptions++; Mono.empty() })

        StepVerifier.create(fixture.service.deleteCourse("back-basic"))
            .expectErrorSatisfies { error ->
                error::class shouldBe IllegalStateException::class
                error.message shouldBe "assignment deletion failed"
            }
            .verify()

        courseRelationDeleteSubscriptions shouldBe 0
        courseDeleteSubscriptions shouldBe 0
    }

    "과제 복사는 과제 명령 서비스에 그대로 위임한다" {
        val fixture = CourseCommandFixture()
        val request = CopyAssignmentRequest(
            sourceAssignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
            targetWeekNo = 2,
            targetOrderInWeek = 3,
        )
        val response = Mockito.mock(AssignmentDetailResponse::class.java)
        Mockito.`when`(
            fixture.assignmentCommandService.copyAssignment("back-basic", request, "admin")
        ).thenReturn(Mono.just(response))

        StepVerifier.create(fixture.service.copyAssignment("back-basic", request, "admin"))
            .expectNext(response)
            .verifyComplete()

        Mockito.verify(fixture.assignmentCommandService)
            .copyAssignment("back-basic", request, "admin")
    }

    "수강 등록은 수강 명령 서비스에 그대로 위임한다" {
        val fixture = CourseCommandFixture()
        val request = Mockito.mock(EnrollCourseRequest::class.java)
        val response = Mockito.mock(CourseEnrollmentResponse::class.java)
        Mockito.`when`(fixture.courseEnrollmentCommandService.enrollMember("back-basic", request))
            .thenReturn(Mono.just(response))

        StepVerifier.create(fixture.service.enrollMember("back-basic", request))
            .expectNext(response)
            .verifyComplete()

        Mockito.verify(fixture.courseEnrollmentCommandService).enrollMember("back-basic", request)
    }

    "수강 상태 변경은 수강 명령 서비스에 그대로 위임한다" {
        val fixture = CourseCommandFixture()
        val request = Mockito.mock(UpdateEnrollmentRequest::class.java)
        val response = Mockito.mock(CourseEnrollmentResponse::class.java)
        Mockito.`when`(
            fixture.courseEnrollmentCommandService.updateEnrollmentStatus("back-basic", "user-1", request)
        ).thenReturn(Mono.just(response))

        StepVerifier.create(fixture.service.updateEnrollmentStatus("back-basic", "user-1", request))
            .expectNext(response)
            .verifyComplete()

        Mockito.verify(fixture.courseEnrollmentCommandService)
            .updateEnrollmentStatus("back-basic", "user-1", request)
    }

    "수강 삭제는 수강 명령 서비스에 그대로 위임한다" {
        val fixture = CourseCommandFixture()
        Mockito.`when`(fixture.courseEnrollmentCommandService.deleteEnrollment("back-basic", "user-1"))
            .thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteEnrollment("back-basic", "user-1"))
            .verifyComplete()

        Mockito.verify(fixture.courseEnrollmentCommandService).deleteEnrollment("back-basic", "user-1")
    }

    "과제 생성은 과제 명령 서비스에 그대로 위임한다" {
        val fixture = CourseCommandFixture()
        val request = Mockito.mock(CreateAssignmentRequest::class.java)
        val response = Mockito.mock(AssignmentDetailResponse::class.java)
        Mockito.`when`(fixture.assignmentCommandService.createAssignment("back-basic", request, "admin"))
            .thenReturn(Mono.just(response))

        StepVerifier.create(fixture.service.createAssignment("back-basic", request, "admin"))
            .expectNext(response)
            .verifyComplete()

        Mockito.verify(fixture.assignmentCommandService).createAssignment("back-basic", request, "admin")
    }

    "과제 수정은 과제 명령 서비스에 그대로 위임한다" {
        val fixture = CourseCommandFixture()
        val request = Mockito.mock(UpdateAssignmentRequest::class.java)
        val response = Mockito.mock(AssignmentDetailResponse::class.java)
        Mockito.`when`(
            fixture.assignmentCommandService.updateAssignment("back-basic", "assignment-1", request)
        ).thenReturn(Mono.just(response))

        StepVerifier.create(fixture.service.updateAssignment("back-basic", "assignment-1", request))
            .expectNext(response)
            .verifyComplete()

        Mockito.verify(fixture.assignmentCommandService)
            .updateAssignment("back-basic", "assignment-1", request)
    }

    "과제 삭제는 과제 명령 서비스에 그대로 위임한다" {
        val fixture = CourseCommandFixture()
        Mockito.`when`(fixture.assignmentCommandService.deleteAssignment("back-basic", "assignment-1"))
            .thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.deleteAssignment("back-basic", "assignment-1"))
            .verifyComplete()

        Mockito.verify(fixture.assignmentCommandService).deleteAssignment("back-basic", "assignment-1")
    }
})

private class CourseCommandFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val courseEnrollmentCommandService: CourseEnrollmentCommandService =
        Mockito.mock(CourseEnrollmentCommandService::class.java)
    val assignmentCommandService: AssignmentCommandService = Mockito.mock(AssignmentCommandService::class.java)
    val service = CourseCommandService(
        courseStore = RepositoryCourseStore(courseRepository),
        courseEnrollmentStore = RepositoryCourseEnrollmentStore(courseEnrollmentRepository),
        courseWeekStore = RepositoryCourseWeekStore(courseWeekRepository),
        courseEnrollmentCommandService = courseEnrollmentCommandService,
        assignmentCommandService = assignmentCommandService,
    )
}

private fun queryCourse(id: String, slug: String, title: String, fieldTag: CourseTrack = CourseTrack.FL): Course = Course(
    id = id,
    slug = slug,
    fieldTag = fieldTag,
    startDate = LocalDate.of(2026, 3, 1),
    endDate = LocalDate.of(2026, 3, 30),
    metadata = CourseMetadata(
        title = title,
        description = null,
        phase = CoursePhase.BASIC,
        attributes = emptyMap(),
    ),
)
