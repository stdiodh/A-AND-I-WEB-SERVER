package com.example.aandi_post_web_server.assignment.application.mapper

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentRequirementResponse
import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.domain.model.EffectiveAssignmentPublication
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.entity.AssignmentRequirement
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class AssignmentResponseMapperTest : StringSpec({
    "child entities map every exposed response field" {
        val requirement = AssignmentRequirement(
            assignmentId = "assignment-1",
            sortOrder = 2,
            requirementText = "요구사항",
        )
        val testCase = AssignmentTestCase(
            assignmentId = "assignment-1",
            seq = 3,
            inputValues = listOf("A", "B"),
            outputText = "C",
            visibility = AssignmentTestCaseVisibility.HIDDEN,
        )

        requirement.toResponse() shouldBe AssignmentRequirementResponse(2, "요구사항")
        testCase.toResponse() shouldBe AssignmentTestCaseResponse(
            seq = 3,
            inputValues = listOf("A", "B"),
            outputText = "C",
            visibility = AssignmentTestCaseVisibility.HIDDEN,
        )
    }

    "summary response uses explicit publication and supplied children" {
        val assignment = assignment()
        val publication = publication()
        val requirements = listOf(AssignmentRequirementResponse(1, "요구사항"))
        val testCases = listOf(AssignmentTestCaseResponse(1, listOf("1"), "2"))

        val response = assignment.toSummaryResponse(publication, requirements, testCases)

        response.id shouldBe "assignment-1"
        response.weekNo shouldBe 2
        response.orderInWeek shouldBe 3
        response.startAt shouldBe START_AT
        response.endAt shouldBe END_AT
        response.status shouldBe AssignmentStatus.PUBLISHED
        response.publishedAt shouldBe PUBLISHED_AT
        response.metadata.title shouldBe "과제"
        response.metadata.requirements shouldBe requirements
        response.metadata.testCases shouldBe testCases
    }

    "detail response adds course slug and uses explicit publication" {
        val requirements = listOf(AssignmentRequirementResponse(1, "요구사항"))
        val testCases = listOf(AssignmentTestCaseResponse(1, listOf("1"), "2"))

        val response = assignment().toDetailResponse("back-basic", publication(), requirements, testCases)

        response.id shouldBe "assignment-1"
        response.courseSlug shouldBe "back-basic"
        response.weekNo shouldBe 2
        response.orderInWeek shouldBe 3
        response.startAt shouldBe START_AT
        response.endAt shouldBe END_AT
        response.status shouldBe AssignmentStatus.PUBLISHED
        response.publishedAt shouldBe PUBLISHED_AT
        response.metadata.title shouldBe "과제"
        response.metadata.requirements shouldBe requirements
        response.metadata.testCases shouldBe testCases
    }

    "assignment response requires a persisted id" {
        shouldThrow<IllegalArgumentException> {
            assignment().copy(id = null).toSummaryResponse(publication(), emptyList(), emptyList())
        }
    }
})

private val START_AT = Instant.parse("2026-03-01T00:00:00Z")
private val END_AT = Instant.parse("2026-03-08T00:00:00Z")
private val PUBLISHED_AT = Instant.parse("2026-03-01T00:00:00Z")

private fun publication(): EffectiveAssignmentPublication =
    EffectiveAssignmentPublication(
        status = AssignmentStatus.PUBLISHED,
        publishedAt = PUBLISHED_AT,
    )

private fun assignment(): Assignment = Assignment(
    id = "assignment-1",
    courseId = "course-1",
    courseSlug = "back-basic",
    createdBy = "admin-1",
    weekNo = 2,
    orderInWeek = 3,
    startAt = START_AT,
    endAt = END_AT,
    metadata = AssignmentMetadata(
        title = "과제",
        difficulty = AssignmentDifficulty.MID,
        description = "설명",
        timeLimitMinutes = 60,
    ),
    status = AssignmentStatus.DRAFT,
)
