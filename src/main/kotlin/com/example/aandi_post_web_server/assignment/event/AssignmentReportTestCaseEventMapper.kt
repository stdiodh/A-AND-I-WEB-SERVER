package com.example.aandi_post_web_server.assignment.event

import com.example.aandi_post_web_server.assignment.dtos.AssignmentExampleResponse
import org.springframework.stereotype.Component

@Component
class AssignmentReportTestCaseEventMapper {

    fun created(assignmentId: String, examples: List<AssignmentExampleResponse>): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.REPORT_TEST_CASE_CREATED,
            uuid = assignmentId,
            problemId = assignmentId,
            testCases = examples.map(::toTestCase),
        )

    fun updated(assignmentId: String, examples: List<AssignmentExampleResponse>): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.REPORT_TEST_CASE_UPDATED,
            uuid = assignmentId,
            problemId = assignmentId,
            testCases = examples.map(::toTestCase),
        )

    fun deleted(assignmentId: String): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.REPORT_TEST_CASE_DELETED,
            uuid = assignmentId,
            problemId = assignmentId,
            testCases = emptyList(),
        )

    private fun toTestCase(example: AssignmentExampleResponse): AssignmentReportTestCase =
        AssignmentReportTestCase(
            input = example.inputText,
            output = example.outputText,
        )
}
