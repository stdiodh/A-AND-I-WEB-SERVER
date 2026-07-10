package com.example.aandi_post_web_server.user.infrastructure.event

import com.example.aandi_post_web_server.user.application.service.ReportUserSyncOutcome
import com.example.aandi_post_web_server.user.application.service.ReportUserSyncService
import com.example.aandi_post_web_server.user.infrastructure.config.UserSyncEventProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.util.concurrent.CompletableFuture

class SqsUserEventConsumerTest : StringSpec({
    "메시지 처리 성공 시 deleteMessage 를 호출한다" {
        val fixture = UserEventConsumerFixture()
        val message = userProfileUpdatedMessage()
        fixture.stubSuccessfulSync()
        fixture.stubSuccessfulDelete()

        StepVerifier.create(fixture.consumer.processMessage(message))
            .verifyComplete()

        val eventCaptor = ArgumentCaptor.forClass(AuthUserEvent::class.java)
        Mockito.verify(fixture.reportUserSyncService)
            .sync(captureAuthUserEvent(eventCaptor))
        with(eventCaptor.value) {
            eventType shouldBe AuthUserEventType.UserProfileUpdated
            eventId shouldBe "evt-1"
            id shouldBe "user-1"
            publicCode shouldBe "#FL301"
            updatedAt shouldBe java.time.Instant.parse("2026-03-20T01:00:00Z")
        }
        Mockito.verify(fixture.sqsAsyncClient)
            .deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "사용자 동기화 실패 시 deleteMessage 를 호출하지 않는다" {
        val fixture = UserEventConsumerFixture()
        val message = userProfileUpdatedMessage()
        val failure = IllegalStateException("mongo unavailable")
        Mockito.`when`(
            fixture.reportUserSyncService.sync(anyAuthUserEvent())
        ).thenReturn(Mono.error(failure))

        StepVerifier.create(fixture.consumer.processMessage(message))
            .expectErrorMatches { it === failure }
            .verify()

        Mockito.verify(fixture.sqsAsyncClient, Mockito.never())
            .deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "잘못된 JSON 메시지는 동기화하거나 deleteMessage 를 호출하지 않는다" {
        val fixture = UserEventConsumerFixture()
        val message = Message.builder()
            .messageId("msg-malformed")
            .receiptHandle("receipt-handle-malformed")
            .body("{")
            .build()

        StepVerifier.create(fixture.consumer.processMessage(message))
            .expectErrorMatches { it is JsonProcessingException }
            .verify()

        Mockito.verifyNoInteractions(fixture.reportUserSyncService)
        Mockito.verify(fixture.sqsAsyncClient, Mockito.never())
            .deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "deleteMessage 실패를 전파한다" {
        val fixture = UserEventConsumerFixture()
        val message = userProfileUpdatedMessage()
        val failure = IllegalStateException("sqs delete unavailable")
        fixture.stubSuccessfulSync()
        Mockito.`when`(
            fixture.sqsAsyncClient.deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
        ).thenReturn(CompletableFuture.failedFuture<DeleteMessageResponse>(failure))

        StepVerifier.create(fixture.consumer.processMessage(message))
            .expectErrorMatches { it is IllegalStateException && it.message == failure.message }
            .verify()

        Mockito.verify(fixture.sqsAsyncClient)
            .deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "pollBatch 는 첫 메시지 실패 후 다음 메시지를 처리하고 두 번째 메시지만 삭제한다" {
        val fixture = UserEventConsumerFixture()
        val malformedMessage = Message.builder()
            .messageId("msg-1")
            .receiptHandle("receipt-handle-1")
            .body("{")
            .build()
        val successfulMessage = userProfileUpdatedMessage(
            messageId = "msg-2",
            receiptHandle = "receipt-handle-2",
        )
        Mockito.`when`(
            fixture.sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java))
        ).thenReturn(
            CompletableFuture.completedFuture(
                ReceiveMessageResponse.builder()
                    .messages(malformedMessage, successfulMessage)
                    .build()
            )
        )
        fixture.stubSuccessfulSync()
        fixture.stubSuccessfulDelete()

        StepVerifier.create(fixture.consumer.pollBatch())
            .verifyComplete()

        Mockito.verify(fixture.reportUserSyncService, Mockito.times(1))
            .sync(anyAuthUserEvent())
        val deleteCaptor = ArgumentCaptor.forClass(DeleteMessageRequest::class.java)
        Mockito.verify(fixture.sqsAsyncClient, Mockito.times(1))
            .deleteMessage(deleteCaptor.capture())
        deleteCaptor.value.receiptHandle() shouldBe "receipt-handle-2"
    }

    "receiveMessage 실패를 pollBatch 밖으로 전파한다" {
        val fixture = UserEventConsumerFixture()
        val failure = IllegalStateException("sqs receive unavailable")
        Mockito.`when`(
            fixture.sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java))
        ).thenReturn(CompletableFuture.failedFuture<ReceiveMessageResponse>(failure))

        StepVerifier.create(fixture.consumer.pollBatch())
            .expectErrorMatches { it is IllegalStateException && it.message == failure.message }
            .verify()

        Mockito.verifyNoInteractions(fixture.reportUserSyncService)
        Mockito.verify(fixture.sqsAsyncClient, Mockito.never())
            .deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }
})

private class UserEventConsumerFixture {
    val sqsAsyncClient: SqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
    val reportUserSyncService: ReportUserSyncService = Mockito.mock(ReportUserSyncService::class.java)
    val consumer = SqsUserEventConsumer(
        properties = UserSyncEventProperties(
            enabled = true,
            queueUrl = "https://sqs.ap-northeast-2.amazonaws.com/362622729632/report-user-events-queue",
        ),
        sqsAsyncClient = sqsAsyncClient,
        authUserEventParser = AuthUserEventParser(
            jacksonObjectMapper().registerModule(JavaTimeModule()),
        ),
        reportUserSyncService = reportUserSyncService,
    )

    fun stubSuccessfulSync() {
        Mockito.`when`(
            reportUserSyncService.sync(anyAuthUserEvent())
        ).thenReturn(Mono.just(ReportUserSyncOutcome.UPSERTED))
    }

    fun stubSuccessfulDelete() {
        Mockito.`when`(
            sqsAsyncClient.deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
        ).thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))
    }
}

private fun userProfileUpdatedMessage(
    messageId: String = "msg-1",
    receiptHandle: String = "receipt-handle-1",
): Message = Message.builder()
    .messageId(messageId)
    .receiptHandle(receiptHandle)
    .body(
        """
        {
          "eventType": "UserProfileUpdated",
          "eventId": "evt-1",
          "occurredAt": "2026-03-20T01:00:00Z",
          "id": "user-1",
          "publicCode": "#FL301",
          "username": "mekazon",
          "role": "USER",
          "updatedAt": "2026-03-20T01:00:00Z"
        }
        """.trimIndent()
    )
    .build()

private fun anyAuthUserEvent(): AuthUserEvent =
    ArgumentMatchers.any(AuthUserEvent::class.java)
        ?: placeholderAuthUserEvent()

private fun captureAuthUserEvent(captor: ArgumentCaptor<AuthUserEvent>): AuthUserEvent =
    captor.capture() ?: placeholderAuthUserEvent()

private fun placeholderAuthUserEvent(): AuthUserEvent =
    AuthUserEvent(
        eventType = AuthUserEventType.UserDeleted,
        id = "matcher-placeholder",
    )
