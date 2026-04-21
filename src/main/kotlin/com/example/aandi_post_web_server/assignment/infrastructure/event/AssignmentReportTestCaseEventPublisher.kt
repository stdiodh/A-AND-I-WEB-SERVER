package com.example.aandi_post_web_server.assignment.infrastructure.event

import reactor.core.publisher.Mono

interface AssignmentReportTestCaseEventPublisher {
    fun publish(event: AssignmentReportTestCaseEvent): Mono<Void>
}
