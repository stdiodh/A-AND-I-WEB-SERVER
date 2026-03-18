package com.example.aandi_post_web_server.assignment.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import reactor.test.StepVerifier
import java.time.Instant

class AssignmentReportTestCaseEventConfigTest : StringSpec({
    "enabled=false 이면 no-op publisher 를 사용한다" {
        val config = AssignmentReportTestCaseEventConfig()
        val publisher = config.assignmentReportTestCaseEventPublisher(
            properties = AssignmentReportTestCaseEventProperties(enabled = false),
            objectMapper = ObjectMapper(),
        )

        publisher.shouldBeInstanceOf<NoopAssignmentReportTestCaseEventPublisher>()

        StepVerifier.create(
            publisher.publish(
                AssignmentReportTestCaseEvent(
                    eventId = "event-1",
                    eventType = AssignmentReportTestCaseEventType.REPORT_TEST_CASE_CREATED,
                    occurredAt = Instant.parse("2026-03-18T00:00:00Z"),
                    assignmentId = "assignment-uuid",
                    assignmentStatus = AssignmentStatus.DRAFT,
                    problemId = "assignment-uuid",
                    testCases = emptyList(),
                )
            )
        )
            .verifyComplete()
    }
})
