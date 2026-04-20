package com.example.aandi_post_web_server.assignment.infrastructure.event

data class AssignmentReportTestCaseEvent(
    val eventType: AssignmentReportTestCaseEventType,
    val problemId: String,
    val testCases: List<AssignmentReportTestCase>,
)

data class AssignmentReportTestCase(
    val caseId: Int,
    val input: List<String>,
    val output: String,
)

enum class AssignmentReportTestCaseEventType {
    PROBLEM_CREATED,
    PROBLEM_UPDATED,
    PROBLEM_DELETED,
}
