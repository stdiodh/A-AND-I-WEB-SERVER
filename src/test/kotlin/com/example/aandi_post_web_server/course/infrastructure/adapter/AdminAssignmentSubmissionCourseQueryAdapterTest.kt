package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.course.api.dto.CourseEnrollmentResponse
import com.example.aandi_post_web_server.course.application.service.CourseQueryService
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class AdminAssignmentSubmissionCourseQueryAdapterTest : StringSpec({
    "assignment validation errors are preserved" {
        val courseQueryService = Mockito.mock(CourseQueryService::class.java)
        val adapter = AdminAssignmentSubmissionCourseQueryAdapter(courseQueryService)
        val error = ResponseStatusException(HttpStatus.NOT_FOUND, "과제를 찾을 수 없습니다.")
        Mockito.`when`(courseQueryService.getAdminAssignmentDetail(COURSE_SLUG, ASSIGNMENT_ID))
            .thenReturn(Mono.error(error))

        StepVerifier.create(adapter.ensureAssignmentBelongsToCourse(COURSE_SLUG, ASSIGNMENT_ID))
            .expectErrorSatisfies { actual -> actual shouldBe error }
            .verify()
    }

    "enrollments are projected without changing their order" {
        val courseQueryService = Mockito.mock(CourseQueryService::class.java)
        val adapter = AdminAssignmentSubmissionCourseQueryAdapter(courseQueryService)
        Mockito.`when`(courseQueryService.getEnrollments(COURSE_SLUG))
            .thenReturn(
                Flux.just(
                    enrollment("user-2", "#BE302", "bob", EnrollmentStatus.BANNED),
                    enrollment("user-1", "#BE301", "alice", EnrollmentStatus.ENABLED),
                )
            )

        StepVerifier.create(adapter.findEnrollments(COURSE_SLUG))
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-2"
                enrollment.publicCode shouldBe "#BE302"
                enrollment.username shouldBe "bob"
                enrollment.status shouldBe EnrollmentStatus.BANNED
            }
            .assertNext { enrollment ->
                enrollment.userId shouldBe "user-1"
                enrollment.publicCode shouldBe "#BE301"
                enrollment.username shouldBe "alice"
                enrollment.status shouldBe EnrollmentStatus.ENABLED
            }
            .verifyComplete()
    }
})

private fun enrollment(
    userId: String,
    publicCode: String,
    username: String,
    status: EnrollmentStatus,
): CourseEnrollmentResponse =
    CourseEnrollmentResponse(
        courseId = "course-1",
        courseSlug = COURSE_SLUG,
        userId = userId,
        publicCode = publicCode,
        username = username,
        status = status,
        joinedAt = Instant.parse("2026-03-05T09:20:18Z"),
        bannedAt = null,
        banReason = null,
        updatedAt = Instant.parse("2026-03-05T09:20:18Z"),
    )

private const val COURSE_SLUG = "back-basic"
private const val ASSIGNMENT_ID = "7fbe8f62-9d89-4c74-b1e4-3ad3b9d7f001"
