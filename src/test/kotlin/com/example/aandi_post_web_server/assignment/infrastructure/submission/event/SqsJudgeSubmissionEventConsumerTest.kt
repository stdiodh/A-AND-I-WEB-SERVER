package com.example.aandi_post_web_server.assignment.infrastructure.submission.event

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.application.submission.service.AssignmentSubmissionStatusProjectionService
import com.example.aandi_post_web_server.assignment.application.submission.service.AssignmentSubmissionStatusProjectionStoreException
import com.example.aandi_post_web_server.assignment.application.submission.service.AssignmentSubmissionStatusProjectionStore
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SqsJudgeSubmissionEventConsumerTest : StringSpec({
    val queueUrl = JUDGE_QUEUE_URL

    "활성화된 consumer 의 queue URL 이 비어 있으면 실행 상태를 남기지 않는다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val consumer = judgeSubmissionEventConsumer(
            sqsAsyncClient = sqsAsyncClient,
            store = InMemoryAssignmentSubmissionStatusProjectionStore(),
            properties = JudgeSubmissionEventProperties(
                enabled = true,
                queueUrl = "",
                region = "ap-northeast-2",
            ),
        )

        shouldThrow<IllegalArgumentException> {
            consumer.start()
        }

        consumer.isRunning shouldBe false
        Mockito.verifyNoInteractions(sqsAsyncClient)
    }

    "활성화된 consumer 의 region 이 비어 있으면 실행 상태를 남기지 않는다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val consumer = judgeSubmissionEventConsumer(
            sqsAsyncClient = sqsAsyncClient,
            store = InMemoryAssignmentSubmissionStatusProjectionStore(),
            properties = JudgeSubmissionEventProperties(
                enabled = true,
                queueUrl = queueUrl,
                region = "",
            ),
        )

        shouldThrow<IllegalArgumentException> {
            consumer.start()
        }

        consumer.isRunning shouldBe false
        Mockito.verifyNoInteractions(sqsAsyncClient)
    }

    "중복 시작을 막고 중지 시 진행 중인 polling 을 취소한 뒤 callback 을 호출한다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val consumer = judgeSubmissionEventConsumer(
            sqsAsyncClient,
            InMemoryAssignmentSubmissionStatusProjectionStore(),
        )
        val pendingReceive = CompletableFuture<ReceiveMessageResponse>()
        var callbackCount = 0
        var runningAtCallback: Boolean? = null
        Mockito.`when`(sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java)))
            .thenReturn(pendingReceive)

        consumer.start()
        consumer.start()
        consumer.stop(
            Runnable {
                callbackCount += 1
                runningAtCallback = consumer.isRunning
            }
        )

        Mockito.verify(sqsAsyncClient, Mockito.times(1))
            .receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java))
        pendingReceive.isCancelled shouldBe true
        consumer.isRunning shouldBe false
        callbackCount shouldBe 1
        runningAtCallback shouldBe false
    }

    "시작 도중 중지해도 polling subscription 을 남기지 않는다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val consumer = judgeSubmissionEventConsumer(
            sqsAsyncClient,
            InMemoryAssignmentSubmissionStatusProjectionStore(),
        )
        val receiveEntered = CountDownLatch(1)
        val allowReceiveReturn = CountDownLatch(1)
        val stopCompleted = CountDownLatch(1)
        val pendingReceive = CompletableFuture<ReceiveMessageResponse>()
        val startFailure = AtomicReference<Throwable?>()
        val stopFailure = AtomicReference<Throwable?>()
        Mockito.`when`(sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java)))
            .thenAnswer {
                receiveEntered.countDown()
                allowReceiveReturn.await()
                pendingReceive
            }
        val startThread = Thread(
            { runCatching { consumer.start() }.onFailure(startFailure::set) },
            "judge-consumer-start",
        ).apply { isDaemon = true }
        val stopThread = Thread(
            {
                try {
                    consumer.stop()
                } catch (error: Throwable) {
                    stopFailure.set(error)
                } finally {
                    stopCompleted.countDown()
                }
            },
            "judge-consumer-stop",
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
            consumer.isRunning shouldBe false
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
                consumer.stop()
            }
            pendingReceive.cancel(true)
        }
    }

    "메시지 처리 성공 시 projection 저장 후 deleteMessage 를 호출한다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val consumer = SqsJudgeSubmissionEventConsumer(
            properties = JudgeSubmissionEventProperties(
                enabled = true,
                queueUrl = queueUrl,
                region = "ap-northeast-2",
            ),
            sqsAsyncClient = sqsAsyncClient,
            judgeCompletedEventParser = JudgeCompletedEventParser(
                jacksonObjectMapper().registerModule(JavaTimeModule()),
            ),
            projectionService = AssignmentSubmissionStatusProjectionService(store),
        )
        val message = Message.builder()
            .messageId("msg-1")
            .receiptHandle("receipt-handle-1")
            .body(
                """
                {
                  "eventType": "JUDGE_COMPLETED",
                  "publicCode": "A00123",
                  "problemId": "quiz-101",
                  "score": 80,
                  "passedCases": 8,
                  "totalCases": 10,
                  "timestamp": "2026-04-09T02:15:30.123Z"
                }
                """.trimIndent()
            )
            .build()

        Mockito.`when`(sqsAsyncClient.deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java)))
            .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))

        StepVerifier.create(consumer.processMessage(message))
            .verifyComplete()

        store.findAll() shouldHaveSize 1
        Mockito.verify(sqsAsyncClient).deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "지원하지 않는 eventType 은 스킵하고 deleteMessage 를 호출한다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val consumer = SqsJudgeSubmissionEventConsumer(
            properties = JudgeSubmissionEventProperties(
                enabled = true,
                queueUrl = queueUrl,
                region = "ap-northeast-2",
            ),
            sqsAsyncClient = sqsAsyncClient,
            judgeCompletedEventParser = JudgeCompletedEventParser(
                jacksonObjectMapper().registerModule(JavaTimeModule()),
            ),
            projectionService = AssignmentSubmissionStatusProjectionService(store),
        )
        val message = Message.builder()
            .messageId("msg-2")
            .receiptHandle("receipt-handle-2")
            .body(
                """
                {
                  "eventType": "JUDGE_RUNNING",
                  "publicCode": "A00123",
                  "problemId": "quiz-101",
                  "score": 80,
                  "passedCases": 8,
                  "totalCases": 10,
                  "timestamp": "2026-04-09T02:15:30.123Z"
                }
                """.trimIndent()
            )
            .build()

        Mockito.`when`(sqsAsyncClient.deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java)))
            .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))

        StepVerifier.create(consumer.processMessage(message))
            .verifyComplete()

        store.findAll() shouldHaveSize 0
        Mockito.verify(sqsAsyncClient).deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "sns fan-out envelope 메시지도 projection 저장 후 deleteMessage 를 호출한다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val consumer = SqsJudgeSubmissionEventConsumer(
            properties = JudgeSubmissionEventProperties(
                enabled = true,
                queueUrl = queueUrl,
                region = "ap-northeast-2",
            ),
            sqsAsyncClient = sqsAsyncClient,
            judgeCompletedEventParser = JudgeCompletedEventParser(
                jacksonObjectMapper().registerModule(JavaTimeModule()),
            ),
            projectionService = AssignmentSubmissionStatusProjectionService(store),
        )
        val message = Message.builder()
            .messageId("msg-3")
            .receiptHandle("receipt-handle-3")
            .body(
                """
                {
                  "Type": "Notification",
                  "MessageId": "52f8a9dd-19e1-4c13-a5b5-77d59c35d001",
                  "TopicArn": "arn:aws:sns:ap-northeast-2:000000000000:judge-submission-events.fifo",
                  "Message": "{\"eventType\":\"JUDGE_COMPLETED\",\"publicCode\":\"A00123\",\"problemId\":\"quiz-101\",\"score\":100,\"passedCases\":10,\"totalCases\":10,\"timestamp\":\"2026-04-09T02:15:30.123Z\"}"
                }
                """.trimIndent()
            )
            .build()

        Mockito.`when`(sqsAsyncClient.deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java)))
            .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))

        StepVerifier.create(consumer.processMessage(message))
            .verifyComplete()

        store.findAll() shouldHaveSize 1
        Mockito.verify(sqsAsyncClient).deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "pollBatch 는 queue URL 과 long polling 설정으로 메시지를 batch 수신한다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val properties = JudgeSubmissionEventProperties(
            enabled = true,
            queueUrl = queueUrl,
            region = "ap-northeast-2",
            waitTimeSeconds = 20,
            maxNumberOfMessages = 10,
            visibilityTimeoutSeconds = 60,
        )
        val consumer = SqsJudgeSubmissionEventConsumer(
            properties = properties,
            sqsAsyncClient = sqsAsyncClient,
            judgeCompletedEventParser = JudgeCompletedEventParser(
                jacksonObjectMapper().registerModule(JavaTimeModule()),
            ),
            projectionService = AssignmentSubmissionStatusProjectionService(store),
        )
        val message = Message.builder()
            .messageId("msg-4")
            .receiptHandle("receipt-handle-4")
            .body(
                """
                {
                  "eventType": "JUDGE_COMPLETED",
                  "publicCode": "A00124",
                  "problemId": "quiz-102",
                  "score": 90,
                  "passedCases": 9,
                  "totalCases": 10,
                  "timestamp": "2026-04-09T02:15:30.123Z"
                }
                """.trimIndent()
            )
            .build()
        val receiveCaptor = ArgumentCaptor.forClass(ReceiveMessageRequest::class.java)

        Mockito.`when`(sqsAsyncClient.receiveMessage(receiveCaptor.capture()))
            .thenReturn(
                CompletableFuture.completedFuture(
                    ReceiveMessageResponse.builder()
                        .messages(message)
                        .build()
                )
            )
        Mockito.`when`(sqsAsyncClient.deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java)))
            .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))

        StepVerifier.create(consumer.pollBatch())
            .verifyComplete()

        with(receiveCaptor.value) {
            queueUrl() shouldBe properties.queueUrl
            waitTimeSeconds() shouldBeExactly properties.waitTimeSeconds
            maxNumberOfMessages() shouldBeExactly properties.maxNumberOfMessages
            visibilityTimeout() shouldBeExactly properties.visibilityTimeoutSeconds
        }
        store.findAll() shouldHaveSize 1
        Mockito.verify(sqsAsyncClient).deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "projection 저장에 실패하면 deleteMessage 를 호출하지 않는다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val failingStore = FailingAssignmentSubmissionStatusProjectionStore()
        val consumer = SqsJudgeSubmissionEventConsumer(
            properties = JudgeSubmissionEventProperties(
                enabled = true,
                queueUrl = queueUrl,
                region = "ap-northeast-2",
            ),
            sqsAsyncClient = sqsAsyncClient,
            judgeCompletedEventParser = JudgeCompletedEventParser(
                jacksonObjectMapper().registerModule(JavaTimeModule()),
            ),
            projectionService = AssignmentSubmissionStatusProjectionService(failingStore),
        )
        val message = Message.builder()
            .messageId("msg-5")
            .receiptHandle("receipt-handle-5")
            .body(
                """
                {
                  "eventType": "JUDGE_COMPLETED",
                  "publicCode": "A00999",
                  "problemId": "quiz-999",
                  "score": 40,
                  "passedCases": 4,
                  "totalCases": 10,
                  "timestamp": "2026-04-09T02:15:30.123Z"
                }
                """.trimIndent()
            )
            .build()

        StepVerifier.create(consumer.processMessage(message))
            .expectError(AssignmentSubmissionStatusProjectionStoreException::class.java)
            .verify()

        Mockito.verify(sqsAsyncClient, Mockito.never())
            .deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "deleteMessage 실패는 오류를 전파하고 저장된 projection 을 유지한다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val consumer = judgeSubmissionEventConsumer(sqsAsyncClient, store)
        val message = judgeCompletedMessage(
            messageId = "msg-delete-failure",
            receiptHandle = "receipt-delete-failure",
            assignmentId = "quiz-delete-failure",
            publicCode = "A01001",
        )
        val deleteFailure = IllegalStateException("delete unavailable")

        Mockito.`when`(sqsAsyncClient.deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java)))
            .thenReturn(CompletableFuture.failedFuture<DeleteMessageResponse>(deleteFailure))

        StepVerifier.create(consumer.processMessage(message))
            .expectErrorMatches { ex ->
                ex is IllegalStateException && ex.message == deleteFailure.message
            }
            .verify()

        store.findAll() shouldHaveSize 1
        store.findAll().single().assignmentId shouldBe "quiz-delete-failure"
        Mockito.verify(sqsAsyncClient).deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "pollBatch 는 실패한 메시지를 삭제하지 않고 다음 메시지를 계속 처리한다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val store = InMemoryAssignmentSubmissionStatusProjectionStore(
            failingAssignmentId = "quiz-failure",
        )
        val consumer = judgeSubmissionEventConsumer(sqsAsyncClient, store)
        val failedMessage = judgeCompletedMessage(
            messageId = "msg-batch-failure",
            receiptHandle = "receipt-batch-failure",
            assignmentId = "quiz-failure",
            publicCode = "A01002",
        )
        val successfulMessage = judgeCompletedMessage(
            messageId = "msg-batch-success",
            receiptHandle = "receipt-batch-success",
            assignmentId = "quiz-success",
            publicCode = "A01003",
        )
        val deleteCaptor = ArgumentCaptor.forClass(DeleteMessageRequest::class.java)

        Mockito.`when`(sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java)))
            .thenReturn(
                CompletableFuture.completedFuture(
                    ReceiveMessageResponse.builder()
                        .messages(failedMessage, successfulMessage)
                        .build()
                )
            )
        Mockito.`when`(sqsAsyncClient.deleteMessage(deleteCaptor.capture()))
            .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))

        StepVerifier.create(consumer.pollBatch())
            .verifyComplete()

        store.findAll() shouldHaveSize 1
        store.findAll().single().assignmentId shouldBe "quiz-success"
        with(deleteCaptor.value) {
            queueUrl() shouldBe queueUrl
            receiptHandle() shouldBe "receipt-batch-success"
        }
        Mockito.verify(sqsAsyncClient, Mockito.times(1))
            .deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }

    "receiveMessage 실패는 pollBatch 밖으로 전파하고 deleteMessage 를 호출하지 않는다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val store = InMemoryAssignmentSubmissionStatusProjectionStore()
        val consumer = judgeSubmissionEventConsumer(sqsAsyncClient, store)
        val receiveFailure = IllegalStateException("receive unavailable")

        Mockito.`when`(sqsAsyncClient.receiveMessage(ArgumentMatchers.any(ReceiveMessageRequest::class.java)))
            .thenReturn(CompletableFuture.failedFuture<ReceiveMessageResponse>(receiveFailure))

        StepVerifier.create(consumer.pollBatch())
            .expectErrorMatches { ex ->
                ex is IllegalStateException && ex.message == receiveFailure.message
            }
            .verify()

        store.findAll() shouldHaveSize 0
        Mockito.verify(sqsAsyncClient, Mockito.never())
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

private const val JUDGE_QUEUE_URL = "https://example.com/queues/judge-submission-events"

private fun judgeSubmissionEventConsumer(
    sqsAsyncClient: SqsAsyncClient,
    store: AssignmentSubmissionStatusProjectionStore,
    properties: JudgeSubmissionEventProperties = JudgeSubmissionEventProperties(
        enabled = true,
        queueUrl = JUDGE_QUEUE_URL,
        region = "ap-northeast-2",
    ),
): SqsJudgeSubmissionEventConsumer =
    SqsJudgeSubmissionEventConsumer(
        properties = properties,
        sqsAsyncClient = sqsAsyncClient,
        judgeCompletedEventParser = JudgeCompletedEventParser(
            jacksonObjectMapper().registerModule(JavaTimeModule()),
        ),
        projectionService = AssignmentSubmissionStatusProjectionService(store),
    )

private fun judgeCompletedMessage(
    messageId: String,
    receiptHandle: String,
    assignmentId: String,
    publicCode: String,
): Message =
    Message.builder()
        .messageId(messageId)
        .receiptHandle(receiptHandle)
        .body(
            """
            {
              "eventType": "JUDGE_COMPLETED",
              "publicCode": "$publicCode",
              "problemId": "$assignmentId",
              "score": 80,
              "passedCases": 8,
              "totalCases": 10,
              "timestamp": "2026-04-09T02:15:30.123Z"
            }
            """.trimIndent()
        )
        .build()

private class InMemoryAssignmentSubmissionStatusProjectionStore(
    private val failingAssignmentId: String? = null,
) : AssignmentSubmissionStatusProjectionStore {
    private val projections = linkedMapOf<String, AssignmentSubmissionStatusProjection>()

    override fun findByAssignmentIdAndPublicCode(
        assignmentId: String,
        publicCode: String,
    ): Mono<AssignmentSubmissionStatusProjection> =
        Mono.justOrEmpty(projections["$assignmentId::$publicCode"])

    override fun save(projection: AssignmentSubmissionStatusProjection): Mono<AssignmentSubmissionStatusProjection> {
        if (projection.assignmentId == failingAssignmentId) {
            return Mono.error(IllegalStateException("projection store unavailable"))
        }

        val key = "${projection.assignmentId}::${projection.publicCode}"
        val currentVersion = projections[key]?.version ?: -1L
        val persisted = projection.copy(
            id = projection.id ?: key,
            version = currentVersion + 1,
        )
        projections[key] = persisted
        return Mono.just(persisted)
    }

    fun findAll(): List<AssignmentSubmissionStatusProjection> = projections.values.toList()
}

private class FailingAssignmentSubmissionStatusProjectionStore : AssignmentSubmissionStatusProjectionStore {
    override fun findByAssignmentIdAndPublicCode(
        assignmentId: String,
        publicCode: String,
    ): Mono<AssignmentSubmissionStatusProjection> = Mono.empty()

    override fun save(projection: AssignmentSubmissionStatusProjection): Mono<AssignmentSubmissionStatusProjection> =
        Mono.error(IllegalStateException("projection store unavailable"))
}
