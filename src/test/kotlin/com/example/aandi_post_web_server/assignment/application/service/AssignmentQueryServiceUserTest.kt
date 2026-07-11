package com.example.aandi_post_web_server.assignment.application.service

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import com.example.aandi_post_web_server.assignment.application.service.AssignmentQueryServiceTestData.queryAssignment
import com.example.aandi_post_web_server.assignment.application.service.AssignmentQueryServiceTestData.queryAssignmentId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class AssignmentQueryServiceUserTest : StringSpec({
    "과제 조회는 status 미지정 시 PUBLISHED만 조회한다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val published = queryAssignment(id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", courseId = "course-1")
        val futureDraft = queryAssignment(id = "7c53f1b3-0df8-4a9d-a56d-a5f50b96b7a1", courseId = "course-1").copy(
            status = AssignmentStatus.DRAFT,
            startAt = Instant.parse("2026-04-01T00:00:00Z"),
            publishedAt = null,
        )

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(published, futureDraft))

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = null,
                userId = userId,
            ).map { it.id }.collectList()
        )
            .assertNext { ids ->
                ids.shouldContainExactly("8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111")
            }
            .verifyComplete()
    }

    "과제 조회에서 DRAFT 상태 요청은 BAD_REQUEST를 반환한다" {
        val fixture = AssignmentQueryServiceTestFixture()
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

    "과제 목록 조회는 requirements와 examples를 함께 반환한다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val published = queryAssignment(id = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111", courseId = "course-1")

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(published))
        fixture.stubBatchChildren(
            requirements = listOf(
                AssignmentRequirement(
                    assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                    sortOrder = 1,
                    requirementText = "함수 분리 필수",
                )
            ),
            examples = listOf(
                AssignmentTestCase(
                    assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111",
                    seq = 1,
                    inputValues = listOf("ADD 1"),
                    outputText = "1",
                )
            ),
        )

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            )
        )
            .assertNext {
                it.id shouldBe published.id
                it.weekNo shouldBe 1
                it.metadata.requirements.map { requirement -> requirement.requirementText } shouldBe listOf("함수 분리 필수")
                it.metadata.examples.map { example -> example.outputText } shouldBe listOf("1")
            }
            .verifyComplete()
    }

    "주차별 사용자 과제 조회는 PUBLIC testcase만 노출한다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseIdAndWeekNo("course-1", 1))
            .thenReturn(Flux.just(published))
        fixture.stubBatchChildren(
            examples = listOf(
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

        StepVerifier.create(
            fixture.service.getAssignmentsByWeek(
                courseSlug = "back-basic",
                weekNo = 1,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            )
        )
            .assertNext { summary ->
                summary.metadata.examples.map { it.outputText } shouldBe listOf("visible")
            }
            .verifyComplete()
    }

    "과제 목록 조회는 빈 목록이면 child document batch 조회를 실행하지 않는다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.empty())

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            )
        )
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdOrderBySortOrder(Mockito.anyString())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdOrderBySeq(Mockito.anyString())
    }

    "과제 목록 조회는 assignment 수와 무관하게 child document를 batch로 한 번씩 조회한다" {
        verifyAssignmentListUsesSingleBatchForCount(1)
        verifyAssignmentListUsesSingleBatchForCount(30)
    }

    "과제 목록 조회는 정렬과 child document 매핑 및 PUBLIC testcase 필터링을 보존한다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val firstId = queryAssignmentId(1)
        val secondId = queryAssignmentId(2)
        val emptyId = queryAssignmentId(3)
        val first = queryAssignment(id = firstId, courseId = "course-1").copy(weekNo = 1, orderInWeek = 2)
        val second = queryAssignment(id = secondId, courseId = "course-1").copy(weekNo = 1, orderInWeek = 1)
        val empty = queryAssignment(id = emptyId, courseId = "course-1").copy(weekNo = 2, orderInWeek = 1)
        val privateMarker = "PERF_PRIVATE_MUST_NOT_LEAK_001"

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(empty, first, second))
        fixture.stubBatchChildren(
            requirements = listOf(
                AssignmentRequirement(assignmentId = firstId, sortOrder = 2, requirementText = "A second"),
                AssignmentRequirement(assignmentId = secondId, sortOrder = 1, requirementText = "duplicate requirement"),
                AssignmentRequirement(assignmentId = firstId, sortOrder = 1, requirementText = "duplicate requirement"),
            ),
            examples = listOf(
                AssignmentTestCase(
                    assignmentId = firstId,
                    seq = 3,
                    inputValues = listOf("hidden-a"),
                    outputText = privateMarker,
                    visibility = AssignmentTestCaseVisibility.HIDDEN,
                ),
                AssignmentTestCase(
                    assignmentId = firstId,
                    seq = 2,
                    inputValues = listOf("a2"),
                    outputText = "A public second",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = secondId,
                    seq = 1,
                    inputValues = listOf("same"),
                    outputText = "duplicate output",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = firstId,
                    seq = 1,
                    inputValues = listOf("same"),
                    outputText = "duplicate output",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
                AssignmentTestCase(
                    assignmentId = secondId,
                    seq = 2,
                    inputValues = listOf("hidden-b"),
                    outputText = privateMarker,
                    visibility = AssignmentTestCaseVisibility.HIDDEN,
                ),
            ),
        )

        StepVerifier.create(
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = null,
                status = AssignmentStatus.PUBLISHED,
                userId = userId,
            ).collectList()
        )
            .assertNext { assignments ->
                assignments.map { it.id }.shouldContainExactly(secondId, firstId, emptyId)
                assignments[0].metadata.requirements.map { it.requirementText } shouldBe listOf("duplicate requirement")
                assignments[0].metadata.examples.map { it.outputText } shouldBe listOf("duplicate output")
                assignments[1].metadata.requirements.map { it.requirementText } shouldBe listOf("duplicate requirement", "A second")
                assignments[1].metadata.examples.map { it.outputText } shouldBe listOf("duplicate output", "A public second")
                assignments[2].metadata.requirements shouldBe emptyList()
                assignments[2].metadata.examples shouldBe emptyList()
                assignments.flatMap { it.metadata.examples }.any { it.outputText == privateMarker } shouldBe false
            }
            .verifyComplete()
    }

    "과제 목록 조회는 수강 실패 시 assignment와 child document를 조회하지 않는다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId, enabled = false)

        StepVerifier.create(fixture.service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, userId))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "조회 가능한 코스를 찾을 수 없습니다."
            }
            .verify()

        Mockito.verify(fixture.assignmentRepository, Mockito.never()).findAllByCourseId(Mockito.anyString())
        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
    }

    "과제 목록 조회는 future-scheduled와 DRAFT 과제를 사용자에게 노출하지 않는다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val visible = queryAssignment(id = queryAssignmentId(1), courseId = "course-1")
        val future = queryAssignment(id = queryAssignmentId(2), courseId = "course-1")
            .copy(startAt = Instant.parse("2027-01-01T00:00:00Z"), publishedAt = Instant.parse("2027-01-01T00:00:00Z"))
        val draft = queryAssignment(id = queryAssignmentId(3), courseId = "course-1")
            .copy(status = AssignmentStatus.DRAFT, publishedAt = null)

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(future, draft, visible))

        StepVerifier.create(
            fixture.service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, userId)
                .map { it.id }
                .collectList()
        )
            .assertNext { ids -> ids.shouldContainExactly(queryAssignmentId(1)) }
            .verifyComplete()
    }

    "과제 목록 조회는 child document batch 조회 실패를 전파한다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = queryAssignmentId(1)
        val published = queryAssignment(id = assignmentId, courseId = "course-1")
        val failure = IllegalStateException("requirement batch failed")

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
            .thenReturn(Flux.just(published))
        Mockito.`when`(fixture.assignmentRequirementRepository.findAllByAssignmentIdIn(listOf(assignmentId)))
            .thenReturn(Flux.error(failure))

        StepVerifier.create(fixture.service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, userId))
            .expectErrorSatisfies { error -> error shouldBe failure }
            .verify()
    }

    "사용자 과제 상세 조회는 PRIVATE testcase를 노출하지 않는다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val published = queryAssignment(id = assignmentId, courseId = "course-1")

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(published))
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

        StepVerifier.create(fixture.service.getAssignmentDetail("back-basic", assignmentId, userId))
            .assertNext { detail ->
                detail.id shouldBe assignmentId
                detail.courseSlug shouldBe "back-basic"
                detail.metadata.testCases.map { it.outputText } shouldBe listOf("visible")
            }
            .verifyComplete()

        Mockito.verify(fixture.assignmentRequirementRepository)
            .findAllByAssignmentIdOrderBySortOrder(assignmentId)
        Mockito.verify(fixture.assignmentTestCaseRepository)
            .findAllByAssignmentIdOrderBySeq(assignmentId)
        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdIn(Mockito.anyCollection())
    }

    "사용자 과제 상세 조회는 비공개 과제를 숨기고 child document를 조회하지 않는다" {
        val fixture = AssignmentQueryServiceTestFixture()
        val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val draft = queryAssignment(id = assignmentId, courseId = "course-1")
            .copy(status = AssignmentStatus.DRAFT, publishedAt = null)

        fixture.stubCourse("course-1", "back-basic")
        fixture.stubEnrollment("course-1", userId)
        Mockito.`when`(fixture.assignmentRepository.findByIdAndCourseId(assignmentId, "course-1"))
            .thenReturn(Mono.just(draft))

        StepVerifier.create(fixture.service.getAssignmentDetail("back-basic", assignmentId, userId))
            .expectErrorSatisfies { error ->
                (error as ResponseStatusException).statusCode shouldBe HttpStatus.NOT_FOUND
                error.reason shouldBe "과제를 찾을 수 없습니다: $assignmentId"
            }
            .verify()

        Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
            .findAllByAssignmentIdOrderBySortOrder(Mockito.anyString())
        Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
            .findAllByAssignmentIdOrderBySeq(Mockito.anyString())
    }

    "잘못된 weekNo는 BAD_REQUEST를 반환한다" {
        val fixture = AssignmentQueryServiceTestFixture()

        val error = shouldThrow<ResponseStatusException> {
            fixture.service.getAssignments(
                courseSlug = "back-basic",
                weekNo = 0,
                status = AssignmentStatus.PUBLISHED,
                userId = "8ee88b63-526d-49dc-9e72-a96be0f81385",
            )
        }

        error.statusCode shouldBe HttpStatus.BAD_REQUEST
        error.reason shouldBe "weekNo는 1 이상이어야 합니다."
    }
})

private fun verifyAssignmentListUsesSingleBatchForCount(count: Int) {
    val fixture = AssignmentQueryServiceTestFixture()
    val userId = "8ee88b63-526d-49dc-9e72-a96be0f81385"
    val assignments = (1..count).map { index ->
        queryAssignment(id = queryAssignmentId(index), courseId = "course-1")
            .copy(weekNo = ((index - 1) / 10) + 1, orderInWeek = ((index - 1) % 10) + 1)
    }
    val assignmentIds = assignments.map { assignment -> requireNotNull(assignment.id) }

    fixture.stubCourse("course-1", "back-basic")
    fixture.stubEnrollment("course-1", userId)
    Mockito.`when`(fixture.assignmentRepository.findAllByCourseId("course-1"))
        .thenReturn(Flux.fromIterable(assignments.asReversed()))

    StepVerifier.create(
        fixture.service.getAssignments("back-basic", null, AssignmentStatus.PUBLISHED, userId)
            .map { it.id }
            .collectList()
    )
        .assertNext { ids -> ids.shouldContainExactly(assignmentIds) }
        .verifyComplete()

    Mockito.verify(fixture.assignmentRequirementRepository, Mockito.times(1))
        .findAllByAssignmentIdIn(assignmentIds)
    Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.times(1))
        .findAllByAssignmentIdIn(assignmentIds)
    Mockito.verify(fixture.assignmentRequirementRepository, Mockito.never())
        .findAllByAssignmentIdOrderBySortOrder(Mockito.anyString())
    Mockito.verify(fixture.assignmentTestCaseRepository, Mockito.never())
        .findAllByAssignmentIdOrderBySeq(Mockito.anyString())
}
