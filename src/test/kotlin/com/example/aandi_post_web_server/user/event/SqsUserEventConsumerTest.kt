package com.example.aandi_post_web_server.user.infrastructure.event

import com.example.aandi_post_web_server.user.infrastructure.config.UserSyncEventProperties
import com.example.aandi_post_web_server.user.entity.ReportUser
import com.example.aandi_post_web_server.user.application.service.ReportUserSyncService
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mongodb.client.result.UpdateResult
import io.kotest.core.spec.style.StringSpec
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import java.time.Instant
import java.util.concurrent.CompletableFuture

class SqsUserEventConsumerTest : StringSpec({
    "메시지 처리 성공 시 deleteMessage 를 호출한다" {
        val sqsAsyncClient = Mockito.mock(SqsAsyncClient::class.java)
        val reactiveMongoTemplate = Mockito.mock(ReactiveMongoTemplate::class.java)
        val consumer = SqsUserEventConsumer(
            properties = UserSyncEventProperties(
                enabled = true,
                queueUrl = "https://sqs.ap-northeast-2.amazonaws.com/362622729632/report-user-events-queue",
            ),
            sqsAsyncClient = sqsAsyncClient,
            authUserEventParser = AuthUserEventParser(
                jacksonObjectMapper().registerModule(JavaTimeModule()),
            ),
            reportUserSyncService = ReportUserSyncService(reactiveMongoTemplate),
        )
        val message = Message.builder()
            .messageId("msg-1")
            .receiptHandle("receipt-handle-1")
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

        Mockito.`when`(
            reactiveMongoTemplate.updateFirst(
                ArgumentMatchers.any(Query::class.java),
                ArgumentMatchers.any(Update::class.java),
                ArgumentMatchers.eq(ReportUser::class.java),
            )
        ).thenReturn(Mono.just(UpdateResult.acknowledged(1L, 1L, null)))
        Mockito.`when`(sqsAsyncClient.deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java)))
            .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()))

        StepVerifier.create(consumer.processMessage(message))
            .verifyComplete()

        Mockito.verify(sqsAsyncClient).deleteMessage(ArgumentMatchers.any(DeleteMessageRequest::class.java))
    }
})
