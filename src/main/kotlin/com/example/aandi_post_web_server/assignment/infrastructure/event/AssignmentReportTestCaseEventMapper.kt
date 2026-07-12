package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.example.aandi_post_web_server.assignment.domain.model.AssignmentTestCaseVisibility
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.entity.AssignmentTestCase
import org.springframework.stereotype.Component

@Component
class AssignmentReportTestCaseEventMapper {

    fun created(
        assignment: Assignment,
        testCases: List<AssignmentTestCase>,
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
        testCases: List<AssignmentTestCase>,
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

    private fun toTestCase(testCase: AssignmentTestCase): AssignmentReportTestCase =
        AssignmentReportTestCase(
            caseId = testCase.seq,
            input = testCase.inputValues,
            output = testCase.outputText,
        )
}
