package com.example.aandi_post_web_server.assignment.event

import com.example.aandi_post_web_server.assignment.dtos.AssignmentExampleResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class AssignmentReportTestCaseEventMapperTest : StringSpec({
    val mapper = AssignmentReportTestCaseEventMapper()

    "create 이벤트는 assignment id 와 전체 testCases 배열을 함께 만든다" {
        val event = mapper.created(
            assignmentId = "assignment-uuid",
            examples = listOf(
                AssignmentExampleResponse(
                    seq = 1,
                    inputText = "1 2",
                    outputText = "3",
                )
            ),
        )

        event.eventType shouldBe AssignmentReportTestCaseEventType.REPORT_TEST_CASE_CREATED
        event.uuid shouldBe "assignment-uuid"
        event.problemId shouldBe "assignment-uuid"
        event.testCases shouldHaveSize 1
        event.testCases.first().caseId shouldBe 1
        event.testCases.first().input shouldBe listOf("1 2")
        event.testCases.first().output shouldBe "3"
    }

    "update 이벤트는 최종 전체 배열 snapshot 을 만든다" {
        val event = mapper.updated(
            assignmentId = "assignment-uuid",
            examples = listOf(
                AssignmentExampleResponse(seq = 1, inputText = "A", outputText = "B"),
                AssignmentExampleResponse(seq = 2, inputText = "C", outputText = "D"),
            ),
        )

        event.eventType shouldBe AssignmentReportTestCaseEventType.REPORT_TEST_CASE_UPDATED
        event.uuid shouldBe "assignment-uuid"
        event.problemId shouldBe "assignment-uuid"
        event.testCases shouldHaveSize 2
        event.testCases.first().caseId shouldBe 1
        event.testCases.first().input shouldBe listOf("A")
    }

    "delete 이벤트는 빈 testCases 배열을 만든다" {
        val event = mapper.deleted("assignment-uuid")

        event.eventType shouldBe AssignmentReportTestCaseEventType.REPORT_TEST_CASE_DELETED
        event.uuid shouldBe "assignment-uuid"
        event.problemId shouldBe "assignment-uuid"
        event.testCases shouldBe emptyList()
    }

    "event JSON 은 testCases 와 input 을 배열로 직렬화한다" {
        val event = mapper.updated(
            assignmentId = "assignment-uuid",
            examples = listOf(
                AssignmentExampleResponse(
                    seq = 1,
                    inputText = "ADD 1\nCLOSE",
                    outputText = "+1",
                )
            ),
        )

        val payload = jacksonObjectMapper().readTree(jacksonObjectMapper().writeValueAsString(event))

        payload["problemId"].asText() shouldBe "assignment-uuid"
        payload["testCases"].isArray shouldBe true
        payload["testCases"][0]["input"].isArray shouldBe true
        payload["testCases"][0]["input"].map { it.asText() } shouldBe listOf("ADD 1\nCLOSE")
    }
})
