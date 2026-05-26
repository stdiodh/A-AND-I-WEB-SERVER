package com.example.aandi_post_web_server.assignment.infrastructure.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import reactor.test.StepVerifier
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sns.model.PublishResponse
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
            topicArn = "arn:aws:sns:ap-northeast-2:362622729632:report-testcase-events-topic.fifo",
        )

        val event = AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.PROBLEM_CREATED,
            problemId = "assignment-uuid",
            testCases = listOf(
                AssignmentReportTestCase(caseId = 1, input = listOf("1 2"), output = "3"),
            ),
        )

        StepVerifier.create(publisher.publish(event))
            .verifyComplete()

        requestCaptor.value.messageGroupId() shouldBe "assignment-uuid"
        requestCaptor.value.messageDeduplicationId().isNullOrBlank() shouldBe false
    }

    "SNS publish 실패는 오류로 전파된다" {
        val snsAsyncClient = Mockito.mock(SnsAsyncClient::class.java)
        Mockito.`when`(snsAsyncClient.publish(Mockito.any(PublishRequest::class.java)))
            .thenReturn(CompletableFuture.failedFuture(RuntimeException("sns down")))

        val publisher = SnsAssignmentReportTestCaseEventPublisher(
            snsAsyncClient = snsAsyncClient,
            objectMapper = ObjectMapper().registerModule(JavaTimeModule()),
            topicArn = "arn:aws:sns:ap-northeast-2:362622729632:report-testcase-events-topic",
        )

        val event = AssignmentReportTestCaseEvent(
            eventType = AssignmentReportTestCaseEventType.PROBLEM_UPDATED,
            problemId = "assignment-uuid",
            testCases = emptyList(),
        )

        StepVerifier.create(publisher.publish(event))
            .expectErrorSatisfies { error ->
                error.message shouldBe "sns down"
            }
            .verify()
    }
})
