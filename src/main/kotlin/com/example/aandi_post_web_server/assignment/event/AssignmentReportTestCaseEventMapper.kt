package com.example.aandi_post_web_server.assignment.event

import com.example.aandi_post_web_server.assignment.dtos.AssignmentTestCaseResponse
import com.example.aandi_post_web_server.assignment.entity.Assignment
import com.example.aandi_post_web_server.assignment.enum.AssignmentTestCaseVisibility
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class AssignmentReportTestCaseEventMapper {

    fun created(
        assignment: Assignment,
        testCases: List<AssignmentTestCaseResponse>,
    ): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = AssignmentReportTestCaseEventType.REPORT_TEST_CASE_CREATED,
            occurredAt = Instant.now(),
            assignmentId = requireNotNull(assignment.id),
            assignmentStatus = assignment.status,
            problemId = requireNotNull(assignment.id),
            testCases = testCases
                .filter { it.visibility == AssignmentTestCaseVisibility.PUBLIC }
                .sortedBy { it.seq }
                .map(::toTestCase),
        )

    fun updated(
        assignment: Assignment,
        testCases: List<AssignmentTestCaseResponse>,
    ): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = AssignmentReportTestCaseEventType.REPORT_TEST_CASE_UPDATED,
            occurredAt = Instant.now(),
            assignmentId = requireNotNull(assignment.id),
            assignmentStatus = assignment.status,
            problemId = requireNotNull(assignment.id),
            testCases = testCases
                .filter { it.visibility == AssignmentTestCaseVisibility.PUBLIC }
                .sortedBy { it.seq }
                .map(::toTestCase),
        )

    fun deleted(assignmentId: String): AssignmentReportTestCaseEvent =
        AssignmentReportTestCaseEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = AssignmentReportTestCaseEventType.REPORT_TEST_CASE_UPDATED,
            occurredAt = Instant.now(),
            assignmentId = assignmentId,
            assignmentStatus = null,
            problemId = assignmentId,
            testCases = emptyList(),
        )

    private fun toTestCase(testCase: AssignmentTestCaseResponse): AssignmentReportTestCase =
        AssignmentReportTestCase(
            seq = testCase.seq,
            input = testCase.inputText,
            output = testCase.outputText,
        )
}
