package com.example.aandi_post_web_server.assignment.event

import reactor.core.publisher.Mono

class NoopAssignmentReportTestCaseEventPublisher : AssignmentReportTestCaseEventPublisher {
    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> = Mono.empty()
}
