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
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
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
        var assignmentDeletionCompleted = false
        var courseRelationsCompleted = 0
        val fixture = CourseCommandFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentCommandService.deleteAllByCourseId("course-1"))
            .thenReturn(Mono.empty<Void>().doOnSuccess { assignmentDeletionCompleted = true })
        Mockito.`when`(fixture.courseWeekRepository.deleteAllByCourseId("course-1"))
            .thenAnswer {
                Mono.defer {
                    assignmentDeletionCompleted shouldBe true
                    courseRelationsCompleted++
                    Mono.just(1L)
                }
            }
        Mockito.`when`(fixture.courseEnrollmentRepository.deleteAllByCourseId("course-1"))
            .thenAnswer {
                Mono.defer {
                    assignmentDeletionCompleted shouldBe true
                    courseRelationsCompleted++
                    Mono.just(1L)
                }
            }
        Mockito.`when`(fixture.courseRepository.deleteById("course-1"))
            .thenAnswer {
                Mono.defer {
                    courseRelationsCompleted shouldBe 2
                    Mono.empty<Void>()
                }
            }

        StepVerifier.create(fixture.service.deleteCourse("back-basic"))
            .verifyComplete()

        assignmentDeletionCompleted shouldBe true
        courseRelationsCompleted shouldBe 2
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
        courseRepository = courseRepository,
        courseEnrollmentRepository = courseEnrollmentRepository,
        courseWeekRepository = courseWeekRepository,
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
