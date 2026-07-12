package com.example.aandi_post_web_server.user.infrastructure.event

import com.example.aandi_post_web_server.user.application.model.AuthUserEvent
import com.example.aandi_post_web_server.user.application.model.AuthUserEventType
import com.example.aandi_post_web_server.user.application.service.ReportUserSyncOutcome
import com.example.aandi_post_web_server.user.application.service.ReportUserSyncService
import com.example.aandi_post_web_server.user.infrastructure.config.UserSyncEventProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SqsUserEventConsumerTest : StringSpec({
    "활성화된 consumer 의 queue URL 이 비어 있으면 실행 상태를 남기지 않는다" {
        val fixture = UserEventConsumerFixture(
            properties = UserSyncEventProperties(
                enabled = true,
                queueUrl = "",
            ),
        )

        val exception = shouldThrow<IllegalArgumentException> {
            fixture.consumer.start()
        }

        exception.message shouldBe "app.events.user-sync.queue-url must not be blank when enabled=true"
        fixture.consumer.isRunning shouldBe false
        Mockito.verifyNoInteractions(fixture.sqsAsyncClient)
    }

    "실행 중인 consumer 를 다시 시작해도 polling 을 중복 구독하지 않는다" {
        val fixture = UserEventConsumerFixture()
        val pendingReceive = CompletableFuture<ReceiveMessageResponse>()
        Mockito.`when`(
            fixture.sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java))
        ).thenReturn(pendingReceive)

        fixture.consumer.start()
        try {
            fixture.consumer.start()

            fixture.consumer.isRunning shouldBe true
            Mockito.verify(fixture.sqsAsyncClient, Mockito.times(1))
                .receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java))
        } finally {
            fixture.consumer.stop()
        }
    }

    "consumer 중지 시 진행 중인 polling 을 취소한 뒤 callback 을 호출한다" {
        val fixture = UserEventConsumerFixture()
        val pendingReceive = CompletableFuture<ReceiveMessageResponse>()
        var callbackCount = 0
        var runningAtCallback: Boolean? = null
        Mockito.`when`(
            fixture.sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java))
        ).thenReturn(pendingReceive)

        fixture.consumer.start()
        fixture.consumer.stop(
            Runnable {
                callbackCount += 1
                runningAtCallback = fixture.consumer.isRunning
            }
        )

        pendingReceive.isCancelled shouldBe true
        fixture.consumer.isRunning shouldBe false
        callbackCount shouldBe 1
        runningAtCallback shouldBe false
    }

    "시작 도중 중지해도 polling subscription 을 남기지 않는다" {
        val fixture = UserEventConsumerFixture()
        val receiveEntered = CountDownLatch(1)
        val allowReceiveReturn = CountDownLatch(1)
        val stopCompleted = CountDownLatch(1)
        val pendingReceive = CompletableFuture<ReceiveMessageResponse>()
        val startFailure = AtomicReference<Throwable?>()
        val stopFailure = AtomicReference<Throwable?>()
        Mockito.`when`(
            fixture.sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java))
        ).thenAnswer {
            receiveEntered.countDown()
            allowReceiveReturn.await()
            pendingReceive
        }
        val startThread = Thread(
            { runCatching { fixture.consumer.start() }.onFailure(startFailure::set) },
            "user-consumer-start",
        ).apply { isDaemon = true }
        val stopThread = Thread(
            {
                try {
                    fixture.consumer.stop()
                } catch (error: Throwable) {
                    stopFailure.set(error)
                } finally {
                    stopCompleted.countDown()
                }
            },
            "user-consumer-stop",
        ).apply { isDaemon = true }

        startThread.start()
        try {
            receiveEntered.await(5, TimeUnit.SECONDS) shouldBe true
            stopThread.start()
            awaitStopBlockedOrCompleted(stopThread, stopCompleted) shouldBe true
            allowReceiveReturn.countDown()
            startThread.join(5_000)
            stopThread.join(5_000)

            startThread.isAlive shouldBe false
            stopThread.isAlive shouldBe false
            startFailure.get() shouldBe null
            stopFailure.get() shouldBe null
            fixture.consumer.isRunning shouldBe false
            pendingReceive.isCancelled shouldBe true
        } finally {
            allowReceiveReturn.countDown()
            if (stopThread.state == Thread.State.NEW) {
                stopThread.start()
            }
            startThread.join(5_000)
            stopThread.join(5_000)
            if (startThread.isAlive) {
                startThread.interrupt()
            }
            if (stopThread.isAlive) {
                stopThread.interrupt()
            }
            startThread.join(2_000)
            stopThread.join(2_000)
            if (!startThread.isAlive && !stopThread.isAlive) {
                fixture.consumer.stop()
            }
            pendingReceive.cancel(true)
        }
    }

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

private fun awaitStopBlockedOrCompleted(
    stopThread: Thread,
    stopCompleted: CountDownLatch,
): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
        if (stopCompleted.await(10, TimeUnit.MILLISECONDS) || stopThread.state == Thread.State.BLOCKED) {
            return true
        }
    }
    return false
}

private class UserEventConsumerFixture(
    properties: UserSyncEventProperties = UserSyncEventProperties(
        enabled = true,
        queueUrl = "https://sqs.ap-northeast-2.amazonaws.com/362622729632/report-user-events-queue",
    ),
) {
    val sqsAsyncClient: SqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
    val reportUserSyncService: ReportUserSyncService = Mockito.mock(ReportUserSyncService::class.java)
    val consumer = SqsUserEventConsumer(
        properties = properties,
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
