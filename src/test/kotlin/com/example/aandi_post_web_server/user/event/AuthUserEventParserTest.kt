package com.example.aandi_post_web_server.user.event

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class AuthUserEventParserTest : StringSpec({
    val parser = AuthUserEventParser(
        jacksonObjectMapper().registerModule(JavaTimeModule()),
    )

    "raw user event json 을 파싱한다" {
        val event = parser.parse(
            """
            {
              "eventType": "UserProfileUpdated",
              "eventId": "evt-1",
              "occurredAt": "2026-03-20T01:00:00Z",
              "id": "user-1",
              "publicCode": "#FL301",
              "username": "mekazon",
              "role": "USER",
              "nickname": "메카존",
              "profileImageUrl": "https://example.com/profile.png",
              "updatedAt": "2026-03-20T01:00:00Z"
            }
            """.trimIndent()
        )

        event.eventType shouldBe AuthUserEventType.UserProfileUpdated
        event.id shouldBe "user-1"
        event.publicCode shouldBe "#FL301"
        event.updatedAt shouldBe Instant.parse("2026-03-20T01:00:00Z")
    }

    "sns envelope 안의 Message 값을 파싱한다" {
        val event = parser.parse(
            """
            {
              "Type": "Notification",
              "Message": "{\"type\":\"UserDeleted\",\"eventId\":\"evt-2\",\"userId\":\"user-1\",\"occurredAt\":\"2026-03-20T01:05:00Z\"}"
            }
            """.trimIndent()
        )

        event.eventType shouldBe AuthUserEventType.UserDeleted
        event.id shouldBe "user-1"
        event.eventId shouldBe "evt-2"
        event.occurredAt shouldBe Instant.parse("2026-03-20T01:05:00Z")
    }

    "auth 실제 user profile updated sns message 를 파싱한다" {
        val event = parser.parse(
            """
            {
              "Type": "Notification",
              "MessageId": "380bb86c-6c47-5df0-9e75-da5a5d0586b9",
              "TopicArn": "arn:aws:sns:ap-northeast-2:362622729632:user-events-topic",
              "Message": "{\"eventId\":\"5788d6e1-67e9-4410-8953-40d8c1cf4dff\",\"type\":\"UserProfileUpdated\",\"occurredAt\":\"2026-03-19T17:14:42.922398600Z\",\"userId\":\"6db4dae6-2cf1-4651-a57c-2b0d3cf966fc\",\"username\":\"user_55\",\"role\":\"USER\",\"userTrack\":\"NO\",\"cohort\":4,\"cohortOrder\":12,\"publicCode\":\"#NO412\",\"nickname\":null,\"profileImageUrl\":null,\"version\":0}"
            }
            """.trimIndent()
        )

        event.eventType shouldBe AuthUserEventType.UserProfileUpdated
        event.id shouldBe "6db4dae6-2cf1-4651-a57c-2b0d3cf966fc"
        event.publicCode shouldBe "#NO412"
        event.userTrack shouldBe "NO"
        event.cohort shouldBe 4
        event.cohortOrder shouldBe 12
        event.version shouldBe 0
        event.occurredAt shouldBe Instant.parse("2026-03-19T17:14:42.922398600Z")
    }
})
