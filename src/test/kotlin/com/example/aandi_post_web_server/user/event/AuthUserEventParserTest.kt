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
              "Message": "{\"eventType\":\"UserDeleted\",\"eventId\":\"evt-2\",\"id\":\"user-1\",\"occurredAt\":\"2026-03-20T01:05:00Z\"}"
            }
            """.trimIndent()
        )

        event.eventType shouldBe AuthUserEventType.UserDeleted
        event.id shouldBe "user-1"
        event.eventId shouldBe "evt-2"
        event.occurredAt shouldBe Instant.parse("2026-03-20T01:05:00Z")
    }
})
