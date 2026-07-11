package com.example.aandi_post_web_server.course.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTemplateLanguage
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.course.application.service.CourseQueryServiceTestData.queryAssignment
import com.example.aandi_post_web_server.course.application.service.CourseQueryServiceTestData.queryAssignmentId
import com.example.aandi_post_web_server.course.application.service.CourseQueryServiceTestData.queryCourse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CourseQueryServiceAdminAssignmentTest : StringSpec({
    "관리자 과제 목록 조회는 수강 상태와 무관하게 status 필터를 적용한다" {
        val fixture = CourseQueryServiceTestFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val draft = queryAssignment(id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", courseId = "course-1").copy(
            status = AssignmentStatus.DRAFT,
            startAt = Instant.parse("2026-04-01T00:00:00Z"),
            publishedAt = null,
        )
        val published = queryAssignment(id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1", courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(draft, published))

        StepVerifier.create(
            fixture.service.getAdminAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.DRAFT,
            ).map { it.id }.collectList()
        )
            .assertNext { ids ->
                ids.shouldContainExactly("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
            }
            .verifyComplete()
    }

    "관리자 과제 상세 조회는 DRAFT 과제도 조회할 수 있다" {
        val fixture = CourseQueryServiceTestFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val draft = queryAssignment(id = assignmentId, courseId = "course-1").copy(
            status = AssignmentStatus.DRAFT,
            startAt = Instant.parse("2026-04-01T00:00:00Z"),
            publishedAt = null,
        )

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(draft))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAdminAssignmentDetail("back-basic", assignmentId)
        )
            .assertNext { detail ->
                detail.id shouldBe assignmentId
                detail.status shouldBe AssignmentStatus.DRAFT
                detail.metadata.codeTemplates.first().language shouldBe AssignmentTemplateLanguage.KOTLIN
            }
            .verifyComplete()
    }

    "관리자 과제 목록 응답은 top-level title, publishedAt, problemId 를 포함한다" {
        val fixture = CourseQueryServiceTestFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(published))

        StepVerifier.create(
            fixture.service.getAdminAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
            )
        )
            .assertNext { summary ->
                summary.id shouldBe assignmentId
                summary.title shouldBe "배포 조회 테스트 과제"
                summary.metadata.title shouldBe "배포 조회 테스트 과제"
                summary.publishedAt shouldBe published.publishedAt
                summary.problemId shouldBe assignmentId
            }
            .verifyComplete()
    }

    "관리자 과제 상세 응답은 top-level title, publishedAt, problemId 를 포함한다" {
        val fixture = CourseQueryServiceTestFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(published))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAdminAssignmentDetail("back-basic", assignmentId)
        )
            .assertNext { detail ->
                detail.id shouldBe assignmentId
                detail.title shouldBe "배포 조회 테스트 과제"
                detail.metadata.title shouldBe "배포 조회 테스트 과제"
                detail.publishedAt shouldBe published.publishedAt
                detail.problemId shouldBe assignmentId
            }
            .verifyComplete()
    }

    "관리자 과제 목록 조회는 hidden과 excluded testcase 노출 동작을 유지한다" {
        val fixture = CourseQueryServiceTestFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = queryAssignmentId(1)
        val draft = queryAssignment(id = assignmentId, courseId = "course-1")
            .copy(status = AssignmentStatus.DRAFT, publishedAt = null)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(draft))
        fixture.stubBatchChildren(
            examples = listOf(
                AssignmentTestCase(
                    assignmentId = assignmentId,
                    seq = 3,
                    inputValues = listOf("excluded"),
                    outputText = "internal",
                    visibility = AssignmentTestCaseVisibility.EXCLUDED,
                ),
                AssignmentTestCase(
                    assignmentId = assignmentId,
                    seq = 1,
                    inputValues = listOf("public"),
                    outputText = "visible",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = assignmentId,
                    seq = 2,
                    inputValues = listOf("hidden"),
                    outputText = "secret",
                    visibility = AssignmentTestCaseVisibility.HIDDEN,
                ),
            ),
        )

        StepVerifier.create(fixture.service.getAdminAssignments("back-basic", null, null))
            .assertNext { summary ->
                summary.status shouldBe AssignmentStatus.DRAFT
                summary.metadata.examples.map { it.outputText }.shouldContainExactly("visible", "secret", "internal")
            }
            .verifyComplete()
    }

    "관리자 상태 필터와 응답은 요청당 동일한 공개 기준 시각을 사용한다" {
        val startAt = Instant.parse("2026-07-10T00:00:00Z")
        val clock = AdvancingClock(
            startAt.minusNanos(1),
            startAt.plusNanos(1),
        )
        val fixture = CourseQueryServiceTestFixture(clock)
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val scheduled = queryAssignment(id = queryAssignmentId(1), courseId = "course-1")
            .copy(startAt = startAt, publishedAt = startAt)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(scheduled))

        StepVerifier.create(fixture.service.getAdminAssignments("back-basic", null, AssignmentStatus.DRAFT))
            .assertNext { summary -> summary.status shouldBe AssignmentStatus.DRAFT }
            .verifyComplete()

        clock.readCount shouldBe 1
    }

    "관리자 과제 상세 조회는 hidden과 excluded testcase를 모두 포함한다" {
        val fixture = CourseQueryServiceTestFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val draft = queryAssignment(id = assignmentId, courseId = "course-1").copy(status = AssignmentStatus.DRAFT, publishedAt = null)

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(draft))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId))
            .thenReturn(Flux.empty())
        Mockito.`when`(fixture.assignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId))
            .thenReturn(
                Flux.just(
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 1,
                        inputValues = listOf("public"),
                        outputText = "visible",
                        visibility = AssignmentTestCaseVisibility.PUBLIC,
                    ),
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 2,
                        inputValues = listOf("hidden"),
                        outputText = "secret",
                        visibility = AssignmentTestCaseVisibility.HIDDEN,
                    ),
                    AssignmentTestCase(
                        assignmentId = assignmentId,
                        seq = 3,
                        inputValues = listOf("excluded"),
                        outputText = "internal",
                        visibility = AssignmentTestCaseVisibility.EXCLUDED,
                    ),
                )
            )

        StepVerifier.create(fixture.service.getAdminAssignmentDetail("back-basic", assignmentId))
            .assertNext { detail ->
                detail.status shouldBe AssignmentStatus.DRAFT
                detail.metadata.testCases.map { it.outputText }.shouldContainExactly("visible", "secret", "internal")
            }
            .verifyComplete()
    }

    "관리자 과제 목록 조회는 없는 코스를 NOT_FOUND로 분류하고 과제를 조회하지 않는다" {
        val fixture = CourseQueryServiceTestFixture()
        Mockito.`when`(fixture.courseRepository.findBySlug("missing-course")).thenReturn(Mono.empty())

        StepVerifier.create(fixture.service.getAdminAssignments("missing-course", null, null))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "코스를 찾을 수 없습니다: missing-course"
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never()).findAllByCourseId(Mockito.anyString())
        Mockito.verify(fixture.assignmentRepository, Mockito.never())
            .findAllByCourseIdAndWeekNo(Mockito.anyString(), Mockito.anyInt())
    }

    "관리자 과제 목록 week 필터는 주차별 repository 조회를 사용한다" {
        val fixture = CourseQueryServiceTestFixture()
        val course = queryCourse(id = "course-1", slug = "back-basic", title = "BACK 기초")
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        Mockito.`when`(fixture.courseRepository.findBySlug("back-basic")).thenReturn(Mono.just(course))
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseIdAndWeekNo("course-1", 1))
            .thenReturn(Flux.just(published))

        StepVerifier.create(fixture.service.getAdminAssignments("back-basic", 1, null))
            .assertNext { summary ->
                summary.id shouldBe assignmentId
            }
            .verifyComplete()

        Mockito.verify(fixture.assignmentRepository).findAllByCourseIdAndWeekNo("course-1", 1)
    }
})

private class AdvancingClock(
    private vararg val instants: Instant,
) : Clock() {
    var readCount: Int = 0
        private set

    override fun instant(): Instant = instants[minOf(readCount++, instants.lastIndex)]

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this
}
