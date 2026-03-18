package com.example.aandi_post_web_server.assignment.event

import com.example.aandi_post_web_server.assignment.dtos.AssignmentExampleResponse
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentMetadata
import com.example.aandi_post_web_server.assignment.enum.AssignmentDifficulty
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

class AssignmentReportTestCaseEventMapperTest : StringSpec({
    val mapper = AssignmentReportTestCaseEventMapper()

    "create 이벤트는 assignment status 와 examples 전체 snapshot 을 함께 만든다" {
        val event = mapper.created(
            assignment = assignment(
                id = "assignment-uuid",
                status = AssignmentStatus.DRAFT,
            ),
            testCases = listOf(
                AssignmentExampleResponse(seq = 2, inputText = "3 4", outputText = "7"),
                AssignmentExampleResponse(seq = 1, inputText = "1 2", outputText = "3"),
            ),
        )

        event.eventType shouldBe AssignmentReportTestCaseEventType.REPORT_TEST_CASE_CREATED
        event.assignmentId shouldBe "assignment-uuid"
        event.assignmentStatus shouldBe AssignmentStatus.DRAFT
        event.problemId shouldBe "assignment-uuid"
        event.eventId.isNotBlank() shouldBe true
        event.testCases shouldHaveSize 2
        event.testCases.first().seq shouldBe 1
        event.testCases.first().input shouldBe "1 2"
        event.testCases.first().output shouldBe "3"
    }

    "update 이벤트는 assignment status 와 examples 기준 최종 전체 배열 snapshot 을 만든다" {
        val event = mapper.updated(
            assignment = assignment(
                id = "assignment-uuid",
                status = AssignmentStatus.PUBLISHED,
            ),
            testCases = listOf(
                AssignmentExampleResponse(seq = 1, inputText = "A", outputText = "B"),
                AssignmentExampleResponse(seq = 2, inputText = "C", outputText = "D"),
            ),
        )

        event.eventType shouldBe AssignmentReportTestCaseEventType.REPORT_TEST_CASE_UPDATED
        event.assignmentId shouldBe "assignment-uuid"
        event.assignmentStatus shouldBe AssignmentStatus.PUBLISHED
        event.problemId shouldBe "assignment-uuid"
        event.testCases shouldHaveSize 2
    }

    "delete 이벤트는 빈 testCases 배열을 담은 update 이벤트를 만든다" {
        val event = mapper.deleted("assignment-uuid")

        event.eventType shouldBe AssignmentReportTestCaseEventType.REPORT_TEST_CASE_UPDATED
        event.assignmentId shouldBe "assignment-uuid"
        event.assignmentStatus shouldBe null
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
