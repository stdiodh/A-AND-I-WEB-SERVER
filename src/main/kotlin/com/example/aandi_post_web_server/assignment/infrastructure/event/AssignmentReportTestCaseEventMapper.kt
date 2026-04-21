package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.example.aandi_post_web_server.assignment.api.dto.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import org.springframework.stereotype.Component

@Component
class AssignmentReportTestCaseEventMapper {

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
            input = testCase.inputValues,
            output = testCase.outputText,
        )
}
