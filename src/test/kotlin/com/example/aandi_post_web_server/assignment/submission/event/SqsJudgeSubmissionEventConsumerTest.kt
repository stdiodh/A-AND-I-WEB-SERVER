package com.example.aandi_post_web_server.assignment.infrastructure.submission.event

import com.example.aandi_post_web_server.assignment.submission.entity.AssignmentSubmissionStatusProjection
import com.example.aandi_post_web_server.assignment.application.submission.service.AssignmentSubmissionStatusProjectionService
import com.example.aandi_post_web_server.assignment.application.submission.service.AssignmentSubmissionStatusProjectionStoreException
import com.example.aandi_post_web_server.assignment.application.submission.service.AssignmentSubmissionStatusProjectionStore
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

class SqsJudgeSubmissionEventConsumerTest : StringSpec({
    val queueUrl = "https://example.com/queues/judge-submission-events"

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
})

private class InMemoryAssignmentSubmissionStatusProjectionStore : AssignmentSubmissionStatusProjectionStore {
    private val projections = linkedMapOf<String, AssignmentSubmissionStatusProjection>()

    override fun findByAssignmentIdAndPublicCode(
        assignmentId: String,
        publicCode: String,
    ): Mono<AssignmentSubmissionStatusProjection> =
        Mono.justOrEmpty(projections["$assignmentId::$publicCode"])

    override fun save(projection: AssignmentSubmissionStatusProjection): Mono<AssignmentSubmissionStatusProjection> {
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
