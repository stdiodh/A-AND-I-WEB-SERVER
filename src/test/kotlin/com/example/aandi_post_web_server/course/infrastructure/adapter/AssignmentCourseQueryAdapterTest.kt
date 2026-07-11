package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.EnrollmentStatus
import com.example.aandi_post_web_server.course.domain.model.UserId
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseEnrollment
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseEnrollmentRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDate

class AssignmentCourseQueryAdapterTest : StringSpec({
    "course lookup projects the minimal assignment reference" {
        val fixture = AssignmentCourseQueryAdapterFixture()
        val course = course()
        Mockito.`when`(fixture.courseRepository.findBySlug(COURSE_SLUG)).thenReturn(Mono.just(course))

        StepVerifier.create(fixture.adapter.findBySlug(CourseSlug.from("  BACK-BASIC  ")))
            .assertNext { reference ->
                reference.id shouldBe COURSE_ID
                reference.slug shouldBe COURSE_SLUG
            }
            .verifyComplete()

        Mockito.verify(fixture.courseRepository).findBySlug(COURSE_SLUG)
    }

    "missing course lookup remains empty for the application service to classify" {
        val fixture = AssignmentCourseQueryAdapterFixture()
        Mockito.`when`(fixture.courseRepository.findBySlug(COURSE_SLUG)).thenReturn(Mono.empty())

        StepVerifier.create(fixture.adapter.findBySlug(CourseSlug.from(COURSE_SLUG)))
            .verifyComplete()
    }

    "enabled enrollment returns true" {
        val fixture = AssignmentCourseQueryAdapterFixture()
        Mockito.`when`(fixture.courseEnrollmentRepository.findByCourseIdAndUserId(COURSE_ID, USER_ID))
            .thenReturn(Mono.just(enrollment(EnrollmentStatus.ENABLED)))

        StepVerifier.create(
            fixture.adapter.isEnrollmentEnabled(CourseId.from(COURSE_ID), UserId.from(USER_ID))
        )
            .expectNext(true)
            .verifyComplete()
    }

    "banned or missing enrollment returns false" {
        val bannedFixture = AssignmentCourseQueryAdapterFixture()
        Mockito.`when`(bannedFixture.courseEnrollmentRepository.findByCourseIdAndUserId(COURSE_ID, USER_ID))
            .thenReturn(Mono.just(enrollment(EnrollmentStatus.BANNED)))

        StepVerifier.create(
            bannedFixture.adapter.isEnrollmentEnabled(CourseId.from(COURSE_ID), UserId.from(USER_ID))
        )
            .expectNext(false)
            .verifyComplete()

        val missingFixture = AssignmentCourseQueryAdapterFixture()
        Mockito.`when`(missingFixture.courseEnrollmentRepository.findByCourseIdAndUserId(COURSE_ID, USER_ID))
            .thenReturn(Mono.empty())

        StepVerifier.create(
            missingFixture.adapter.isEnrollmentEnabled(CourseId.from(COURSE_ID), UserId.from(USER_ID))
        )
            .expectNext(false)
            .verifyComplete()
    }
})

private class AssignmentCourseQueryAdapterFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseEnrollmentRepository: CourseEnrollmentRepository = Mockito.mock(CourseEnrollmentRepository::class.java)
    val adapter = AssignmentCourseQueryAdapter(courseRepository, courseEnrollmentRepository)
}

private fun course(): Course = Course(
    id = COURSE_ID,
    slug = COURSE_SLUG,
    fieldTag = CourseTrack.FL,
    startDate = LocalDate.of(2026, 3, 1),
    endDate = LocalDate.of(2026, 6, 30),
    metadata = CourseMetadata(
        title = "Backend Basic",
        phase = CoursePhase.BASIC,
    ),
)

private fun enrollment(status: EnrollmentStatus): CourseEnrollment = CourseEnrollment(
    courseId = COURSE_ID,
    userId = USER_ID,
    status = status,
)

private const val COURSE_ID = "course-1"
private const val COURSE_SLUG = "back-basic"
private const val USER_ID = "user-1"
