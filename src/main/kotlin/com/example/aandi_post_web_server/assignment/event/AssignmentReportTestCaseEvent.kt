package com.example.aandi_post_web_server.assignment.event

import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import java.time.Instant

data class AssignmentReportTestCaseEvent(
    val eventId: String,
    val eventType: AssignmentReportTestCaseEventType,
    val occurredAt: Instant,
    val assignmentId: String,
    val assignmentStatus: AssignmentStatus?,
    val problemId: String,
    val testCases: List<AssignmentReportTestCase>,
)

data class AssignmentReportTestCase(
    val seq: Int,
    val input: String,
    val output: String,
)

enum class AssignmentReportTestCaseEventType {
    REPORT_TEST_CASE_CREATED,
    REPORT_TEST_CASE_UPDATED,
    REPORT_TEST_CASE_DELETED,
}
