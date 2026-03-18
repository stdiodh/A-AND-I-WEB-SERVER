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
        val requestBuilder = PublishRequest.builder()
            .topicArn(topicArn)
            .subject(event.eventType.name)
            .message(message)

        if (topicArn.endsWith(".fifo")) {
            requestBuilder
                .messageGroupId(event.assignmentId)
                .messageDeduplicationId(event.eventId)
        }

        return Mono.fromFuture(snsAsyncClient.publish(requestBuilder.build())).then()
    }
}
