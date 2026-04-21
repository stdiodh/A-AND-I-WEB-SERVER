package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import reactor.test.StepVerifier

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
                    eventType = AssignmentReportTestCaseEventType.PROBLEM_CREATED,
                    problemId = "assignment-uuid",
                    testCases = emptyList(),
                )
            )
        )
            .verifyComplete()
    }
})
