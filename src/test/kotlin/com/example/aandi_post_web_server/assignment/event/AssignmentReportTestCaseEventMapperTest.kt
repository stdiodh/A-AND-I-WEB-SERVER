package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentExampleResponse
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentStatus
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

class AssignmentReportTestCaseEventMapperTest : StringSpec({
    val mapper = AssignmentReportTestCaseEventMapper()

    "create 이벤트는 EXCLUDED 를 제외한 visibility 만 OJ payload 에 포함한다" {
        val event = mapper.created(
            assignment = assignment(
                id = "assignment-uuid",
                status = AssignmentStatus.DRAFT,
            ),
            testCases = listOf(
                AssignmentExampleResponse(seq = 3, inputValues = listOf("9", "9"), outputText = "18", visibility = AssignmentTestCaseVisibility.EXCLUDED),
                AssignmentExampleResponse(seq = 2, inputValues = listOf("3", "4"), outputText = "7", visibility = AssignmentTestCaseVisibility.HIDDEN),
                AssignmentExampleResponse(seq = 1, inputValues = listOf("1", "2"), outputText = "3", visibility = AssignmentTestCaseVisibility.PUBLIC),
            ),
        )

        event.eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_CREATED
        event.problemId shouldBe "assignment-uuid"
        event.testCases shouldHaveSize 2
        event.testCases.first().caseId shouldBe 1
        event.testCases.first().input shouldBe listOf("1", "2")
        event.testCases.first().output shouldBe "3"
        event.testCases.last().caseId shouldBe 2
        event.testCases.last().input shouldBe listOf("3", "4")
    }

    "update 이벤트는 input 배열 규약을 유지한 problem update payload 를 만든다" {
        val event = mapper.updated(
            assignment = assignment(
                id = "assignment-uuid",
                status = AssignmentStatus.PUBLISHED,
            ),
            testCases = listOf(
                AssignmentExampleResponse(seq = 1, inputValues = listOf("A"), outputText = "B", visibility = AssignmentTestCaseVisibility.PUBLIC),
                AssignmentExampleResponse(seq = 2, inputValues = listOf("C"), outputText = "D", visibility = AssignmentTestCaseVisibility.HIDDEN),
            ),
        )

        event.eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_UPDATED
        event.problemId shouldBe "assignment-uuid"
        event.testCases shouldHaveSize 2
        event.testCases[1].input shouldBe listOf("C")
    }

    "problem sync 이벤트의 problemId 는 assignmentId 와 동일하다" {
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val event = mapper.created(
            assignment = assignment(
                id = assignmentId,
                status = AssignmentStatus.PUBLISHED,
            ),
            testCases = emptyList(),
        )

        event.problemId shouldBe assignmentId
    }

    "ASSIGNMENT_PUBLISHED Report EVENT payload 는 monitor 필드를 모두 담는다" {
        val assignmentId = "8f7f8a47-3f5e-4f59-9f2d-a9a9e7b6f111"
        val publishedAt = Instant.parse("2026-03-18T00:00:00Z")
        val payload = AssignmentReportEventPayload.from(
            eventType = AssignmentReportEventType.ASSIGNMENT_PUBLISHED,
            assignment = assignment(
                id = assignmentId,
                status = AssignmentStatus.PUBLISHED,
            ).copy(publishedAt = publishedAt),
            status = AssignmentStatus.PUBLISHED,
            publishedAt = publishedAt,
        )

        payload.assignmentId shouldBe assignmentId
        payload.courseSlug shouldBe "back-basic"
        payload.title shouldBe "title"
        payload.status shouldBe AssignmentStatus.PUBLISHED
        payload.startAt shouldBe Instant.parse("2026-03-18T00:00:00Z")
        payload.endAt shouldBe Instant.parse("2026-03-25T00:00:00Z")
        payload.publishedAt shouldBe publishedAt
        payload.problemId shouldBe assignmentId
    }

    "빈 입력은 빈 args 배열로 변환한다" {
        val event = mapper.created(
            assignment = assignment(
                id = "assignment-uuid",
                status = AssignmentStatus.DRAFT,
            ),
            testCases = listOf(
                AssignmentExampleResponse(seq = 1, inputValues = emptyList(), outputText = "EMPTY", visibility = AssignmentTestCaseVisibility.PUBLIC),
            ),
        )

        event.testCases.single().input shouldBe emptyList()
    }

    "한 줄 공백은 유지하고 줄바꿈만 args 경계로 사용한다" {
        val event = mapper.updated(
            assignment = assignment(
                id = "assignment-uuid",
                status = AssignmentStatus.PUBLISHED,
            ),
            testCases = listOf(
                AssignmentExampleResponse(
                    seq = 1,
                    inputValues = listOf("hello world", "42  99"),
                    outputText = "ok",
                    visibility = AssignmentTestCaseVisibility.PUBLIC,
                ),
            ),
        )

        event.testCases.single().input shouldBe listOf("hello world", "42  99")
    }

    "create 이벤트는 모든 케이스가 EXCLUDED 면 빈 testCases 배열을 유지한다" {
        val event = mapper.created(
            assignment = assignment(
                id = "assignment-uuid",
                status = AssignmentStatus.DRAFT,
            ),
            testCases = listOf(
                AssignmentExampleResponse(seq = 1, inputValues = listOf("1 2"), outputText = "3", visibility = AssignmentTestCaseVisibility.EXCLUDED),
            ),
        )

        event.eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_CREATED
        event.problemId shouldBe "assignment-uuid"
        event.testCases shouldBe emptyList()
    }

    "delete 이벤트는 빈 testCases 배열을 담은 problem delete payload 를 만든다" {
        val event = mapper.deleted("assignment-uuid")

        event.eventType shouldBe AssignmentReportTestCaseEventType.PROBLEM_DELETED
        event.problemId shouldBe "assignment-uuid"
        event.testCases shouldBe emptyList()
    }
})

private fun assignment(
    id: String,
    status: AssignmentStatus,
): Assignment = Assignment(
    id = id,
    courseId = "course-1",
    courseSlug = "back-basic",
    createdBy = "admin-1",
    weekNo = 1,
    orderInWeek = 1,
    startAt = Instant.parse("2026-03-18T00:00:00Z"),
    endAt = Instant.parse("2026-03-25T00:00:00Z"),
    metadata = AssignmentMetadata(
        title = "title",
        difficulty = AssignmentDifficulty.MID,
        description = "description",
        timeLimitMinutes = 60,
    ),
    status = status,
)
