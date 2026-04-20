package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import java.util.UUID

class SnsAssignmentReportTestCaseEventPublisher(
    private val snsAsyncClient: SnsAsyncClient,
    private val objectMapper: ObjectMapper,
    private val topicArn: String,
) : AssignmentReportTestCaseEventPublisher {
    private val log = LoggerFactory.getLogger(SnsAssignmentReportTestCaseEventPublisher::class.java)

    override fun publish(event: AssignmentReportTestCaseEvent): Mono<Void> {
        val message = objectMapper.writeValueAsString(event)
        val requestBuilder = PublishRequest.builder()
            .topicArn(topicArn)
            .subject(event.eventType.name)
            .message(message)

        if (topicArn.endsWith(".fifo")) {
            requestBuilder
                .messageGroupId(event.problemId)
                .messageDeduplicationId(UUID.randomUUID().toString())
        }

        return Mono.fromFuture(snsAsyncClient.publish(requestBuilder.build()))
            .doOnSuccess { response ->
                log.info(
                    "Published assignment problem sync event to SNS. eventType={}, problemId={}, messageId={}",
                    event.eventType,
                    event.problemId,
                    response.messageId(),
                )
            }
            .doOnError { error ->
                log.error(
                    "Failed to publish assignment problem sync event to SNS. eventType={}, problemId={}",
                    event.eventType,
                    event.problemId,
                    error,
                )
            }
            .then()
    }
}
