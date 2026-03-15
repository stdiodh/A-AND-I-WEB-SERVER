package com.example.aandi_post_web_server.assignment.event

data class AssignmentReportTestCaseEvent(
    val eventType: AssignmentReportTestCaseEventType,
    val uuid: String,
    val problemId: String,
    val testCases: List<AssignmentReportTestCase>,
)

data class AssignmentReportTestCase(
    val input: String,
    val output: String,
)

enum class AssignmentReportTestCaseEventType {
    REPORT_TEST_CASE_CREATED,
    REPORT_TEST_CASE_UPDATED,
    REPORT_TEST_CASE_DELETED,
}
