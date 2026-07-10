package com.example.aandi_post_web_server.course.infrastructure.adapter

import com.example.aandi_post_web_server.course.domain.model.CourseId
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseSlug
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import com.example.aandi_post_web_server.course.domain.model.WeekNo
import com.example.aandi_post_web_server.course.entity.Course
import com.example.aandi_post_web_server.course.entity.CourseMetadata
import com.example.aandi_post_web_server.course.entity.CourseWeek
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseRepository
import com.example.aandi_post_web_server.course.infrastructure.repository.CourseWeekRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.time.LocalDate

class AssignmentCourseAdapterTest : StringSpec({
    "course lookups project repository values" {
        val fixture = AssignmentCourseAdapterFixture()
        val course = course()
        Mockito.`when`(fixture.courseRepository.findBySlug(course.slug)).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.courseRepository.findById(requireNotNull(course.id))).thenReturn(Mono.just(course))

        StepVerifier.create(fixture.adapter.findBySlug(CourseSlug.from(course.slug)))
            .assertNext { reference ->
                reference.id shouldBe course.id
                reference.slug shouldBe course.slug
            }
            .verifyComplete()

        StepVerifier.create(fixture.adapter.findSlugById(requireNotNull(course.id)))
            .expectNext(course.slug)
            .verifyComplete()
    }

    "course lookups preserve empty repository results" {
        val fixture = AssignmentCourseAdapterFixture()
        Mockito.`when`(fixture.courseRepository.findBySlug("missing-course")).thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseRepository.findById("missing-id")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.adapter.findBySlug(CourseSlug.from("missing-course")))
            .verifyComplete()
        StepVerifier.create(fixture.adapter.findSlugById("missing-id"))
            .verifyComplete()
    }

    "existing course week completes without saving a duplicate" {
        val fixture = AssignmentCourseAdapterFixture()
        val existingWeek = CourseWeek(
            id = "week-7",
            courseId = COURSE_ID,
            weekNo = WEEK_NO,
            title = "7주차",
        )
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo(COURSE_ID, WEEK_NO))
            .thenReturn(Mono.just(existingWeek))

        StepVerifier.create(
            fixture.adapter.ensureWeekExistsOrCreate(
                courseId = CourseId.from(COURSE_ID),
                weekNo = WeekNo.from(WEEK_NO),
                startAt = START_AT,
                endAt = END_AT,
            )
        ).verifyComplete()

        Mockito.verify(fixture.courseWeekRepository, Mockito.never())
            .save(ArgumentMatchers.any(CourseWeek::class.java))
    }

    "missing course week is saved with Seoul local dates" {
        val fixture = AssignmentCourseAdapterFixture()
        val captor = ArgumentCaptor.forClass(CourseWeek::class.java)
        Mockito.`when`(fixture.courseWeekRepository.findByCourseIdAndWeekNo(COURSE_ID, WEEK_NO))
            .thenReturn(Mono.empty())
        Mockito.`when`(fixture.courseWeekRepository.save(ArgumentMatchers.any(CourseWeek::class.java)))
            .thenAnswer { invocation -> Mono.just(invocation.arguments[0] as CourseWeek) }

        StepVerifier.create(
            fixture.adapter.ensureWeekExistsOrCreate(
                courseId = CourseId.from(COURSE_ID),
                weekNo = WeekNo.from(WEEK_NO),
                startAt = START_AT,
                endAt = END_AT,
            )
        ).verifyComplete()

        Mockito.verify(fixture.courseWeekRepository).save(captor.capture())
        captor.value.courseId shouldBe COURSE_ID
        captor.value.weekNo shouldBe WEEK_NO
        captor.value.title shouldBe "7주차"
        captor.value.startDate shouldBe LocalDate.of(2026, 5, 12)
        captor.value.endDate shouldBe LocalDate.of(2026, 5, 18)
    }
})

private class AssignmentCourseAdapterFixture {
    val courseRepository: CourseRepository = Mockito.mock(CourseRepository::class.java)
    val courseWeekRepository: CourseWeekRepository = Mockito.mock(CourseWeekRepository::class.java)
    val adapter = AssignmentCourseAdapter(courseRepository, courseWeekRepository)
}

private fun course(): Course = Course(
    id = COURSE_ID,
    slug = "back-basic",
    fieldTag = CourseTrack.FL,
    startDate = LocalDate.of(2026, 3, 1),
    endDate = LocalDate.of(2026, 6, 30),
    metadata = CourseMetadata(
        title = "Backend Basic",
        phase = CoursePhase.BASIC,
    ),
)

private const val COURSE_ID = "course-1"
private const val WEEK_NO = 7
private val START_AT: Instant = Instant.parse("2026-05-11T15:00:00Z")
private val END_AT: Instant = Instant.parse("2026-05-18T14:59:59Z")
