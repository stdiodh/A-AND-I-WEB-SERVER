package com.example.aandi_post_web_server.assignment.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.example.aandi_post_web_server.assignment.enum.AssignmentStatus
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import reactor.test.StepVerifier
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.PublishResponse
import java.time.Instant
import java.util.concurrent.CompletableFuture

class SnsAssignmentReportTestCaseEventPublisherTest : StringSpec({
    "fifo topic 이면 message group id 와 deduplication id 를 함께 보낸다" {
        val snsAsyncClient = Mockito.mock(SnsAsyncClient::class.java)
        val requestCaptor = ArgumentCaptor.forClass(PublishRequest::class.java)
        Mockito.`when`(snsAsyncClient.publish(requestCaptor.capture()))
            .thenReturn(CompletableFuture.completedFuture(PublishResponse.builder().messageId("message-1").build()))

        val publisher = SnsAssignmentReportTestCaseEventPublisher(
            snsAsyncClient = snsAsyncClient,
            objectMapper = ObjectMapper().registerModule(JavaTimeModule()),
            topicArn = "arn:aws:sns:ap-northeast-2:362622729632:report-events-topic.fifo",
        )

        val event = AssignmentReportTestCaseEvent(
            eventId = "event-1",
            eventType = AssignmentReportTestCaseEventType.REPORT_TEST_CASE_CREATED,
            occurredAt = Instant.parse("2026-03-18T00:00:00Z"),
            assignmentId = "assignment-uuid",
            assignmentStatus = AssignmentStatus.DRAFT,
            problemId = "assignment-uuid",
            testCases = listOf(
                AssignmentReportTestCase(seq = 1, input = "1 2", output = "3"),
            ),
        )

        StepVerifier.create(publisher.publish(event))
            .verifyComplete()

        requestCaptor.value.messageGroupId() shouldBe "assignment-uuid"
        requestCaptor.value.messageDeduplicationId() shouldBe "event-1"
    }
})
