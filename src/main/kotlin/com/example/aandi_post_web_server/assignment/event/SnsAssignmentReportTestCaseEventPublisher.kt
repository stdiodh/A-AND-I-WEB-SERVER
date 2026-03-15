package com.example.aandi_post_web_server.assignment.event

import com.fasterxml.jackson.databind.ObjectMapper
import reactor.core.publisher.Mono
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.PublishRequest

class SnsAssignmentReportTestCaseEventPublisher(
    private val snsAsyncClient: SnsAsyncClient,
    private val objectMapper: ObjectMapper,
    private val topicArn: String,
) : AssignmentReportTestCaseEventPublisher {

    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        val message = objectMapper.writeValueAsString(event)
        val request = PublishRequest.builder()
            .topicArn(topicArn)
            .subject(event.eventType.name)
            .message(message)
            .build()
        return Mono.fromFuture(snsAsyncClient.publish(request)).then()
    }
}
