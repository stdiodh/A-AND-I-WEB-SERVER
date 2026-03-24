package com.example.aandi_post_web_server.assignment.event

import com.example.aandi_post_web_server.assignment.dtos.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import org.springframework.stereotype.Component

@Component
class AssignmentReportTestCaseEventMapper {

    private val lineBreakPattern = Regex("\\r\\n|\\n|\\r")

    fun created(
        assignment: Assignment,
        testCases: List<AssignmentTestCaseResponse>,
    ): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.PROBLEM_CREATED,
            problemId = requireNotNull(assignment.id),
            testCases = testCases
                .filter { it.visibility != AssignmentTestCaseVisibility.EXCLUDED }
                .sortedBy { it.seq }
                .map(::toTestCase),
        )

    fun updated(
        assignment: Assignment,
        testCases: List<AssignmentTestCaseResponse>,
    ): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.PROBLEM_UPDATED,
            problemId = requireNotNull(assignment.id),
            testCases = testCases
                .filter { it.visibility != AssignmentTestCaseVisibility.EXCLUDED }
                .sortedBy { it.seq }
                .map(::toTestCase),
        )

    fun deleted(assignmentId: String): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.PROBLEM_DELETED,
            problemId = assignmentId,
            testCases = emptyList(),
        )

    private fun toTestCase(testCase: AssignmentTestCaseResponse): AssignmentReportTestCase =
        AssignmentReportTestCase(
            caseId = testCase.seq,
            input = toInputArgs(testCase.inputText),
            output = testCase.outputText,
        )

    private fun toInputArgs(inputText: String): List<String> {
        if (inputText.isEmpty()) {
            return emptyList()
        }
        return inputText.split(lineBreakPattern)
    }
}
