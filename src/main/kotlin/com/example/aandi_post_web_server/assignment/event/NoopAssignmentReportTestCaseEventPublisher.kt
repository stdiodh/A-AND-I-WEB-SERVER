package com.example.aandi_post_web_server.assignment.event

import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

class NoopAssignmentReportTestCaseEventPublisher : AssignmentReportTestCaseEventPublisher {
    private val log = LoggerFactory.getLogger(NoopAssignmentReportTestCaseEventPublisher::class.java)

    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        log.info(
            "Skipping assignment problem sync publish because feature is disabled. eventType={}, problemId={}, testCaseCount={}",
            event.eventType,
            event.problemId,
            event.testCases.size,
        )
        return Mono.empty()
    }
}
