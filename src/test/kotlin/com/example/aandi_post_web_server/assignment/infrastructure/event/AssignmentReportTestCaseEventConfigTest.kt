package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
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

    "enabled=true 이고 topicArn 이 비어 있으면 설정 오류를 반환한다" {
        val config = AssignmentReportTestCaseEventConfig()

        val error = shouldThrow<IllegalArgumentException> {
            config.assignmentReportTestCaseEventPublisher(
                properties = AssignmentReportTestCaseEventProperties(enabled = true, topicArn = ""),
                objectMapper = ObjectMapper(),
            )
        }

        error.message shouldContain "APP_EVENTS_REPORT_TEST_CASE_SNS_TOPIC_ARN"
    }
})
