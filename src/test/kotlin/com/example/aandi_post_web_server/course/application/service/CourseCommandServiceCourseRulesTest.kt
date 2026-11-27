package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.application.service.AssignmentCommandService
import com.example.aandi_post_web_server.course.api.dto.CourseMetadataPayload
import com.example.aandi_post_web_server.course.api.dto.CreateCourseRequest
import com.example.aandi_post_web_server.course.api.dto.UpdateCourseRequest
import com.example.aandi_post_web_server.course.domain.model.CoursePhase
import com.example.aandi_post_web_server.course.domain.model.CourseStatus
import com.example.aandi_post_web_server.course.domain.model.CourseTrack
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.test.StepVerifier
import java.time.LocalDate

class CourseCommandServiceCourseRulesTest : StringSpec({
    "코스 생성은 slug를 정규화하고 메타데이터를 trim 해 PUBLISHED 상태로 저장한다" {
        val fixture = CourseRulesFixture()

        StepVerifier.create(
            fixture.service.createCourse(
                CreateCourseRequest(
                    slug = "  Back-Basic ",
                    fieldTag = CourseTrack.SP,
                    startDate = LocalDate.parse("2026-03-02"),
                    endDate = LocalDate.parse("2026-03-30"),
                    metadata = CourseMetadataPayload(
                        title = "  BACK 기초  ",
                        description = "  서버 기초 과정 ",
                        phase = CoursePhase.FRAMEWORK,
                        attributes = mapOf("cohort" to 3),
                    ),
                ),
            ),
        )
            .assertNext { response ->
                response.id shouldBe "generated-1"
                response.slug shouldBe "back-basic"
                response.fieldTag shouldBe CourseTrack.SP
                response.status shouldBe CourseStatus.PUBLISHED
                response.metadata.title shouldBe "BACK 기초"
                response.metadata.description shouldBe "서버 기초 과정"
                response.metadata.phase shouldBe CoursePhase.FRAMEWORK
                response.metadata.attributes shouldBe mapOf("cohort" to 3)
            }
            .verifyComplete()

        fixture.courseStore.saved shouldHaveSize 1
        val persisted = fixture.courseStore.saved.single()
        persisted.slug shouldBe "back-basic"
        persisted.createdAt shouldBe persisted.updatedAt
    }

    "코스 생성은 종료일이 시작일보다 빠르면 400을 반환하고 저장하지 않는다" {
        val fixture = CourseRulesFixture()

        StepVerifier.create(
            fixture.service.createCourse(
                CreateCourseRequest(
                    slug = "back-basic",
                    fieldTag = CourseTrack.SP,
                    startDate = LocalDate.parse("2026-03-30"),
                    endDate = LocalDate.parse("2026-03-29"),
                    metadata = CourseMetadataPayload(title = "BACK 기초"),
                ),
            ),
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.BAD_REQUEST
                exception.reason shouldBe "endDate는 startDate보다 빠를 수 없습니다."
            }
            .verify()

        fixture.courseStore.saved.shouldBeEmpty()
    }

    "코스 생성은 slug가 비어 있으면 저장소 조회 전에 400을 던진다" {
        val fixture = CourseRulesFixture()

        val exception = shouldThrow<ResponseStatusException> {
            fixture.service.createCourse(
                CreateCourseRequest(
                    slug = "   ",
                    fieldTag = CourseTrack.NO,
                    startDate = LocalDate.parse("2026-03-02"),
                    endDate = LocalDate.parse("2026-03-30"),
                    metadata = CourseMetadataPayload(title = "공통"),
                ),
            )
        }

        exception.statusCode shouldBe HttpStatus.BAD_REQUEST
        exception.reason shouldBe "courseSlug는 비어 있을 수 없습니다."
        fixture.courseStore.saved.shouldBeEmpty()
    }

    "코스 수정은 전달한 필드만 바꾸고 나머지 값은 유지한다" {
        val original = storedCourse(fieldTag = CourseTrack.SP, status = CourseStatus.PUBLISHED)
        val fixture = CourseRulesFixture(original)

        StepVerifier.create(
            fixture.service.updateCourse(
                courseSlug = "BACK-BASIC",
                request = UpdateCourseRequest(fieldTag = CourseTrack.FL),
            ),
        )
            .assertNext { response ->
                response.fieldTag shouldBe CourseTrack.FL
                response.startDate shouldBe original.startDate
                response.endDate shouldBe original.endDate
                response.metadata.title shouldBe original.metadata.title
                response.metadata.description shouldBe original.metadata.description
                response.status shouldBe CourseStatus.PUBLISHED
            }
            .verifyComplete()

        val persisted = fixture.courseStore.saved.single()
        persisted.id shouldBe "course-1"
        persisted.metadata shouldBe original.metadata
        persisted.createdAt shouldBe original.createdAt
        persisted.updatedAt shouldNotBe original.updatedAt
    }

    "코스 수정은 날짜, 메타데이터, 상태를 함께 바꾸고 메타데이터 문자열을 trim 한다" {
        val fixture = CourseRulesFixture(storedCourse())

        StepVerifier.create(
            fixture.service.updateCourse(
                courseSlug = "back-basic",
                request = UpdateCourseRequest(
                    startDate = LocalDate.parse("2026-04-06"),
                    endDate = LocalDate.parse("2026-05-04"),
                    metadata = CourseMetadataPayload(
                        title = " 백엔드 심화 ",
                        description = null,
                        phase = CoursePhase.CS,
                        attributes = mapOf("room" to "A"),
                    ),
                    status = CourseStatus.DRAFT,
                ),
            ),
        )
            .assertNext { response ->
                response.startDate shouldBe LocalDate.parse("2026-04-06")
                response.endDate shouldBe LocalDate.parse("2026-05-04")
                response.metadata.title shouldBe "백엔드 심화"
                response.metadata.description shouldBe null
                response.metadata.phase shouldBe CoursePhase.CS
                response.metadata.attributes shouldBe mapOf("room" to "A")
                response.status shouldBe CourseStatus.DRAFT
            }
            .verifyComplete()

        fixture.courseStore.courses.getValue("course-1").status shouldBe CourseStatus.DRAFT
    }

    "코스 수정은 새 종료일이 기존 시작일보다 빠르면 400을 반환하고 저장하지 않는다" {
        val fixture = CourseRulesFixture(storedCourse(startDate = LocalDate.parse("2026-03-02")))

        StepVerifier.create(
            fixture.service.updateCourse(
                courseSlug = "back-basic",
                request = UpdateCourseRequest(endDate = LocalDate.parse("2026-03-01")),
            ),
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.BAD_REQUEST
                exception.reason shouldBe "endDate는 startDate보다 빠를 수 없습니다."
            }
            .verify()

        fixture.courseStore.saved.shouldBeEmpty()
    }

    "존재하지 않는 코스 수정은 정규화된 slug로 404를 반환한다" {
        val fixture = CourseRulesFixture()

        StepVerifier.create(
            fixture.service.updateCourse("Missing-Course", UpdateCourseRequest(status = CourseStatus.DRAFT)),
        )
            .expectErrorSatisfies { error ->
                val exception = error as ResponseStatusException
                exception.statusCode shouldBe HttpStatus.NOT_FOUND
                exception.reason shouldBe "코스를 찾을 수 없습니다: missing-course"
            }
            .verify()

        fixture.courseStore.saved.shouldBeEmpty()
    }

    "존재하지 않는 코스 삭제는 404를 반환하고 과제 삭제를 호출하지 않는다" {
        val fixture = CourseRulesFixture()

        StepVerifier.create(fixture.service.deleteCourse("missing-course"))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
            }
            .verify()

        Mockito.verify(fixture.assignmentCommandService, Mockito.never())
            .deleteAllByCourseId(ArgumentMatchers.anyString())
    }
})

private class CourseRulesFixture(vararg courses: com.example.aandi_post_web_server.course.entity.Course) {
    val courseStore = InMemoryCourseStore(*courses)
    val assignmentCommandService: AssignmentCommandService = Mockito.mock(AssignmentCommandService::class.java)
    val service = CourseCommandService(
        courseStore = courseStore,
        courseEnrollmentStore = InMemoryCourseEnrollmentStore(),
        courseWeekStore = InMemoryCourseWeekStore(),
        courseEnrollmentCommandService = Mockito.mock(CourseEnrollmentCommandService::class.java),
        assignmentCommandService = assignmentCommandService,
    )
}

